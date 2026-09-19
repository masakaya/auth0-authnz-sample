package com.example.authnz.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BridgeMessagesTest {

    @Test
    fun `reads a getToken request`() {
        val request = BridgeMessages.parseRequest(
            """{"version":1,"type":"getToken","requestId":"6f1c1e0a","forceRefresh":false}""",
        )

        assertEquals(BridgeRequest.GetToken("6f1c1e0a", forceRefresh = false), request)
    }

    @Test
    fun `treats a missing forceRefresh as false`() {
        val request = BridgeMessages.parseRequest("""{"version":1,"type":"getToken","requestId":"a"}""")

        assertEquals(BridgeRequest.GetToken("a", forceRefresh = false), request)
    }

    @Test
    fun `reads forceRefresh when it is set`() {
        val request = BridgeMessages.parseRequest(
            """{"version":1,"type":"getToken","requestId":"a","forceRefresh":true}""",
        )

        assertEquals(BridgeRequest.GetToken("a", forceRefresh = true), request)
    }

    @Test
    fun `ignores the order of the fields and any extra field`() {
        val request = BridgeMessages.parseRequest(
            """{"requestId":"a","extra":{"nested":[1,2]},"type":"getToken","version":1}""",
        )

        assertEquals(BridgeRequest.GetToken("a", forceRefresh = false), request)
    }

    @Test
    fun `accepts a version written as a whole floating point number`() {
        val request = BridgeMessages.parseRequest("""{"version":1.0,"type":"getToken","requestId":"a"}""")

        assertEquals(BridgeRequest.GetToken("a", forceRefresh = false), request)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Not an object at all.
            "",
            "   ",
            "null",
            "[]",
            "\"getToken\"",
            // Malformed documents.
            "{",
            """{"version":1,"type":"getToken","requestId":"a"""",
            """{"version":1,"type":"getToken","requestId":"a"} trailing""",
            // Versions the contract does not describe.
            """{"version":2,"type":"getToken","requestId":"a"}""",
            """{"version":"1","type":"getToken","requestId":"a"}""",
            """{"type":"getToken","requestId":"a"}""",
            // Types the contract does not describe.
            """{"version":1,"type":"revokeToken","requestId":"a"}""",
            """{"version":1,"type":"tokenResult","requestId":"a"}""",
            """{"version":1,"requestId":"a"}""",
            // Request ids that cannot be answered.
            """{"version":1,"type":"getToken"}""",
            """{"version":1,"type":"getToken","requestId":""}""",
            """{"version":1,"type":"getToken","requestId":7}""",
            // Fields of the wrong type.
            """{"version":1,"type":"getToken","requestId":"a","forceRefresh":"true"}""",
            """{"version":1,"type":"getToken","requestId":"a","forceRefresh":1}""",
        ],
    )
    fun `ignores anything the contract does not describe`(payload: String) {
        assertNull(BridgeMessages.parseRequest(payload))
    }

    @Test
    fun `writes a tokenResult`() {
        assertEquals(
            """{"version":1,"type":"tokenResult","requestId":"6f1c1e0a","accessToken":"eyJ...",""" +
                """"expiresAt":1789000000000}""",
            BridgeMessages.tokenResult("6f1c1e0a", "eyJ...", 1_789_000_000_000L),
        )
    }

    @Test
    fun `writes an error without a message`() {
        assertEquals(
            """{"version":1,"type":"error","requestId":"a","code":"not_logged_in"}""",
            BridgeMessages.error("a", BridgeErrorCode.NOT_LOGGED_IN),
        )
    }

    @Test
    fun `writes an error with a message`() {
        assertEquals(
            """{"version":1,"type":"error","requestId":"a","code":"internal_error","message":"boom"}""",
            BridgeMessages.error("a", BridgeErrorCode.INTERNAL_ERROR, "boom"),
        )
    }

    @Test
    fun `writes a logout notification without a request id`() {
        assertEquals("""{"version":1,"type":"logout"}""", BridgeMessages.logout())
    }

    @Test
    fun `escapes values so that a reply cannot be forged`() {
        val forged = BridgeMessages.error("a\",\"code\":\"not_logged_in", BridgeErrorCode.LOCKED)

        assertEquals(
            """{"version":1,"type":"error","requestId":"a\",\"code\":\"not_logged_in","code":"locked"}""",
            forged,
        )
        // The escaped document still reads back as a single request id.
        val members = MiniJson.parseObject(forged)
        assertEquals("a\",\"code\":\"not_logged_in", members?.get("requestId"))
        assertEquals("locked", members?.get("code"))
    }

    @Test
    fun `uses the wire spelling of every error code`() {
        assertEquals(
            listOf("not_logged_in", "refresh_failed", "user_cancelled", "locked", "internal_error"),
            BridgeErrorCode.entries.map { it.wireValue },
        )
    }

    @Test
    fun `names the injected object the same on every platform`() {
        assertEquals("NativeAuthBridge", BRIDGE_OBJECT_NAME)
    }
}
