package com.webwithroni.voicejarvis

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/**
 * JARVIS Home Visual Shell V1
 *
 * Owns the cinematic visual environment behind Home UI.
 *
 * It does not own:
 * - conversation logic
 * - voice logic
 * - navigation
 * - Orb state logic
 */
class JarvisHomeVisualShell(
    context: Context
) : FrameLayout(context) {

    private val cinematicBackground =
        JarvisCinematicBackgroundView(
            context
        )

    init {

        isClickable =
            false

        isFocusable =
            false

        clipChildren =
            false

        clipToPadding =
            false

        addView(
            cinematicBackground,
            LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT
            )
        )

        cinematicBackground.show(
            JarvisBackgroundId.SYSTEM_CORE,
            animate = false
        )

        cinematicBackground.setDarkness(
            0.64f
        )

        setState(
            JarvisState.LISTENING
        )
    }

    fun setState(
        state: JarvisState
    ) {

        when (state) {

            JarvisState.LISTENING -> {

                cinematicBackground.show(
                    JarvisBackgroundId.SYSTEM_CORE
                )

                cinematicBackground.setAtmosphere(
                    ContextCompat.getColor(
                        context,
                        R.color.vj_cyan
                    ),
                    0.32f
                )
            }

            JarvisState.HEARING -> {

                cinematicBackground.show(
                    JarvisBackgroundId.CONNECTION
                )

                cinematicBackground.setAtmosphere(
                    ContextCompat.getColor(
                        context,
                        R.color.vj_cyan_bright
                    ),
                    0.40f
                )
            }

            JarvisState.THINKING -> {

                cinematicBackground.show(
                    JarvisBackgroundId.COGNITIVE_FLOW
                )

                cinematicBackground.setAtmosphere(
                    ContextCompat.getColor(
                        context,
                        R.color.vj_violet
                    ),
                    0.38f
                )
            }

            JarvisState.SPEAKING -> {

                cinematicBackground.show(
                    JarvisBackgroundId.RESPONSE
                )

                cinematicBackground.setAtmosphere(
                    ContextCompat.getColor(
                        context,
                        R.color.vj_blue
                    ),
                    0.34f
                )
            }

            JarvisState.ERROR -> {

                cinematicBackground.show(
                    JarvisBackgroundId.INTENT_FIELD
                )

                cinematicBackground.setAtmosphere(
                    ContextCompat.getColor(
                        context,
                        R.color.vj_state_error
                    ),
                    0.42f
                )
            }

            JarvisState.PAUSED -> {

                cinematicBackground.show(
                    JarvisBackgroundId.ORIGIN
                )

                cinematicBackground.setAtmosphere(
                    Color.GRAY,
                    0.12f
                )
            }
        }
    }

    fun setReducedMotion(
        enabled: Boolean
    ) {

        cinematicBackground.setReducedMotion(
            enabled
        )
    }

    fun pause() {

        cinematicBackground.pause()
    }

    fun resume() {

        cinematicBackground.resume()
    }

    fun dispose() {

        cinematicBackground.dispose()
    }
}
