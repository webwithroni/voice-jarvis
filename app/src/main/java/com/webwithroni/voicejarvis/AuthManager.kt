package com.webwithroni.voicejarvis

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Central authentication gateway for Voice Jarvis.
 *
 * Responsibilities:
 *
 * - Firebase authentication state
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

    /**
     * Sign in using Firebase Email + Password.
     *
     * JARVIS intentionally exposes no account-creation flow.
     * Accounts are provisioned outside the application.
     */
    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<FirebaseUser> {

        return try {

            val normalizedEmail =
                email.trim()

            if (
                normalizedEmail.isBlank()
            ) {
                throw IllegalArgumentException(
                    "Email is required."
                )
            }

            if (
                password.isBlank()
            ) {
                throw IllegalArgumentException(
                    "Password is required."
                )
            }

            val signedIn =
                auth
                    .signInWithEmailAndPassword(
                        normalizedEmail,
                        password
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
     * Send Firebase password-reset email.
     */
    suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit> {

        return try {

            val normalizedEmail =
                email.trim()

            if (
                normalizedEmail.isBlank()
            ) {
                throw IllegalArgumentException(
                    "Email is required."
                )
            }

            auth
                .sendPasswordResetEmail(
                    normalizedEmail
                )
                .await()

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

    /**
     * Sign out from Firebase.
     */
    suspend fun signOut(
        context: Context
    ): Result<Unit> {

        return try {

            auth.signOut()

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
