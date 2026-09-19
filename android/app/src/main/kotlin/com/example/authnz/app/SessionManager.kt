package com.example.authnz.app

import androidx.fragment.app.FragmentActivity
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.AuthenticationLevel
import com.auth0.android.authentication.storage.BiometricPolicy
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.LocalAuthenticationOptions
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.example.authnz.bridge.BridgeErrorCode
import com.example.authnz.bridge.CachedAccessToken
import com.example.authnz.bridge.MemoryTokenCache

/** What a token request produced. Mirrors the replies the bridge contract defines. */
sealed interface TokenOutcome {

    /** An access token that is usable until [CachedAccessToken.expiresAtEpochMillis]. */
    data class Granted(val token: CachedAccessToken) : TokenOutcome

    /** No token. [code] is what the page is told; [detail] is for the log only. */
    data class Denied(val code: BridgeErrorCode, val detail: String?) : TokenOutcome
}

/**
 * Owns the sign-in session: the browser round trip, the encrypted credential store and the
 * in-memory access token.
 *
 * The refresh token never leaves this class. Callers - including the WebView bridge - only ever
 * receive an access token and the instant it expires.
 */
class SessionManager(private val activity: FragmentActivity) {

    private val account = Auth0.getInstance(AppSettings.auth0ClientId, AppSettings.auth0Domain)
    private val authenticationApi = AuthenticationAPIClient(account)
    private val tokenCache = MemoryTokenCache()

    private val credentialsManager = SecureCredentialsManager(
        authenticationApi,
        activity,
        SharedPreferencesStorage(activity),
        activity,
        localAuthenticationOptions(),
    )

    /**
     * The local authentication prompt.
     *
     * The level is `WEAK` rather than `STRONG` and the device credential fallback is on, so that a
     * device without an enrolled biometric can still unlock the credentials with its screen lock.
     * The policy is stated rather than left at its default of prompting on every access: one
     * confirmation covers the next [BIOMETRIC_SESSION_SECONDS] seconds, which is what keeps a page
     * that asks for a token repeatedly from raising a prompt every time.
     */
    private fun localAuthenticationOptions(): LocalAuthenticationOptions =
        LocalAuthenticationOptions.Builder()
            .setTitle(activity.getString(R.string.auth_prompt_title))
            .setDescription(activity.getString(R.string.auth_prompt_description))
            .setAuthenticationLevel(AuthenticationLevel.WEAK)
            .setDeviceCredentialFallback(true)
            .setPolicy(BiometricPolicy.Session(BIOMETRIC_SESSION_SECONDS))
            .build()

    /** True when credentials are stored and can still be renewed. */
    fun isSignedIn(): Boolean = credentialsManager.hasValidCredentials()

