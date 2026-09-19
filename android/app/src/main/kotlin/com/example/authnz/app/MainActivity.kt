package com.example.authnz.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.authnz.bridge.OriginPolicy

/**
 * The only screen: a sign-in button while there is no session, and the web application in a
 * WebView, with a sign-out button, once there is one.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var statusView: TextView
    private lateinit var logInButton: Button
    private lateinit var logOutButton: Button
    private lateinit var webView: WebView

    /** The one origin the WebView may show and the bridge may talk to. */
    private val originPolicy: OriginPolicy? = OriginPolicy.ofWebAppUrl(AppSettings.webAppUrl)

    /** Null when the bridge could not be injected; the page then behaves as in a plain browser. */
    private var bridge: NativeAuthBridge? = null

    private var busy = false
    private var pageLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        logInButton = findViewById(R.id.log_in)
        logOutButton = findViewById(R.id.log_out)
        webView = findViewById(R.id.web_view)

        session = SessionManager(this)

        logInButton.setOnClickListener { logIn() }
        logOutButton.setOnClickListener { logOut() }

        configureWebView()
        render()
    }

    private fun logIn() {
        if (busy) return
        busy = true
        render()
        session.logIn { error ->
            busy = false
            if (error != null) {
                Log.w(TAG, "sign-in failed", error)
                statusView.setText(R.string.status_login_failed)
                render(keepStatus = true)
            } else {
                render()
            }
        }
    }

    /**
     * Signs out, then tells the page and wipes the WebView.
     *
     * [SessionManager.logOut] has already ended the browser session, revoked the refresh token and
     * destroyed the stored credentials by the time this runs. The notice goes out first, so that a
     * page still on screen drops the token it holds, and the WebView's own state is cleared after
     * it: both are posted to the WebView, so they stay in that order.
     */
    private fun logOut() {
        if (busy) return
        busy = true
        render()
        session.logOut {
            bridge?.notifyLogout()
            webView.post {
                clearWebViewState()
                webView.loadUrl(BLANK_PAGE)
                pageLoaded = false
                busy = false
                render()
            }
        }
    }

    private fun configureWebView() {
        val policy = originPolicy ?: return

        // Remote debugging exposes everything the page holds, so it stays out of release builds.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // An https page must not pull anything over plain http.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                bridge?.onPageStarted()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url?.toString()
                if (policy.containsUrl(url)) return false
                // Anything outside the web application belongs in a real browser, where the user
                // can see the address bar and where this WebView's session does not follow it.
                openInExternalBrowser(url)
                return true
            }
        }

        // Fail closed: when the bridge cannot be restricted to one origin it is not injected, and
        // the field stays null so that nothing later tries to send a token through it.
        val candidate = NativeAuthBridge(webView, policy, session)
        bridge = if (candidate.install()) candidate else null
    }

    private fun openInExternalBrowser(url: String?) {
        if (url == null) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "no browser to open $url", error)
        }
    }

    /** Removes every trace the web application left in the WebView. */
    private fun clearWebViewState() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(this).clearFormData()
        webView.clearCache(true)
        webView.clearHistory()
    }

    private fun render(keepStatus: Boolean = false) {
        val signedIn = AppSettings.isAuthConfigured && session.isSignedIn()
        val showWebView = signedIn && originPolicy != null && !busy

        logInButton.visibility = if (signedIn || busy) View.GONE else View.VISIBLE
        logInButton.isEnabled = AppSettings.isAuthConfigured
        logOutButton.visibility = if (signedIn && !busy) View.VISIBLE else View.GONE
        webView.visibility = if (showWebView) View.VISIBLE else View.GONE

        if (showWebView && !pageLoaded) {
            pageLoaded = true
            webView.loadUrl(AppSettings.webAppUrl)
        }

        if (keepStatus) return
        statusView.setText(
            when {
                busy -> R.string.status_working
                !AppSettings.isAuthConfigured -> R.string.status_not_configured
                originPolicy == null -> R.string.status_bad_web_app_url
                signedIn -> R.string.status_signed_in
                else -> R.string.status_signed_out
            },
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val BLANK_PAGE = "about:blank"
    }
}
