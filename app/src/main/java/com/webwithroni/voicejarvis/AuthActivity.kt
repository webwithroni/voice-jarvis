package com.webwithroni.voicejarvis

import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.webwithroni.voicejarvis.orb.HumanoidOrbView
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState
import kotlinx.coroutines.launch

/**
 * JARVIS authentication surface.
 *
 * Primary authentication:
 * - Firebase Phone OTP
 *
 * Secondary authentication:
 * - Google
 *
 * No anonymous user is created here.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var orb: HumanoidOrbView

    private lateinit var phoneInput: EditText
    private lateinit var sendOtpButton: Button

    private lateinit var otpSection: View
    private lateinit var otpInput: EditText
    private lateinit var verifyOtpButton: Button
    private lateinit var resendOtpButton: TextView

    private lateinit var googleSignInButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var verificationId: String? = null
    private var resendToken:
        PhoneAuthProvider.ForceResendingToken? = null

    private var normalizedPhoneNumber: String? = null

    private var resendTimer: CountDownTimer? = null

    private var authenticationInProgress =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        AuthManager.initialize(
            this
        )

        if (
            AuthManager.isSignedIn() &&
            !AuthManager.isAnonymous()
        ) {
            openPostAuthDestination()
            return
        }

        setContentView(
            R.layout.activity_auth
        )

        bindViews()
        configureOrb()
        configureListeners()

        if (
            savedInstanceState != null
        ) {
            verificationId =
                savedInstanceState.getString(
                    STATE_VERIFICATION_ID
                )

            normalizedPhoneNumber =
                savedInstanceState.getString(
                    STATE_PHONE_NUMBER
                )

            if (
                verificationId != null
            ) {
                showOtpStage()
            }
        }
    }

    private fun bindViews() {

        orb =
            findViewById(
                R.id.authOrb
            )

        phoneInput =
            findViewById(
                R.id.phoneNumberInput
            )

        sendOtpButton =
            findViewById(
                R.id.sendOtpButton
            )

        otpSection =
            findViewById(
                R.id.otpSection
            )

        otpInput =
            findViewById(
                R.id.otpInput
            )

        verifyOtpButton =
            findViewById(
                R.id.verifyOtpButton
            )

        resendOtpButton =
            findViewById(
                R.id.resendOtpButton
            )

        googleSignInButton =
            findViewById(
                R.id.googleSignInButton
            )

        progressBar =
            findViewById(
                R.id.authProgress
            )

        statusText =
            findViewById(
                R.id.authStatusText
            )

        otpInput.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    6
                )
            )
    }

    private fun configureOrb() {

        orb.setState(
            OrbState.LISTENING
        )

        orb.setActivity(
            OrbActivity.NONE
        )

        orb.setContentDescription(
            "JARVIS neural orb"
        )
    }

    private fun configureListeners() {

        sendOtpButton.setOnClickListener {
            sendOtp()
        }

        verifyOtpButton.setOnClickListener {
            verifyOtp()
        }

        resendOtpButton.setOnClickListener {
            resendOtp()
        }

        googleSignInButton.setOnClickListener {
            beginGoogleSignIn()
        }
    }

    private fun sendOtp() {

        val phoneNumber =
            normalizePhoneNumber(
                phoneInput.text
                    .toString()
            )

        if (
            phoneNumber == null
        ) {

            statusText.text =
                "Enter a valid phone number."

            phoneInput.requestFocus()

            return
        }

        normalizedPhoneNumber =
            phoneNumber

        setAuthenticationLoading(
            true
        )

        statusText.text =
            "Sending verification code…"

        orb.setState(
            OrbState.THINKING
        )

        AuthManager.startPhoneVerification(
            activity = this,
            phoneNumber = phoneNumber,
            callbacks =
                phoneVerificationCallbacks
        )
    }

    private val phoneVerificationCallbacks =
        object :
            AuthManager.PhoneVerificationCallbacks {

            override fun onVerificationCompleted(
                credential: PhoneAuthCredential
            ) {

                runOnUiThread {

                    statusText.text =
                        "Phone number verified automatically."

                    orb.setState(
                        OrbState.SPEAKING
                    )
                }

                completePhoneCredential(
                    credential
                )
            }

            override fun onCodeSent(
                verificationId: String,
                resendToken:
                    PhoneAuthProvider.ForceResendingToken
            ) {

                runOnUiThread {

                    this@AuthActivity
                        .verificationId =
                        verificationId

                    this@AuthActivity
                        .resendToken =
                        resendToken

                    setAuthenticationLoading(
                        false
                    )

                    showOtpStage()

                    statusText.text =
                        "Verification code sent."

                    otpInput.requestFocus()

                    startResendTimer()
                }
            }

            override fun onVerificationFailed(
                error: FirebaseException
            ) {

                runOnUiThread {

                    setAuthenticationLoading(
                        false
                    )

                    orb.setState(
                        OrbState.ERROR
                    )

                    statusText.text =
                        friendlyPhoneError(
                            error
                        )

                    Toast.makeText(
                        this@AuthActivity,
                        error.message
                            ?: "Phone verification failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onCodeAutoRetrievalTimeOut(
                verificationId: String
            ) {

                runOnUiThread {

                    this@AuthActivity
                        .verificationId =
                        verificationId

                    setAuthenticationLoading(
                        false
                    )

                    statusText.text =
                        "Enter the 6-digit code from SMS."
                }
            }
        }

    private fun verifyOtp() {

        val id =
            verificationId

        if (
            id.isNullOrBlank()
        ) {

            statusText.text =
                "Request a new verification code."

            return
        }

        val code =
            otpInput.text
                .toString()
                .trim()

        if (
            !code.matches(
                Regex("\\d{6}")
            )
        ) {

            statusText.text =
                "Enter the 6-digit verification code."

            otpInput.requestFocus()

            return
        }

        setAuthenticationLoading(
            true
        )

        statusText.text =
            "Verifying your code…"

        orb.setState(
            OrbState.THINKING
        )

        lifecycleScope.launch {

            val result =
                AuthManager.verifyPhoneCode(
                    verificationId = id,
                    verificationCode = code
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

    private fun resendOtp() {

        val phone =
            normalizedPhoneNumber

        val token =
            resendToken

        if (
            phone.isNullOrBlank() ||
            token == null
        ) {

            statusText.text =
                "Wait for the current verification request."
            return
        }

        setAuthenticationLoading(
            true
        )

        statusText.text =
            "Sending a new verification code…"

        AuthManager.resendPhoneVerification(
            activity = this,
            phoneNumber = phone,
            resendToken = token,
            callbacks =
                phoneVerificationCallbacks
        )
    }

    private fun completePhoneCredential(
        credential: PhoneAuthCredential
    ) {

        if (
            authenticationInProgress
        ) {
            return
        }

        authenticationInProgress =
            true

        lifecycleScope.launch {

            val result =
                AuthManager.signInWithPhoneCredential(
                    credential
                )

            result.fold(
                onSuccess = {
                    onAuthenticationSuccess(
                        it
                    )
                },
                onFailure = {
                    authenticationInProgress =
                        false

                    onAuthenticationFailure(
                        it
                    )
                }
            )
        }
    }

    private fun beginGoogleSignIn() {

        setAuthenticationLoading(
            true
        )

        statusText.text =
            "Opening secure Google sign-in…"

        orb.setState(
            OrbState.THINKING
        )

        lifecycleScope.launch {

            val result =
                AuthManager.signInWithGoogle(
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

        authenticationInProgress =
            false

        setAuthenticationLoading(
            false
        )

        orb.setState(
            OrbState.LISTENING
        )

        statusText.text =
            "Welcome ${user.displayName ?: user.email ?: "back"}."

        openPostAuthDestination()
    }

    private fun onAuthenticationFailure(
        error: Throwable
    ) {

        authenticationInProgress =
            false

        setAuthenticationLoading(
            false
        )

        orb.setState(
            OrbState.ERROR
        )

        statusText.text =
            error.message
                ?: "Authentication was not completed."

        Toast.makeText(
            this,
            error.message
                ?: "Authentication failed.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setAuthenticationLoading(
        loading: Boolean
    ) {

        sendOtpButton.isEnabled =
            !loading

        verifyOtpButton.isEnabled =
            !loading

        googleSignInButton.isEnabled =
            !loading

        phoneInput.isEnabled =
            !loading

        progressBar.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun showOtpStage() {

        otpSection.visibility =
            View.VISIBLE

        phoneInput.isEnabled =
            false

        sendOtpButton.visibility =
            View.GONE

        googleSignInButton.visibility =
            View.GONE

        otpInput.text?.clear()

        otpInput.requestFocus()
    }

    private fun startResendTimer() {

        resendTimer?.cancel()

        resendOtpButton.isEnabled =
            false

        resendOtpButton.text =
            "Resend code in 60s"

        resendTimer =
            object :
                CountDownTimer(
                    60_000L,
                    1_000L
                ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    resendOtpButton.text =
                        "Resend code in ${millisUntilFinished / 1000}s"
                }

                override fun onFinish() {

                    resendOtpButton.isEnabled =
                        true

                    resendOtpButton.text =
                        "Resend code"
                }
            }.start()
    }

    private fun normalizePhoneNumber(
        input: String
    ): String? {

        val normalized =
            input
                .trim()
                .replace(
                    Regex("[\\s()-]"),
                    ""
                )

        return when {

            normalized.matches(
                Regex("\\d{10}")
            ) -> {
                "+91$normalized"
            }

            normalized.matches(
                Regex("\\+\\d{10,15}")
            ) -> {
                normalized
            }

            normalized.startsWith(
                "0091"
            ) &&
                normalized.length == 14 -> {
                "+${normalized.drop(2)}"
            }

            else -> {
                null
            }
        }
    }

    private fun friendlyPhoneError(
        error: FirebaseException
    ): String {

        val message =
            error.message
                ?: "Phone verification failed."

        return when {

            message.contains(
                "quota",
                ignoreCase = true
            ) ->
                "SMS verification limit reached. Please try again later."

            message.contains(
                "invalid",
                ignoreCase = true
            ) ->
                "That phone number is not valid."

            message.contains(
                "too-many",
                ignoreCase = true
            ) ->
                "Too many attempts. Please wait before trying again."

            else ->
                message
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
            android.content.Intent(
                this,
                destination
            ).apply {
                flags =
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putString(
            STATE_VERIFICATION_ID,
            verificationId
        )

        outState.putString(
            STATE_PHONE_NUMBER,
            normalizedPhoneNumber
        )

        super.onSaveInstanceState(
            outState
        )
    }

    override fun onDestroy() {

        resendTimer?.cancel()

        super.onDestroy()
    }

    companion object {

        private const val STATE_VERIFICATION_ID =
            "phone_verification_id"

        private const val STATE_PHONE_NUMBER =
            "phone_number"
    }
}
