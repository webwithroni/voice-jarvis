package com.webwithroni.voicejarvis.orb

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Public Android View for the Voice Jarvis particle orb.
 *
 * Visual identity:
 * - floating particles
 * - abstract energy field
 * - no abstract energy field
 * - no eyes
 * - no mouth
 * - no avatar
 *
 * The View is intentionally thin.
 * All visual simulation remains inside OrbRenderer.
 */
class ParticleOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    private val renderer =
        OrbRenderer()

    private val micReactive =
        OrbAudioReactive()

    private val playbackReactive =
        OrbAudioReactive()

    private var state =
        OrbState.LISTENING

    private var activity =
        OrbActivity.NONE

    private var audioAmplitude =
        0f

    private var reducedMotion =
        false

    private var attached =
        false

    init {

        setLayerType(
            View.LAYER_TYPE_HARDWARE,
            null
        )

        isFocusable = false

        contentDescription =
            "Voice Jarvis particle orb"

        renderer.setState(
            state
        )

        renderer.setActivity(
            activity
        )

        renderer.setReducedMotion(
            reducedMotion
        )
    }

    fun setState(
        value: OrbState
    ) {

        if (
            state == value
        ) {
            return
        }

        state = value

        /*
         * Do not leak microphone energy into speaking,
         * or speaker energy into hearing.
         */
        micReactive.reset()
        playbackReactive.reset()

        audioAmplitude = 0f

        renderer.setState(
            value
        )

        renderer.setAudioAmplitude(
            0f
        )

        invalidate()
    }

    fun setActivity(
        value: OrbActivity
    ) {

        if (
            activity == value
        ) {
            return
        }

        activity = value

        renderer.setActivity(
            value
        )

        invalidate()
    }

    fun setMicAmplitude(
        value: Float
    ) {

        if (
            state != OrbState.HEARING
        ) {

            micReactive.reset()
            return
        }

        val filtered =
            micReactive.update(
                value
            )

        audioAmplitude =
            filtered

        renderer.setAudioAmplitude(
            filtered
        )

        invalidate()
    }

    fun setPlaybackAmplitude(
        value: Float
    ) {

        if (
            state != OrbState.SPEAKING
        ) {

            playbackReactive.reset()
            return
        }

        val filtered =
            playbackReactive.update(
                value
            )

        audioAmplitude =
            filtered

        renderer.setAudioAmplitude(
            filtered
        )

        invalidate()
    }

    fun setReducedMotion(
        enabled: Boolean
    ) {

        if (
            reducedMotion == enabled
        ) {
            return
        }

        reducedMotion = enabled

        renderer.setReducedMotion(
            enabled
        )

        invalidate()
    }

    fun reset() {

        micReactive.reset()
        playbackReactive.reset()

        renderer.reset()

        renderer.setState(
            state
        )

        renderer.setActivity(
            activity
        )

        renderer.setAudioAmplitude(
            audioAmplitude
        )

        renderer.setReducedMotion(
            reducedMotion
        )

        invalidate()
    }

    override fun onAttachedToWindow() {

        super.onAttachedToWindow()

        attached = true

        renderer.reset()

        renderer.setState(
            state
        )

        renderer.setActivity(
            activity
        )

        renderer.setAudioAmplitude(
            audioAmplitude
        )

        renderer.setReducedMotion(
            reducedMotion
        )

        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {

        attached = false

        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {

        super.onSizeChanged(
            width,
            height,
            oldWidth,
            oldHeight
        )

        renderer.reset()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(
            canvas
        )

        val safeWidth =
            max(
                width,
                0
            )

        val safeHeight =
            max(
                height,
                0
            )

        if (
            safeWidth <= 0 ||
            safeHeight <= 0
        ) {
            return
        }

        renderer.draw(
            canvas = canvas,
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat()
        )

        if (attached) {
            postInvalidateOnAnimation()
        }
    }

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int
    ) {

        super.onVisibilityChanged(
            changedView,
            visibility
        )

        if (
            visibility == VISIBLE
        ) {
            postInvalidateOnAnimation()
        }
    }

    fun currentState(): OrbState =
        state

    fun currentActivity(): OrbActivity =
        activity
}
