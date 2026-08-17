package com.webwithroni.voicejarvis

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Central authentication gateway for Voice Jarvis.
 *
 * Responsibilities:
 *
 * - Firebase authentication state
 * - Google / Credential Manager sign-in
 * - Anonymous -> Google account linking
 * - Sign-out
 *
 * FirebaseManager remains responsible for:
 *
 * - Firestore
 * - conversations
 * - telemetry
 * - learning data
 */
object AuthManager {

    private const val GOOGLE_ID_TOKEN_TYPE =
        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

    private const val PREFS_NAME =
        "voice_jarvis_auth"

    private const val PREF_ONBOARDING_COMPLETED =
        "onboarding_completed"

    private var appContext:
        Context? =
        null

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private fun credentialManager(
        context: Context
    ): CredentialManager {
        return CredentialManager.create(
            context
        )
    }

    fun currentUser():
        FirebaseUser? {
        return auth.currentUser
    }

    fun isSignedIn():
        Boolean {
        return auth.currentUser != null
    }

    fun userId():
        String? {
        return auth.currentUser?.uid
    }

    fun displayName():
        String? {
        return auth.currentUser?.displayName
    }

    fun email():
        String? {
        return auth.currentUser?.email
    }

    fun photoUrl():
        String? {
        return auth.currentUser
            ?.photoUrl
            ?.toString()
    }

    fun isAnonymous():
        Boolean {
        return auth.currentUser
            ?.isAnonymous
            ?: false
    }

    interface PhoneVerificationCallbacks {

        fun onVerificationCompleted(
            credential: PhoneAuthCredential
        )

        fun onCodeSent(
            verificationId: String,
            resendToken: PhoneAuthProvider.ForceResendingToken
        )

        fun onVerificationFailed(
            error: FirebaseException
        )

        fun onCodeAutoRetrievalTimeOut(
            verificationId: String
        ) {
        }
    }

