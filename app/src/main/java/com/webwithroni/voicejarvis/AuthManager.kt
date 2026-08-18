package com.webwithroni.voicejarvis

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.AuthResult
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Central authentication gateway for Voice Jarvis.
 *
 * Authentication policy:
 *
 * - Firebase Email + Password
 * - Password reset
 * - Firebase sign-out
 *
 * No phone authentication.
 * No Google authentication.
 * No signup flow.
 * No anonymous sign-in flow.
 */
object AuthManager {

    private const val PREFS_NAME =
        "voice_jarvis_auth"

    private const val PREF_ONBOARDING_COMPLETED =
        "onboarding_completed"

    private var appContext:
        Context? =
        null

    private val auth:
        FirebaseAuth by lazy {
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

    fun isAnonymous():
        Boolean {
        return auth.currentUser
            ?.isAnonymous
            ?: false
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

    /**
     * Firebase Email + Password sign-in.
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

            val user =
                auth
                    .signInWithEmailAndPassword(
                        normalizedEmail,
                        password
                    )
                    .awaitFirebaseUser()

            Result.success(
                user
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error:
            Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Firebase password-reset email.
     *
     * Uses the existing Task bridge instead of relying on the
     * kotlinx-coroutines-play-services await() extension.
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
                .awaitTask()

            Result.success(
                Unit
            )

        } catch (
            cancellation:
            CancellationException
        ) {

            throw cancellation

        } catch (
            error:
            Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Sign out from Firebase.
     *
     * The context parameter remains for compatibility with existing
     * callers; no external credential manager is used.
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
            error:
            Throwable
        ) {

            Result.failure(
                error
            )
        }
    }

    /**
     * Per-user onboarding completion.
     */
    fun hasCompletedOnboarding():
        Boolean {

        val uid =
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
                    uid
                ),
                false
            )
    }

    fun setOnboardingCompleted(
        completed: Boolean
    ) {

        val uid =
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
                    uid
                ),
                completed
            )
            .apply()
    }

    private fun onboardingPreferenceKey(
        uid: String
    ):
        String {

        return "${PREF_ONBOARDING_COMPLETED}_${uid}"
    }

    /**
     * Bootstrap compatibility hook.
     *
     * No account is created here.
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

    /**
     * Convert a Firebase AuthResult Task into a cancellable coroutine
     * result without requiring the Play Services coroutine await()
     * extension.
     */
    private suspend fun Task<AuthResult>.awaitFirebaseUser():
        FirebaseUser =

        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->

                if (
                    !continuation.isActive
                ) {
                    return@addOnSuccessListener
                }

                val user =
                    result.user

                if (
                    user != null
                ) {

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
                 * Firebase Auth Tasks do not consistently expose
                 * cancellation, so callbacks are simply ignored
                 * after coroutine cancellation.
                 */
            }
        }

    /**
     * Convert a Firebase Task<T> into a cancellable coroutine
     * without the kotlinx-coroutines-play-services dependency.
     */
    private suspend fun <T> Task<T>.awaitTask():
        T =

        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->

                if (
                    continuation.isActive
                ) {

                    continuation.resume(
                        result
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
                 * Task cancellation is not required for this bridge.
                 */
            }
        }
}