    /**
     * Runs the sign-in in a Custom Tab and stores the result.
     *
     * `offline_access` is requested so that a refresh token comes back; without it the session
     * would end as soon as the first access token expires.
     */
    fun logIn(onFinished: (Throwable?) -> Unit) {
        WebAuthProvider.login(account)
            .withScheme(AppSettings.auth0Scheme)
            .withScope(SCOPE)
            .withAudience(AppSettings.auth0Audience)
            .start(
                activity,
                object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        try {
                            credentialsManager.saveCredentials(result)
                            tokenCache.put(result.toCachedToken(), System.currentTimeMillis())
                            onFinished(null)
                        } catch (error: CredentialsManagerException) {
                            onFinished(error)
                        }
                    }

                    override fun onFailure(error: AuthenticationException) = onFinished(error)
                },
            )
    }

    /**
     * Ends the session.
     *
     * The order matters: the browser session goes first, then the refresh token is revoked so that
     * it cannot outlive the sign-out, then the stored credentials are destroyed. Reading the
     * refresh token may raise the local authentication prompt; if that is declined the revocation
     * is skipped, but the credentials are destroyed all the same. [onFinished] is where the caller
     * tells the page and wipes the WebView.
     */
    fun logOut(onFinished: () -> Unit) {
        WebAuthProvider.logout(account)
            .withScheme(AppSettings.auth0Scheme)
            .start(
                activity,
                object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) = revokeRefreshTokenThenDiscard(onFinished)

                    override fun onFailure(error: AuthenticationException) =
                        revokeRefreshTokenThenDiscard(onFinished)
                },
            )
    }

    private fun revokeRefreshTokenThenDiscard(onFinished: () -> Unit) {
        credentialsManager.getCredentials(
            object : Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) {
                    val refreshToken = result.refreshToken
                    if (refreshToken == null) {
                        discardCredentials()
                        onFinished()
                        return
                    }
                    authenticationApi.revokeToken(refreshToken).start(
                        object : Callback<Void?, AuthenticationException> {
                            override fun onSuccess(result: Void?) {
                                discardCredentials()
                                onFinished()
                            }

                            override fun onFailure(error: AuthenticationException) {
                                discardCredentials()
                                onFinished()
                            }
                        },
                    )
                }

                override fun onFailure(error: CredentialsManagerException) {
                    discardCredentials()
                    onFinished()
                }
            },
        )
    }

    /**
     * Produces an access token.
     *
     * A token that is still usable is served from memory, which is what keeps the local
     * authentication prompt from appearing on every call. [forceRefresh] bypasses both the memory
     * cache and the stored access token and asks for a new one.
     */
    fun accessToken(forceRefresh: Boolean, onResult: (TokenOutcome) -> Unit) {
        if (!forceRefresh) {
            tokenCache.get(System.currentTimeMillis())?.let {
                onResult(TokenOutcome.Granted(it))
                return
            }
        }
        credentialsManager.getCredentials(
            null,
            MIN_TTL_SECONDS,
            emptyMap(),
            forceRefresh,
            object : Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) {
                    val token = result.toCachedToken()
                    tokenCache.put(token, System.currentTimeMillis())
                    onResult(TokenOutcome.Granted(token))
                }

                override fun onFailure(error: CredentialsManagerException) {
                    // A key that the system invalidated - a new fingerprint was enrolled, the screen
                    // lock was removed - leaves credentials that can never be decrypted again. They
                    // are thrown away here so that the next attempt is a clean sign-in.
                    if (error.invalidatesStoredCredentials()) discardCredentials()
                    onResult(TokenOutcome.Denied(error.toBridgeErrorCode(), error.message))
                }
            },
        )
    }

    /** Destroys the stored credentials and forgets the cached access token. */
    fun discardCredentials() {
        tokenCache.clear()
        credentialsManager.clearCredentials()
    }

    private fun Credentials.toCachedToken(): CachedAccessToken =
        CachedAccessToken(accessToken, expiresAt.time)

    private companion object {
        /** `offline_access` is what makes the session survive the first token expiry. */
        const val SCOPE = "openid profile email offline_access"

        /** Do not hand out a token that is about to expire. */
        const val MIN_TTL_SECONDS = 60

        /** One local authentication covers five minutes of token requests. */
        const val BIOMETRIC_SESSION_SECONDS = 300
    }
}

/**
 * Maps a storage failure onto the small set of reasons the bridge contract defines. Everything the
 * page is told is deliberately coarse; the detail stays on the device.
 */
internal fun CredentialsManagerException.toBridgeErrorCode(): BridgeErrorCode = when (this) {
    CredentialsManagerException.NO_CREDENTIALS ->
        BridgeErrorCode.NOT_LOGGED_IN

    CredentialsManagerException.NO_REFRESH_TOKEN,
    CredentialsManagerException.RENEW_FAILED,
    CredentialsManagerException.API_ERROR,
    CredentialsManagerException.NO_NETWORK,
    CredentialsManagerException.SESSION_EXPIRED
    ->
        BridgeErrorCode.REFRESH_FAILED

    CredentialsManagerException.BIOMETRIC_ERROR_USER_CANCELED,
    CredentialsManagerException.BIOMETRIC_ERROR_NEGATIVE_BUTTON,
    CredentialsManagerException.BIOMETRIC_ERROR_CANCELED
    ->
        BridgeErrorCode.USER_CANCELLED

    CredentialsManagerException.BIOMETRIC_ERROR_LOCKOUT,
    CredentialsManagerException.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
    CredentialsManagerException.BIOMETRIC_ERROR_NONE_ENROLLED,
    CredentialsManagerException.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
    CredentialsManagerException.BIOMETRIC_ERROR_HW_UNAVAILABLE,
    CredentialsManagerException.BIOMETRIC_ERROR_NO_ACTIVITY,
    CredentialsManagerException.BIOMETRIC_AUTHENTICATION_FAILED,
    CredentialsManagerException.BIOMETRIC_AUTHENTICATION_CHECK_FAILED,
    CredentialsManagerException.BIOMETRICS_INVALID_USER
    ->
        BridgeErrorCode.LOCKED

    else ->
        BridgeErrorCode.INTERNAL_ERROR
}

/**
 * True when the stored credentials can no longer be decrypted and should be destroyed: the key was
 * invalidated by a change to the device's biometrics or screen lock, or the ciphertext is unusable.
 */
internal fun CredentialsManagerException.invalidatesStoredCredentials(): Boolean =
    this == CredentialsManagerException.BIOMETRICS_INVALID_USER ||
        this == CredentialsManagerException.INVALID_CREDENTIALS ||
        this == CredentialsManagerException.CRYPTO_EXCEPTION