    /**
     * Start Firebase Phone OTP verification.
     *
     * Firebase owns the SMS verification and app-verification flow.
     * The Android Activity receives verification callbacks through the
     * callback interface above.
     */
    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneVerificationCallbacks
    ) {

        startPhoneVerificationInternal(
            activity = activity,
            phoneNumber = phoneNumber,
            callbacks = callbacks,
            forceResendingToken = null
        )
    }

    /**
     * Resend the OTP using Firebase's resending token.
     */
    fun resendPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken,
        callbacks: PhoneVerificationCallbacks
    ) {

        startPhoneVerificationInternal(
            activity = activity,
            phoneNumber = phoneNumber,
            callbacks = callbacks,
            forceResendingToken = resendToken
        )
    }

    private fun startPhoneVerificationInternal(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneVerificationCallbacks,
        forceResendingToken:
            PhoneAuthProvider.ForceResendingToken?
    ) {

        val authCallbacks =
            object :
                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(
                    credential: PhoneAuthCredential
                ) {
                    callbacks.onVerificationCompleted(
                        credential
                    )
                }

                override fun onVerificationFailed(
                    error: FirebaseException
                ) {
                    callbacks.onVerificationFailed(
                        error
                    )
                }

                override fun onCodeSent(
                    verificationId: String,
                    token:
                        PhoneAuthProvider.ForceResendingToken
                ) {
                    callbacks.onCodeSent(
                        verificationId,
                        token
                    )
                }

                override fun onCodeAutoRetrievalTimeOut(
                    verificationId: String
                ) {
                    callbacks.onCodeAutoRetrievalTimeOut(
                        verificationId
                    )
                }
            }

        try {

            val builder =
                PhoneAuthOptions
                    .newBuilder(
                        auth
                    )
                    .setPhoneNumber(
                        phoneNumber
                    )
                    .setTimeout(
                        60L,
                        TimeUnit.SECONDS
                    )
                    .setActivity(
                        activity
                    )
                    .setCallbacks(
                        authCallbacks
                    )

            forceResendingToken?.let {
                builder.setForceResendingToken(
                    it
                )
            }

            PhoneAuthProvider.verifyPhoneNumber(
                builder.build()
            )

        } catch (
            error: Throwable
        ) {

            if (
                error is FirebaseException
            ) {
                callbacks.onVerificationFailed(
                    error
                )
            } else {
                callbacks.onVerificationFailed(
                    FirebaseException(
                        error.message
                            ?: "Unable to start phone verification."
                    )
                )
            }
        }
    }

    /**
     * Verify a manually entered Firebase SMS code.
     */
    suspend fun verifyPhoneCode(
        verificationId: String,
        verificationCode: String
    ): Result<FirebaseUser> {

        return try {

            if (
                verificationId.isBlank()
            ) {
                throw IllegalStateException(
                    "Phone verification session is missing."
                )
            }

            if (
                !verificationCode.matches(
                    Regex("\\d{6}")
                )
            ) {
                throw IllegalArgumentException(
                    "Enter the 6-digit verification code."
                )
            }

            val credential =
                PhoneAuthProvider.getCredential(
                    verificationId,
                    verificationCode
                )

            signInWithPhoneCredential(
                credential
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error: Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Complete Firebase authentication from an already-created
     * PhoneAuthCredential.
     *
     * Anonymous identities are linked when present so an existing UID
     * is preserved instead of creating an unnecessary second identity.
     */
    suspend fun signInWithPhoneCredential(
        credential: PhoneAuthCredential
    ): Result<FirebaseUser> {

        return try {

            val existingUser =
                auth.currentUser

            if (
                existingUser != null &&
                existingUser.isAnonymous
            ) {

                try {

                    val linked =
                        existingUser
                            .linkWithCredential(
                                credential
                            )
                            .awaitFirebaseUser()

                    return Result.success(
                        linked
                    )

                } catch (
                    collision:
                    com.google.firebase.auth.FirebaseAuthUserCollisionException
                ) {

                    val signedIn =
                        auth
                            .signInWithCredential(
                                credential
                            )
                            .awaitFirebaseUser()

                    return Result.success(
                        signedIn
                    )
                }
            }

            val signedIn =
                auth
                    .signInWithCredential(
                        credential
                    )
                    .awaitFirebaseUser()

            Result.success(
                signedIn
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error: Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Start Google authentication through Android Credential Manager.
     *
     * The first request prefers accounts already authorized for the app.
     * If none are available, the request is retried allowing other
     * Google accounts on the device.
     *
     * When the current Firebase user is anonymous, the Google credential
     * is linked to preserve the existing Firebase UID whenever possible.
     */
    suspend fun signInWithGoogle(
        activity: Activity
    ): Result<FirebaseUser> {

        return try {

            /*
             * This screen has an explicit "Continue with Google" button.
             *
             * Use Google's dedicated Sign-in-with-Google Credential Manager
             * flow directly. This flow is specifically intended for cases
             * where an existing Google account requires reauthentication.
             */
            val idToken =
                requestExplicitGoogleIdToken(
                    activity
                )

            val googleCredential =
                GoogleAuthProvider.getCredential(
                    idToken,
                    null
                )

            val existingUser =
                auth.currentUser

            if (
                existingUser != null &&
                existingUser.isAnonymous
            ) {

                try {

                    val linked =
                        existingUser
                            .linkWithCredential(
                                googleCredential
                            )
                            .awaitFirebaseUser()

                    return Result.success(
                        linked
                    )

                } catch (
                    collision:
                    com.google.firebase.auth.FirebaseAuthUserCollisionException
                ) {

                    /*
                     * The Google account already has a Firebase identity.
                     *
                     * Sign in to that existing Firebase identity instead
                     * of creating a second account.
                     */
                    val signedIn =
                        auth
                            .signInWithCredential(
                                googleCredential
                            )
                            .awaitFirebaseUser()

                    return Result.success(
                        signedIn
                    )
                }
            }

            val signedIn =
                auth
                    .signInWithCredential(
                        googleCredential
                    )
                    .awaitFirebaseUser()

            Result.success(
                signedIn
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error: Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Sign out from Firebase and clear the Credential Manager state so
     * a later sign-in can deliberately choose an account again.
     */
    suspend fun signOut(
        context: Context
    ): Result<Unit> {

        return try {

            auth.signOut()

            credentialManager(
                context
            ).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest()
            )

            Result.success(
                Unit
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error: Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    fun hasCompletedOnboarding():
        Boolean {

        val userId =
            userId()
                ?: return false

        val context =
            appContext
                ?: return false

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                onboardingPreferenceKey(
                    userId
                ),
                false
            )
    }

    fun setOnboardingCompleted(
        completed: Boolean
    ) {

        val userId =
            userId()
                ?: return

        val context =
            appContext
                ?: return

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                onboardingPreferenceKey(
                    userId
                ),
                completed
            )
            .apply()
    }

    private fun onboardingPreferenceKey(
        userId: String
    ): String {

        return "${PREF_ONBOARDING_COMPLETED}_${userId}"
    }

    /**
     * Compatibility bootstrap hook.
     *
     * No authentication is created here.
     */
    fun initialize(
        context: Context? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {

        context?.let {
            appContext =
                it.applicationContext
        }

        onComplete?.invoke(
            isSignedIn()
        )
    }

    private suspend fun Task<com.google.firebase.auth.AuthResult>.awaitFirebaseUser():
        FirebaseUser =
        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->
                if (!continuation.isActive) {
                    return@addOnSuccessListener
                }

                val user =
                    result.user

                if (user != null) {
                    continuation.resume(
                        user
                    )
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Firebase returned no authenticated user."
                        )
                    )
                }
            }

            addOnFailureListener { error ->
                if (
                    continuation.isActive
                ) {
                    continuation.resumeWithException(
                        error
                    )
                }
            }

            continuation.invokeOnCancellation {
                /*
                 * Firebase Task cancellation is not universally
                 * supported across all auth operations, so the
                 * bridge simply stops delivering callbacks.
                 */
            }
        }

    /**
     * Explicit Sign-in-with-Google button flow.
     *
     * This is the final fallback after the normal Credential Manager
     * account-selection flows report that no usable credential exists.
     */
    private suspend fun requestExplicitGoogleIdToken(
        activity: Activity
    ): String {

        val nonce =
            createNonce()

        val signInOption =
            GetSignInWithGoogleOption
                .Builder(
                    activity.getString(
                        R.string.default_web_client_id
                    )
                )
                .setNonce(
                    nonce
                )
                .build()

        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(
                    signInOption
                )
                .build()

        val result =
            credentialManager(
                activity
            ).getCredential(
                activity,
                request
            )

        val credential =
            result.credential

        if (
            credential !is CustomCredential ||
            credential.type !=
                GOOGLE_ID_TOKEN_TYPE
        ) {

            throw IllegalStateException(
                "Google Sign-in credential was not returned."
            )
        }

        return try {

            GoogleIdTokenCredential
                .createFrom(
                    credential.data
                )
                .idToken

        } catch (
            error:
            GoogleIdTokenParsingException
        ) {

            throw IllegalStateException(
                "Unable to parse Google Sign-in ID token.",
                error
            )
        }
    }

    private suspend fun requestGoogleIdToken(
        activity: Activity,
        filterByAuthorizedAccounts: Boolean
    ): String {

        val nonce =
            createNonce()

        val googleIdOption =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(
                    activity.getString(
                        R.string.default_web_client_id
                    )
                )
                .setFilterByAuthorizedAccounts(
                    filterByAuthorizedAccounts
                )
                .setAutoSelectEnabled(
                    false
                )
                .setNonce(
                    nonce
                )
                .build()

        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(
                    googleIdOption
                )
                .build()

        val result =
            credentialManager(
                activity
            ).getCredential(
                activity,
                request
            )

        val credential =
            result.credential

        if (
            credential !is CustomCredential ||
            credential.type !=
                GOOGLE_ID_TOKEN_TYPE
        ) {

            throw IllegalStateException(
                "Google ID token credential was not returned."
            )
        }

        return try {

            GoogleIdTokenCredential
                .createFrom(
                    credential.data
                )
                .idToken

        } catch (
            error:
            GoogleIdTokenParsingException
        ) {

            throw IllegalStateException(
                "Unable to parse Google ID token.",
                error
            )
        }
    }

    /**
     * Generate a cryptographically random nonce and return a SHA-256 hash.
     *
     * The raw nonce never leaves this process.
     */
    private fun createNonce():
        String {

        val random =
            ByteArray(32)

        SecureRandom()
            .nextBytes(
                random
            )

        val raw =
            Base64.encodeToString(
                random,
                Base64.NO_WRAP or
                    Base64.URL_SAFE
            )

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    raw.toByteArray(
                        Charsets.UTF_8
                    )
                )

        return Base64.encodeToString(
            digest,
            Base64.NO_WRAP or
                Base64.URL_SAFE
        )
    }
}
