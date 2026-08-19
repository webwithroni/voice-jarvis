package com.webwithroni.voicejarvis

import android.content.Context
import android.util.AttributeSet
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * JARVIS Glass Surface V2
 *
 * Lightweight reusable cinematic glass primitive.
 *
 * Visual hierarchy:
 *
 *   cinematic background
 *          ↓
 *   atmospheric glow
 *          ↓
 *   translucent glass fill
 *          ↓
 *   fine border
 *          ↓
 *   screen content
 *
 * Deliberately avoids real-time blur and elevation shadows.
 * This keeps rendering predictable on lower-end Android hardware.
 */
class JarvisGlassSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(
    context,
    attrs,
    defStyleAttr
) {

    companion object {

        private const val DEFAULT_FILL_ALPHA = 0.10f
        private const val DEFAULT_BORDER_ALPHA = 0.16f

        private const val ACTIVE_BORDER_ALPHA = 0.42f
        private const val ACTIVE_GLOW_ALPHA = 0.10f

        private const val DEFAULT_RADIUS_DP = 22f
        private const val DEFAULT_BORDER_DP = 1
    }

    private val density =
        resources.displayMetrics.density

    private val glowSurface =
        FrameLayout(context)

    private val baseSurface =
        FrameLayout(context)

    private var fillColor =
        Color.WHITE

    private var fillAlpha =
        DEFAULT_FILL_ALPHA

    private var borderColor =
        Color.WHITE

    private var borderAlpha =
        DEFAULT_BORDER_ALPHA

    private var radiusDp =
        DEFAULT_RADIUS_DP

    private var borderDp =
        DEFAULT_BORDER_DP

    private var activeColor =
        context.getColor(
            R.color.vj_cyan
        )

    private var active =
        false

    init {

        clipChildren =
            false

        clipToPadding =
            false

        isClickable =
            false

        isFocusable =
            false

        addView(
            glowSurface,
            fullParams()
        )

        addView(
            baseSurface,
            fullParams()
        )

        updateAppearance()
    }

    /**
     * Marks this surface as visually active.
     */
    fun setActive(
        enabled: Boolean
    ) {

        if (
            active ==
                enabled
        ) {
            return
        }

        active =
            enabled

        updateAppearance()
    }

    /**
     * Sets the translucent glass fill.
     */
    fun setFill(
        color: Int,
        alpha: Float = fillAlpha
    ) {

        fillColor =
            color

        fillAlpha =
            alpha.coerceIn(
                0f,
                1f
            )

        updateAppearance()
    }

    /**
     * Sets the normal border.
     */
    fun setBorder(
        color: Int,
        alpha: Float = borderAlpha
    ) {

        borderColor =
            color

        borderAlpha =
            alpha.coerceIn(
                0f,
                1f
            )

        updateAppearance()
    }

    /**
     * Sets corner radius in dp.
     */
    fun setRadius(
        radius: Float
    ) {

        radiusDp =
            radius.coerceAtLeast(
                0f
            )

        updateAppearance()
    }

    /**
     * Sets the color used for active illumination.
     */
    fun setActiveColor(
        color: Int
    ) {

        activeColor =
            color

        updateAppearance()
    }

    /**
     * Returns whether the surface is currently active.
     */
    fun isActive(): Boolean =
        active

    private fun updateAppearance() {

        val radius =
            dp(
                radiusDp
            )

        val fill =
            withAlpha(
                fillColor,
                fillAlpha
            )

        val resolvedBorderColor =
            if (
                active
            ) {
                activeColor
            } else {
                borderColor
            }

        val resolvedBorderAlpha =
            if (
                active
            ) {
                ACTIVE_BORDER_ALPHA
            } else {
                borderAlpha
            }

        baseSurface.background =
            GradientDrawable().apply {

                shape =
                    GradientDrawable.RECTANGLE

                setColor(
                    fill
                )

                cornerRadius =
                    radius

                setStroke(
                    dp(
                        borderDp
                    ),
                    withAlpha(
                        resolvedBorderColor,
                        resolvedBorderAlpha
                    )
                )
            }

        val glow =
            if (
                active
            ) {
                withAlpha(
                    activeColor,
                    ACTIVE_GLOW_ALPHA
                )
            } else {
                Color.TRANSPARENT
            }

        glowSurface.background =
            GradientDrawable().apply {

                shape =
                    GradientDrawable.RECTANGLE

                setColor(
                    glow
                )

                cornerRadius =
                    radius
            }
    }

    private fun fullParams():
        LayoutParams {

        return LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    private fun dp(
        value: Float
    ): Float =
        value * density

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                density
            )
            .roundToInt()

    private fun withAlpha(
        color: Int,
        alpha: Float
    ): Int {

        return Color.argb(
            (
                alpha.coerceIn(
                    0f,
                    1f
                ) *
                    255f
            )
                .roundToInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}
