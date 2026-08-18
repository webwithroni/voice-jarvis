package com.webwithroni.voicejarvis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.webwithroni.voicejarvis.orb.ParticleOrbView
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState

/**
 * Lightweight first-run onboarding.
 *
 * Three focused steps:
 *
 * 1. Meet JARVIS
 * 2. Understand capabilities
 * 3. Enter the workspace
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var orb: ParticleOrbView
    private lateinit var stepText: TextView
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var primary: Button
    private lateinit var hint: TextView
    private lateinit var skip: TextView

    private var step = 0

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        /*
         * Defensive routing:
         * onboarding should never appear without an authenticated
         * Firebase identity.
         */
        if (
            !AuthManager.isSignedIn() ||
            AuthManager.isAnonymous()
        ) {
            openAuth()
            return
        }

        if (
            AuthManager.hasCompletedOnboarding()
        ) {
            openHome()
            return
        }

        setContentView(
            R.layout.activity_onboarding
        )

        bindViews()
        configureOrb()
        renderStep()

        primary.setOnClickListener {
            advance()
        }

        skip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun bindViews() {

        orb =
            findViewById(
                R.id.onboardingOrb
            )

        stepText =
            findViewById(
                R.id.onboardingStep
            )

        eyebrow =
            findViewById(
                R.id.onboardingEyebrow
            )

        title =
            findViewById(
                R.id.onboardingTitle
            )

        body =
            findViewById(
                R.id.onboardingBody
            )

        primary =
            findViewById(
                R.id.onboardingPrimary
            )

        hint =
            findViewById(
                R.id.onboardingHint
            )

        skip =
            findViewById(
                R.id.onboardingSkip
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
            "JARVIS onboarding neural orb"
        )
    }

    private fun renderStep() {

        when (step) {

            0 -> {
                stepText.text =
                    "01 / 03"

                eyebrow.text =
                    "MEET JARVIS"

                title.text =
                    "A voice system built around you."

                body.text =
                    "Talk naturally, get useful answers, and let JARVIS help with the things you actually do."

                primary.text =
                    "Continue"

                hint.text =
                    "Your setup takes less than a minute."

                orb.setState(
                    OrbState.LISTENING
                )
            }

            1 -> {
                stepText.text =
                    "02 / 03"

                eyebrow.text =
                    "CAPABILITIES"

                title.text =
                    "Voice, memory and device actions."

                body.text =
                    "JARVIS can answer questions, work with tools, manage supported phone actions, and keep your experience connected."

                primary.text =
                    "Continue"

                hint.text =
                    "Sensitive actions can require your confirmation."

                orb.setState(
                    OrbState.THINKING
                )
            }

            2 -> {
                stepText.text =
                    "03 / 03"

                eyebrow.text =
                    "READY"

                title.text =
                    "Let's build your workspace."

                body.text =
                    "Everything is ready. Enable permissions when JARVIS needs them, then start talking."

                primary.text =
                    "Enter JARVIS"

                hint.text =
                    "You can revisit settings anytime."

                orb.setState(
                    OrbState.SPEAKING
                )
            }
        }
    }

    private fun advance() {

        if (step < 2) {
            step += 1
            renderStep()
        } else {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {

        AuthManager.setOnboardingCompleted(
            true
        )

        openHome()
    }

    private fun openHome() {

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }

    private fun openAuth() {

        startActivity(
            Intent(
                this,
                AuthActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }
}
