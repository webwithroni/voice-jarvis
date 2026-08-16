package com.webwithroni.voicejarvis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseUser
import com.webwithroni.voicejarvis.orb.HumanoidOrbView
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState
import kotlinx.coroutines.launch

/**
 * Google-only authentication surface.
 *
 * UI is deliberately separate from authentication logic.
 * AuthManager owns the credential flow.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var orb: HumanoidOrbView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        if (
            AuthManager.isSignedIn() &&
            !AuthManager.isAnonymous()
        ) {
            AuthManager.initialize(
                this
            )

            openPostAuthDestination()
            return
        }

        setContentView(
            R.layout.activity_auth
        )

        signInButton =
            findViewById(
                R.id.googleSignInButton
            )

        progressBar =
            findViewById(
                R.id.googleSignInProgress
            )

        statusText =
            findViewById(
                R.id.authStatusText
            )

        orb =
            findViewById(
                R.id.authOrb
            )

        orb.setState(
            OrbState.LISTENING
        )

        orb.setActivity(
            OrbActivity.NONE
        )

        orb.setContentDescription(
            "JARVIS neural orb"
        )

        signInButton.setOnClickListener {
            beginGoogleSignIn()
        }
    }

    private fun beginGoogleSignIn() {

        setLoading(
            true
        )

        orb.setState(
            OrbState.THINKING
        )

        statusText.text =
            "Opening secure Google sign-in…"

        lifecycleScope.launch {

            val result =
                AuthManager
                    .signInWithGoogle(
                        this@AuthActivity
                    )

            result.fold(
                onSuccess = {
                    onAuthenticationSuccess(
                        it
                    )
                },
                onFailure = {
                    onAuthenticationFailure(
                        it
                    )
                }
            )
        }
    }

    private fun onAuthenticationSuccess(
        user: FirebaseUser
    ) {

        setLoading(
            false
        )

        orb.setState(
            OrbState.LISTENING
        )

        statusText.text =
            "Welcome ${user.displayName ?: "back"}."

        openPostAuthDestination()
    }

    private fun onAuthenticationFailure(
        error: Throwable
    ) {

        setLoading(
            false
        )

        orb.setState(
            OrbState.ERROR
        )

        statusText.text =
            "Google sign-in was not completed."

        Toast.makeText(
            this,
            error.message ?: "Google sign-in failed.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setLoading(
        loading: Boolean
    ) {

        signInButton.isEnabled =
            !loading

        progressBar.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun openPostAuthDestination() {

        val destination =
            if (
                AuthManager.hasCompletedOnboarding()
            ) {
                MainActivity::class.java
            } else {
                OnboardingActivity::class.java
            }

        startActivity(
            Intent(
                this,
                destination
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }

    override fun onResume() {
        super.onResume()

        if (
            ::orb.isInitialized &&
            !AuthManager.isSignedIn()
        ) {
            orb.setState(
                OrbState.LISTENING
            )
        }
    }
}
