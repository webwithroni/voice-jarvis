package com.webwithroni.voicejarvis

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var backButton: TextView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_reset_password
        )

        emailInput = findViewById(R.id.resetEmailInput)
        sendButton = findViewById(R.id.resetSendButton)
        backButton = findViewById(R.id.resetBackButton)
        progress = findViewById(R.id.resetProgress)
        status = findViewById(R.id.resetStatus)

        sendButton.setOnClickListener {
            sendReset()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun sendReset() {

        val email =
            emailInput.text
                .toString()
                .trim()

        if (
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {

            status.text =
                "Enter a valid email address."

            emailInput.requestFocus()
            return
        }

        setLoading(true)

        lifecycleScope.launch {

            val result =
                AuthManager.sendPasswordResetEmail(
                    email
                )

            result.fold(

                onSuccess = {

                    setLoading(false)

                    status.text =
                        "Reset link sent. Check your email."
                },

                onFailure = { error ->

                    setLoading(false)

                    status.text =
                        error.message
                            ?: "Unable to send the reset link."
                }
            )
        }
    }

    private fun setLoading(
        loading: Boolean
    ) {

        emailInput.isEnabled =
            !loading

        sendButton.isEnabled =
            !loading

        backButton.isEnabled =
            !loading

        progress.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }
}
