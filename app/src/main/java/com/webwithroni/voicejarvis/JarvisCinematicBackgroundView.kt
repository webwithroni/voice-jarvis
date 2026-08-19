package com.webwithroni.voicejarvis

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.math.sin

/**
 * JARVIS Visual System V2
 *
 * Dedicated cinematic background layer.
 *
 * IMPORTANT:
 * This view owns ONLY its own children.
 * It never modifies or removes views from the screen
 * that hosts it.
 */
class JarvisCinematicBackgroundView(
    context: Context
) : FrameLayout(context) {

    private val backgroundA =
        createImageLayer()

    private val backgroundB =
        createImageLayer()

    private val atmosphere =
        FrameLayout(context)

    private val vignette =
        FrameLayout(context)

    private var activeLayer =
        backgroundA

    private var inactiveLayer =
        backgroundB

    private var currentId:
        JarvisBackgroundId? = null

    private var transitionAnimator:
        ValueAnimator? = null

    private var motionAnimator:
        ValueAnimator? = null

    private var attached =
        false

    private var reducedMotion =
        false

    init {

        clipChildren =
            false

        clipToPadding =
            false

        setBackgroundColor(
            Color.rgb(
                7,
                9,
                13
            )
        )

        addView(
            backgroundA,
            fullLayoutParams()
        )

        addView(
            backgroundB,
            fullLayoutParams()
        )

        addView(
            atmosphere,
            fullLayoutParams()
        )

        addView(
            vignette,
            fullLayoutParams()
        )

        configureAtmosphere()
        configureVignette()

        backgroundA.alpha =
            0f

        backgroundB.alpha =
            0f

        attached =
            true

        startMotion()
    }

    /**
     * Displays a semantic JARVIS background.
     */
    fun show(
        id: JarvisBackgroundId,
        animate: Boolean = true
    ) {

        if (
            currentId == id
        ) {
            return
        }

        val entry =
            JarvisBackgroundCatalog.find(
                id
            )

        val drawable =
            ContextCompat.getDrawable(
                context,
                entry.drawableRes
            )
                ?: return

        inactiveLayer
            .setImageDrawable(
                drawable
            )

        inactiveLayer.scaleX =
            1.035f

        inactiveLayer.scaleY =
            1.035f

        if (
            !animate ||
            currentId == null ||
            reducedMotion
        ) {

            activeLayer.alpha =
                0f

            inactiveLayer.alpha =
                1f

            swapLayers()

            currentId =
                id

            return
        }

        transitionAnimator?.cancel()

        inactiveLayer.alpha =
            0f

        val oldLayer =
            activeLayer

        val newLayer =
            inactiveLayer

        transitionAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f
            ).apply {

                duration =
                    650L

                addUpdateListener { animation ->

                    val progress =
                        animation.animatedValue
                            as Float

                    newLayer.alpha =
                        progress

                    oldLayer.alpha =
                        1f - progress
                }

                addListener(
                    object :
                        AnimatorListenerAdapter() {

                        override fun onAnimationEnd(
                            animation: Animator
                        ) {

                            if (
                                animation !=
                                    transitionAnimator
                            ) {
                                return
                            }

                            swapLayers()

                            currentId =
                                id

                            transitionAnimator =
                                null
                        }
                    }
                )

                start()
            }
    }

    fun currentBackground():
        JarvisBackgroundId? =
        currentId

    /**
     * Sets overall cinematic darkness.
     *
     * 0f = no additional darkness
     * 1f = maximum darkness
     */
    fun setDarkness(
        amount: Float
    ) {

        val alpha =
            (
                amount
                    .coerceIn(
                        0f,
                        1f
                    ) *
                    255f
            ).toInt()

        val drawable =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(
                        (alpha * 0.72f)
                            .toInt(),
                        0,
                        0,
                        0
                    ),
                    Color.argb(
                        (alpha * 0.38f)
                            .toInt(),
                        0,
                        0,
                        0
                    ),
                    Color.argb(
                        alpha,
                        0,
                        0,
                        0
                    )
                )
            )

        vignette.background =
            drawable
    }

    /**
     * State-specific atmospheric tint.
     */
    fun setAtmosphere(
        color: Int,
        intensity: Float
    ) {

        val alpha =
            (
                intensity
                    .coerceIn(
                        0f,
                        1f
                    ) *
                    75f
            ).toInt()

        atmosphere.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(
                        alpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    ),
                    Color.TRANSPARENT
                )
            )
    }

    fun setReducedMotion(
        enabled: Boolean
    ) {

        reducedMotion =
            enabled

        if (
            enabled
        ) {
            stopMotion()

            backgroundA.scaleX =
                1.025f

            backgroundA.scaleY =
                1.025f

            backgroundB.scaleX =
                1.025f

            backgroundB.scaleY =
                1.025f
        } else {
            startMotion()
        }
    }

    fun pause() {

        transitionAnimator?.pause()
        motionAnimator?.pause()
    }

    fun resume() {

        transitionAnimator?.resume()

        if (
            !reducedMotion
        ) {
            motionAnimator?.resume()
        }
    }

    fun dispose() {

        transitionAnimator?.cancel()
        motionAnimator?.cancel()

        transitionAnimator =
            null

        motionAnimator =
            null

        attached =
            false
    }

    private fun createImageLayer():
        ImageView {

        return ImageView(
            context
        ).apply {

            scaleType =
                ImageView.ScaleType
                    .CENTER_CROP

            alpha =
                0f

            isClickable =
                false

            isFocusable =
                false
        }
    }

    private fun configureAtmosphere() {

        atmosphere.gravity =
            Gravity.CENTER
    }

    private fun configureVignette() {

        setDarkness(
            0.62f
        )
    }

    private fun startMotion() {

        if (
            reducedMotion ||
            motionAnimator != null
        ) {
            return
        }

        motionAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f
            ).apply {

                duration =
                    14_000L

                repeatCount =
                    ValueAnimator.INFINITE

                repeatMode =
                    ValueAnimator.REVERSE

                addUpdateListener { animation ->

                    val p =
                        animation.animatedValue
                            as Float

                    val wave =
                        sin(
                            p *
                                Math.PI *
                                2.0
                        )
                            .toFloat()

                    val scale =
                        1.025f +
                            wave *
                            0.006f

                    val x =
                        wave *
                            2.5f

                    val y =
                        wave *
                            3f

                    activeLayer.scaleX =
                        scale

                    activeLayer.scaleY =
                        scale

                    activeLayer.translationX =
                        x

                    activeLayer.translationY =
                        y

                    inactiveLayer.scaleX =
                        scale

                    inactiveLayer.scaleY =
                        scale

                    inactiveLayer.translationX =
                        x

                    inactiveLayer.translationY =
                        y
                }

                start()
            }
    }

    private fun stopMotion() {

        motionAnimator?.cancel()

        motionAnimator =
            null

        activeLayer.translationX =
            0f

        activeLayer.translationY =
            0f

        inactiveLayer.translationX =
            0f

        inactiveLayer.translationY =
            0f
    }

    private fun swapLayers() {

        val oldActive =
            activeLayer

        activeLayer =
            inactiveLayer

        inactiveLayer =
            oldActive

        activeLayer.alpha =
            1f

        inactiveLayer.alpha =
            0f
    }

    private fun fullLayoutParams():
        LayoutParams {

        return LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }
}
