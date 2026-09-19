package com.example.authnz.bridge

import java.util.concurrent.atomic.AtomicLong

/** An access token together with the instant it stops being usable. */
data class CachedAccessToken(
    val accessToken: String,
    val expiresAtEpochMillis: Long,
)

/**
 * Holds the access token in memory for as long as it is usable.
 *
 * This is what keeps the local authentication prompt from appearing on every `getToken` call: the
 * token is read from secure storage once, then served from here until it is close to expiring.
 * Nothing is ever written to disk, so the cache dies with the process.
 *
 * [skewMillis] is subtracted from the expiry so that a token is never handed out with so little
 * life left that it expires on the way to the API.
 */
class MemoryTokenCache(private val skewMillis: Long = DEFAULT_SKEW_MILLIS) {

    @Volatile
    private var cached: CachedAccessToken? = null

    /** Returns the cached token when it is still usable at [nowEpochMillis], otherwise `null`. */
    fun get(nowEpochMillis: Long): CachedAccessToken? {
        val current = cached ?: return null
        return if (isFresh(current, nowEpochMillis)) current else null
    }

    /** Replaces the cached token. A token that is already stale is not kept. */
    fun put(token: CachedAccessToken, nowEpochMillis: Long) {
        cached = if (isFresh(token, nowEpochMillis)) token else null
    }

    /** Forgets the cached token. Called on sign-out and whenever the credentials are discarded. */
    fun clear() {
        cached = null
    }

    private fun isFresh(token: CachedAccessToken, nowEpochMillis: Long): Boolean =
        nowEpochMillis < token.expiresAtEpochMillis - skewMillis

    companion object {
        /** Matches the 30 second margin the web side applies to the tokens it receives. */
        const val DEFAULT_SKEW_MILLIS: Long = 30_000
    }
}

/**
 * Numbers the page loads so that a reply computed for one page is never delivered to the next.
 *
 * Reading a token is asynchronous and may sit behind a local authentication prompt. By the time it
 * finishes, the WebView may have navigated. The handler captures [current] when the request comes
 * in and checks [isCurrent] before replying; a navigation calls [advance] in between and the stale
 * reply is dropped.
 */
class PageGeneration {

    private val generation = AtomicLong(0)

    /** The generation a request seen right now belongs to. */
    val current: Long
        get() = generation.get()

    /** Marks the start of a new page load and returns the new generation. */
    fun advance(): Long = generation.incrementAndGet()

    /** True when [generation] is still the page that is loaded. */
    fun isCurrent(generation: Long): Boolean = this.generation.get() == generation
}
