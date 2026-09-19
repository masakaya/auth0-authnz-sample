package com.example.authnz.bridge

import java.net.URI
import java.net.URISyntaxException

/**
 * A web origin reduced to the three parts the contract compares: scheme, host and port.
 *
 * The default port of the scheme is filled in when the URL omits it, so that `https://example.com`
 * and `https://example.com:443` are the same origin, while `http://example.com` is not.
 */
data class Origin(
    val scheme: String,
    val host: String,
    val port: Int,
) {

    /** The canonical `scheme://host[:port]` form, with the default port left out. */
    val value: String
        get() = if (port == defaultPortFor(scheme)) "$scheme://$host" else "$scheme://$host:$port"

    /** True when the origin is served over plain HTTP, which only debug builds may talk to. */
    val isCleartext: Boolean
        get() = scheme == SCHEME_HTTP

    override fun toString(): String = value

    companion object {
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"

        /**
         * Parses the origin of [url], or returns `null` when it is not an absolute http(s) URL.
         * Other schemes are rejected on purpose: the bridge is only ever spoken over http(s).
         */
        fun parse(url: String?): Origin? {
            if (url.isNullOrEmpty()) return null
            val uri = try {
                URI(url)
            } catch (_: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != SCHEME_HTTP && scheme != SCHEME_HTTPS) return null
            val host = uri.host?.lowercase() ?: return null
            if (host.isEmpty()) return null
            val port = if (uri.port == -1) defaultPortFor(scheme) else uri.port
            return Origin(scheme, host, port)
        }

        private fun defaultPortFor(scheme: String): Int = if (scheme == SCHEME_HTTP) 80 else 443
    }
}

/**
 * The single origin the bridge is willing to talk to.
 *
 * The same object answers two questions: whether an incoming message may be handled, and whether a
 * navigation stays inside the web application. Both are exact comparisons - a message that merely
 * claims an origin in its payload carries no weight.
 */
class OriginPolicy(val allowed: Origin) {

    /** The rule handed to the platform so that the bridge object is injected nowhere else. */
    val allowedOriginRule: String
        get() = allowed.value

    /** True when [sourceOrigin], as reported by the platform, is exactly the allowed origin. */
    fun accepts(sourceOrigin: String?): Boolean = Origin.parse(sourceOrigin) == allowed

    /** True when [url] belongs to the allowed origin. Everything else belongs in a real browser. */
    fun containsUrl(url: String?): Boolean = Origin.parse(url) == allowed

    companion object {
        /**
         * Builds a policy from the configured web application URL, or returns `null` when that URL
         * is unusable. A missing configuration must not silently widen what the bridge accepts.
         */
        fun ofWebAppUrl(webAppUrl: String?): OriginPolicy? =
            Origin.parse(webAppUrl)?.let { OriginPolicy(it) }
    }
}
