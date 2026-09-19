package com.example.authnz.app

/**
 * The values the build put into `BuildConfig`, read from `android/local.properties`, Gradle
 * properties or the environment.
 *
 * None of them is a secret - a public client has no client secret - but none of them is committed
 * either, so a debug build made without any configuration carries placeholders and simply cannot
 * sign in. [isAuthConfigured] is what the screen checks before offering the sign-in button.
 */
object AppSettings {

    val auth0Domain: String = BuildConfig.AUTH0_DOMAIN
    val auth0ClientId: String = BuildConfig.AUTH0_CLIENT_ID
    val auth0Audience: String = BuildConfig.AUTH0_AUDIENCE

    /** The scheme of the sign-in callback. `https` means the callback is an Android App Link. */
    val auth0Scheme: String = BuildConfig.AUTH0_SCHEME

    /** The page the WebView loads, and the only origin the bridge talks to. */
    val webAppUrl: String = BuildConfig.WEB_APP_URL

    val isAuthConfigured: Boolean
        get() = auth0ClientId.isNotEmpty() && auth0Domain.isNotEmpty()
}
