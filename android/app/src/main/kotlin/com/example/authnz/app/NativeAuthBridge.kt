package com.example.authnz.app

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.authnz.bridge.BRIDGE_OBJECT_NAME
import com.example.authnz.bridge.BridgeMessages
import com.example.authnz.bridge.BridgeRequest
import com.example.authnz.bridge.OriginPolicy
import com.example.authnz.bridge.PageGeneration

/**
 * The Android end of the native bridge.
 *
 * The object is injected with `WebViewCompat.addWebMessageListener`, which is the only transport
 * here that lets the platform restrict injection to one origin. The restriction is enforced twice:
 * once by handing the platform a single allowed origin rule, and once in the listener, which
 * compares the origin the platform reports and refuses anything that is not the main frame. A
 * message that fails either check is dropped without a reply, because an error reply would itself
 * tell the sender that a bridge is there.
 *
 * Nothing written inside a message is trusted; only what the platform reports is.
 */
class NativeAuthBridge(
    private val webView: WebView,
    private val policy: OriginPolicy,
    private val session: SessionManager,
) {

    private val pageGeneration = PageGeneration()

    /**
     * The channel back to the page, kept so that an unsolicited sign-out notice can be sent. It is
     * dropped on every navigation, because the proxy belongs to the page that handed it over.
     */
    @Volatile
    private var replyProxy: JavaScriptReplyProxy? = null

    /**
     * Injects the bridge, unless the WebView on this device cannot restrict it to one origin.
     *
     * Returns false without injecting anything on such a device: the page then sees no bridge and
     * behaves exactly as it does in a plain browser. Falling back to a less restricted transport
     * would hand the token to whatever the WebView happens to be showing.
     */
    fun install(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Log.i(TAG, "WebView does not support origin-restricted message listeners; not injecting")
            return false
        }
        val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, reply ->
            onMessage(message, sourceOrigin, isMainFrame, reply)
        }
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_OBJECT_NAME,
            setOf(policy.allowedOriginRule),
            listener,
        )
        return true
    }

    /** Called when the WebView starts loading a page, so that older requests stop being answered. */
    fun onPageStarted() {
        pageGeneration.advance()
        replyProxy = null
    }

    /** Tells the page that the native side signed out. Sent after the credentials are destroyed. */
    fun notifyLogout() {
        val reply = replyProxy ?: return
        val generation = pageGeneration.current
        post(generation, reply, BridgeMessages.logout())
    }

    private fun onMessage(
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        if (!policy.accepts(sourceOrigin.toString())) return
        val payload = message.data ?: return
        val request = BridgeMessages.parseRequest(payload) ?: return

        replyProxy = reply
        val generation = pageGeneration.current

        when (request) {
            is BridgeRequest.GetToken -> session.accessToken(request.forceRefresh) { outcome ->
                val response = when (outcome) {
                    is TokenOutcome.Granted -> BridgeMessages.tokenResult(
                        request.requestId,
                        outcome.token.accessToken,
                        outcome.token.expiresAtEpochMillis,
                    )

                    is TokenOutcome.Denied -> {
                        Log.i(TAG, "token request denied: ${outcome.code} ${outcome.detail.orEmpty()}")
                        BridgeMessages.error(request.requestId, outcome.code)
                    }
                }
                post(generation, reply, response)
            }
        }
    }

    /**
     * Delivers a reply on the WebView's thread, unless the page it was computed for has gone.
     *
     * Producing a token is asynchronous and may wait behind a local authentication prompt, so by
     * the time the answer exists the WebView may already be showing something else.
     */
    private fun post(generation: Long, reply: JavaScriptReplyProxy, payload: String) {
        webView.post {
            if (!pageGeneration.isCurrent(generation)) return@post
            try {
                reply.postMessage(payload)
            } catch (error: IllegalStateException) {
                Log.i(TAG, "reply channel is gone", error)
            }
        }
    }

    private companion object {
        const val TAG = "NativeAuthBridge"
    }
}
