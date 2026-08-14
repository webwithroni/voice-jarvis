package com.webwithroni.voicejarvis.orb

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Public Android View for the Jarvis Orb.
 *
 * This is the ONLY Orb class MainActivity should talk to.
 *
 * Public API:
 *
 * - setState()
 * - setActivity()
 * - setMicAmplitude()
 * - setPlaybackAmplitude()
 * - setReducedMotion()
 * - reset()
 *
 * Internal rendering remains delegated to OrbRenderer.
 */
class HumanoidOrbView @JvmOverloads constructor(
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

    private var lastWidth =
        0

    private var lastHeight =
        0

    init {

        setLayerType(
            View.LAYER_TYPE_HARDWARE,
            null
        )

        isFocusable =
            false

        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_NO

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

    /**
     * Change the primary Orb state.
     */
    fun setState(
        value: OrbState
    ) {

        if (
            state ==
            value
        ) {
            return
        }

        state =
            value

        /*
         * Never carry microphone energy into SPEAKING,
         * or playback energy into HEARING.
         */
        micReactive.reset()
        playbackReactive.reset()

        audioAmplitude =
            0f

        renderer.setState(
            value
        )

        renderer.setAudioAmplitude(
            0f
        )

        invalidate()
    }

    /**
     * Change contextual Orb activity.
     */
    fun setActivity(
        value: OrbActivity
    ) {

        if (
            activity ==
            value
        ) {
            return
        }

        activity =
            value

        renderer.setActivity(
            value
        )

        invalidate()
    }

    /**
     * Supply raw microphone RMS for HEARING.
     *
     * OrbAudioReactive performs:
     *
     * noise gate
     * normalization
     * smoothing
     */
    fun setMicAmplitude(
        value: Float
    ) {

        if (
            state !=
                OrbState.HEARING
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

    /**
     * Supply raw playback RMS for SPEAKING.
     *
     * This path is intentionally independent from the
     * microphone reactive processor.
     */
    fun setPlaybackAmplitude(
        value: Float
    ) {

        if (
            state !=
                OrbState.SPEAKING
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

    /**
     * Enable or disable reduced motion.
     */
    fun setReducedMotion(
        enabled: Boolean
    ) {

        if (
            reducedMotion ==
            enabled
        ) {
            return
        }

        reducedMotion =
            enabled

        renderer.setReducedMotion(
            enabled
        )

        invalidate()
    }

    /**
     * Reset Orb simulation while preserving the currently
     * selected state/activity/audio configuration.
     */
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

        renderer.setReducedMotion(
            reducedMotion
        )

        invalidate()
    }

    override fun onAttachedToWindow() {

        super.onAttachedToWindow()

        attached =
            true

        lastWidth =
            width

        lastHeight =
            height

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
    }

    override fun onDetachedFromWindow() {

        attached =
            false

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

        lastWidth =
            width

        lastHeight =
            height

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
            canvas =
                canvas,
            width =
                safeWidth.toFloat(),
            height =
                safeHeight.toFloat()
        )

        /*
         * Continuous render loop.
         *
         * The renderer internally limits frame delta,
         * making this tolerant of occasional frame drops.
         */
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
            visibility ==
                VISIBLE
        ) {

            postInvalidateOnAnimation()
        }
    }

    /**
     * Expose current base state for diagnostics/tests.
     */
    fun currentState():
        OrbState =
        state

    /**
     * Expose current activity for diagnostics/tests.
     */
    fun currentActivity():
        OrbActivity =
        activity
}
