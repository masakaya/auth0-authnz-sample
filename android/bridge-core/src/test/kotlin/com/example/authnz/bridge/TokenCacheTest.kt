package com.example.authnz.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryTokenCacheTest {

    private val now = 1_000_000L
    private val token = CachedAccessToken("eyJ...", expiresAtEpochMillis = now + 600_000)

    @Test
    fun `is empty before anything is stored`() {
        assertNull(MemoryTokenCache().get(now))
    }

    @Test
    fun `serves a token that is still usable`() {
        val cache = MemoryTokenCache()
        cache.put(token, now)

        assertEquals(token, cache.get(now))
        assertEquals(token, cache.get(now + 300_000))
    }

    @Test
    fun `stops serving a token once it is inside the safety margin`() {
        val cache = MemoryTokenCache(skewMillis = 30_000)
        cache.put(token, now)

        assertEquals(token, cache.get(token.expiresAtEpochMillis - 30_001))
        assertNull(cache.get(token.expiresAtEpochMillis - 30_000))
        assertNull(cache.get(token.expiresAtEpochMillis))
        assertNull(cache.get(token.expiresAtEpochMillis + 1))
    }

    @Test
    fun `does not keep a token that is already stale`() {
        val cache = MemoryTokenCache()
        cache.put(CachedAccessToken("eyJ...", expiresAtEpochMillis = now - 1), now)

        assertNull(cache.get(now))
    }

    @Test
    fun `forgets the token when it is cleared`() {
        val cache = MemoryTokenCache()
        cache.put(token, now)
        cache.clear()

        assertNull(cache.get(now))
    }

    @Test
    fun `uses a thirty second margin by default`() {
        assertEquals(30_000L, MemoryTokenCache.DEFAULT_SKEW_MILLIS)
    }
}

class PageGenerationTest {

    @Test
    fun `answers a request that belongs to the loaded page`() {
        val generation = PageGeneration()
        val pending = generation.current

        assertTrue(generation.isCurrent(pending))
    }

    @Test
    fun `drops a request that was made before a navigation`() {
        val generation = PageGeneration()
        val pending = generation.current
        generation.advance()

        assertFalse(generation.isCurrent(pending))
    }

    @Test
    fun `answers a request made after the navigation`() {
        val generation = PageGeneration()
        generation.advance()
        val pending = generation.current

        assertTrue(generation.isCurrent(pending))
        generation.advance()
        assertFalse(generation.isCurrent(pending))
    }
}
