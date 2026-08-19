package com.webwithroni.voicejarvis

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState
import com.webwithroni.voicejarvis.orb.ParticleOrbView
import kotlinx.coroutines.launch

/**
 * JARVIS authentication surface.
 *
 * Authentication policy:
 * - Firebase Email + Password
 * - Forgot password
 * - No signup
 * - No phone authentication
 * - No Google authentication
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var orb: ParticleOrbView

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var signInButton: Button
    private lateinit var forgotPasswordButton: TextView
    private lateinit var passwordToggle: TextView

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var authenticationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AuthManager.initialize(this)

        if (
            AuthManager.isSignedIn() &&
            !AuthManager.isAnonymous()
        ) {
            openPostAuthDestination()
            return
        }

        setContentView(R.layout.activity_auth)

        bindViews()
        configureOrb()
        configureListeners()
    }

    private fun bindViews() {

        orb = findViewById(R.id.authOrb)

        emailInput =
            findViewById(R.id.emailInput)

        passwordInput =
            findViewById(R.id.passwordInput)

        signInButton =
            findViewById(R.id.signInButton)

        forgotPasswordButton =
            findViewById(R.id.forgotPasswordButton)

        passwordToggle =
            findViewById(R.id.passwordToggle)

        progressBar =
            findViewById(R.id.authProgress)

        statusText =
            findViewById(R.id.authStatusText)
    }

    private fun configureOrb() {

        orb.setState(
            OrbState.LISTENING
        )

        orb.setActivity(
            OrbActivity.NONE
        )

        orb.contentDescription =
            "JARVIS neural orb"
    }

    private fun configureListeners() {

        signInButton.setOnClickListener {
            signIn()
        }

        forgotPasswordButton.setOnClickListener {
            openPasswordReset()
        }

        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
    }

    private fun signIn() {

        if (authenticationInProgress) {
            return
        }

        val email =
            emailInput.text
                .toString()
                .trim()

        val password =
            passwordInput.text
                .toString()

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            statusText.text =
                "Enter a valid email address."

            emailInput.requestFocus()
            return
        }

        if (password.length < 6) {

            statusText.text =
                "Enter your password."

            passwordInput.requestFocus()
            return
        }

        authenticationInProgress = true

        setLoading(true)

        statusText.text =
            "Signing in securely…"

        orb.setState(
            OrbState.THINKING
        )

        lifecycleScope.launch {

            val result =
                AuthManager.signInWithEmailPassword(
                    email = email,
                    password = password
                )

            result.fold(

                onSuccess = {
                    authenticationInProgress = false

                    setLoading(false)

                    orb.setState(
                        OrbState.SPEAKING
                    )

                    statusText.text =
                        "Access confirmed."

                    openPostAuthDestination()
                },

                onFailure = { error ->

                    authenticationInProgress = false

                    setLoading(false)

                    orb.setState(
                        OrbState.ERROR
                    )

                    statusText.text =
                        friendlyAuthError(error)

                    passwordInput.requestFocus()
                    passwordInput.selectAll()
                }
            )
        }
    }

    private fun togglePasswordVisibility() {

        val selection =
            passwordInput.selectionStart

        val showingPlainText =
            passwordInput.inputType ==
                (
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    )

        passwordInput.inputType =
            if (showingPlainText) {

                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

            } else {

                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }

        passwordInput.setSelection(
            selection.coerceAtLeast(0)
                .coerceAtMost(passwordInput.text.length)
        )

        passwordToggle.text =
            if (showingPlainText) {
                "SHOW"
            } else {
                "HIDE"
            }
    }

    private fun setLoading(
        loading: Boolean
    ) {

        emailInput.isEnabled =
            !loading

        passwordInput.isEnabled =
            !loading

        signInButton.isEnabled =
            !loading

        forgotPasswordButton.isEnabled =
            !loading

        progressBar.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun friendlyAuthError(
        error: Throwable
    ): String {

        val message =
            error.message
                ?: return "Sign-in could not be completed."

        return when {

            message.contains(
                "invalid-credential",
                ignoreCase = true
            ) ||
            message.contains(
                "wrong-password",
                ignoreCase = true
            ) ||
            message.contains(
                "user-not-found",
                ignoreCase = true
            ) ->
                "Email or password is incorrect."

            message.contains(
                "too-many-requests",
                ignoreCase = true
            ) ->
                "Too many attempts. Please try again later."

            message.contains(
                "network",
                ignoreCase = true
            ) ->
                "Network connection unavailable."

            message.contains(
                "disabled",
                ignoreCase = true
            ) ->
                "This JARVIS account is disabled."

            else ->
                message
        }
    }

    private fun openPasswordReset() {

        startActivity(
            Intent(
                this,
                ResetPasswordActivity::class.java
            )
        )
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
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )

        finish()
    }

    override fun onDestroy() {
        authenticationInProgress = false
        super.onDestroy()
    }
}
