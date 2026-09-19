package com.example.authnz.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The only screen: a sign-in button while there is no session, and a sign-out button once there is.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var statusView: TextView
    private lateinit var logInButton: Button
    private lateinit var logOutButton: Button

    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        logInButton = findViewById(R.id.log_in)
        logOutButton = findViewById(R.id.log_out)

        session = SessionManager(this)

        logInButton.setOnClickListener { logIn() }
        logOutButton.setOnClickListener { logOut() }

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

    private fun logOut() {
        if (busy) return
        busy = true
        render()
        session.logOut {
            busy = false
            render()
        }
    }

    private fun render(keepStatus: Boolean = false) {
        val signedIn = AppSettings.isAuthConfigured && session.isSignedIn()

        logInButton.visibility = if (signedIn || busy) View.GONE else View.VISIBLE
        logInButton.isEnabled = AppSettings.isAuthConfigured
        logOutButton.visibility = if (signedIn && !busy) View.VISIBLE else View.GONE

        if (keepStatus) return
        statusView.setText(
            when {
                busy -> R.string.status_working
                !AppSettings.isAuthConfigured -> R.string.status_not_configured
                signedIn -> R.string.status_signed_in
                else -> R.string.status_signed_out
            },
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
