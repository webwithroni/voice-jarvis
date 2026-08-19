package com.webwithroni.voicejarvis

import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webwithroni.voicejarvis.orb.ParticleOrbView
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiInflationSmokeTest {

    private val context =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext

    private fun inflate(
        layoutRes: Int
    ): View {

        return LayoutInflater
            .from(context)
            .inflate(
                layoutRes,
                null,
                false
            )
    }

    @Test
    fun mainLayoutInflatesCompletely() {

        val root =
            inflate(
                R.layout.activity_main
            )

        assertNotNull(
            root.findViewById<JarvisHomeVisualShell>(
                R.id.homeVisualShell
            )
        )

        assertNotNull(
            root.findViewById<ParticleOrbView>(
                R.id.orbView
            )
        )

        assertNotNull(
            root.findViewById<JarvisGlassSurface>(
                R.id.homeHistoryHint
            )
        )

        assertNotNull(
            root.findViewById<JarvisGlassSurface>(
                R.id.homeToolsHint
            )
        )

        assertNotNull(
            root.findViewById<JarvisGlassSurface>(
                R.id.permissionCard
            )
        )
    }

    @Test
    fun authLayoutInflatesCompletely() {

        val root =
            inflate(
                R.layout.activity_auth
            )

        assertNotNull(
            root.findViewById<ParticleOrbView>(
                R.id.authOrb
            )
        )
    }

    @Test
    fun onboardingLayoutInflatesCompletely() {

        val root =
            inflate(
                R.layout.activity_onboarding
            )

        assertNotNull(
            root.findViewById<ParticleOrbView>(
                R.id.onboardingOrb
            )
        )
    }
}
