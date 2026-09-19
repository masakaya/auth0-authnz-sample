package com.example.authnz.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OriginTest {

    @Test
    fun `keeps scheme host and port`() {
        assertEquals(Origin("http", "localhost", 4200), Origin.parse("http://localhost:4200"))
    }

    @Test
    fun `fills in the default port of the scheme`() {
        assertEquals(Origin("https", "example.com", 443), Origin.parse("https://example.com"))
        assertEquals(Origin("http", "example.com", 80), Origin.parse("http://example.com"))
    }

    @Test
    fun `takes the origin of a URL that has a path and a query`() {
        assertEquals(
            Origin("https", "example.com", 443),
            Origin.parse("https://example.com/app/index.html?tab=1#top"),
        )
    }

    @Test
    fun `lowercases the scheme and the host`() {
        assertEquals(Origin("https", "example.com", 443), Origin.parse("HTTPS://Example.COM"))
    }

    @Test
    fun `prints the canonical form and hides the default port`() {
        assertEquals("https://example.com", Origin("https", "example.com", 443).value)
        assertEquals("http://localhost:4200", Origin("http", "localhost", 4200).value)
        assertEquals("https://example.com:8443", Origin("https", "example.com", 8443).value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "example.com",
            "/app/index.html",
            "file:///android_asset/index.html",
            "javascript:alert(1)",
            "data:text/html,<p>x</p>",
            "content://com.example/file",
            "intent://example.com#Intent;scheme=https;end",
            "https://",
        ],
    )
    fun `rejects anything that is not an absolute http or https URL`(url: String) {
        assertNull(Origin.parse(url))
    }

    @Test
    fun `rejects a null URL`() {
        assertNull(Origin.parse(null))
    }

    @Test
    fun `reports cleartext only for http`() {
        assertTrue(Origin.parse("http://localhost:4200")!!.isCleartext)
        assertFalse(Origin.parse("https://example.com")!!.isCleartext)
    }
}

class OriginPolicyTest {

    private val policy = OriginPolicy.ofWebAppUrl("http://localhost:4200/app")!!

    @Test
    fun `is built from the configured web application URL`() {
        assertEquals(Origin("http", "localhost", 4200), policy.allowed)
        assertEquals("http://localhost:4200", policy.allowedOriginRule)
    }

    @Test
    fun `is not built from an unusable URL`() {
        assertNull(OriginPolicy.ofWebAppUrl(null))
        assertNull(OriginPolicy.ofWebAppUrl(""))
        assertNull(OriginPolicy.ofWebAppUrl("not a url"))
    }

    @Test
    fun `accepts the allowed origin`() {
        assertTrue(policy.accepts("http://localhost:4200"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Different scheme.
            "https://localhost:4200",
            // Different port.
            "http://localhost:4201",
            "http://localhost",
            // Different host, including hosts that merely look similar.
            "http://127.0.0.1:4200",
            "http://localhost.evil.test:4200",
            "http://evil.test:4200",
            // Not an origin at all.
            "null",
            "",
        ],
    )
    fun `refuses every other source origin`(sourceOrigin: String) {
        assertFalse(policy.accepts(sourceOrigin))
    }

    @Test
    fun `refuses a missing source origin`() {
        assertFalse(policy.accepts(null))
    }

    @Test
    fun `keeps navigations inside the web application`() {
        assertTrue(policy.containsUrl("http://localhost:4200/orders/42"))
        assertFalse(policy.containsUrl("https://accounts.example.com/login"))
        assertFalse(policy.containsUrl("market://details?id=com.example"))
    }
}
