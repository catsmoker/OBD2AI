package com.catsmoker.obd2ai.speedometers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.PrefsKeys
import kotlin.math.abs
import kotlin.math.min

/**
 * Shared gauge base: animated speed needle value, snapped RPM, theme colors,
 * square measuring. Subclasses only implement [onDraw] styling — all paints
 * are cached as fields (sized in `onSizeChanged`) so `onDraw` never allocates.
 */
abstract class BaseSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var maxSpeed: Float = 220f
    var rpmMax: Int = 8000
    var unitLabel: String = "km/h"
    var shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM

    protected var animatedSpeed: Float = 0f
    protected var animatedRpm: Int = 0
    private var speedAnimator: ValueAnimator? = null

    protected val ink: Int = ContextCompat.getColor(context, R.color.on_surface)
    protected val ok: Int = ContextCompat.getColor(context, R.color.gauge_ok)
    protected val warn: Int = ContextCompat.getColor(context, R.color.gauge_warn)
    protected val danger: Int = ContextCompat.getColor(context, R.color.gauge_danger)
    protected val sport: Int = ContextCompat.getColor(context, R.color.gauge_sport)
    protected val neon: Int = ContextCompat.getColor(context, R.color.gauge_neon)
    protected val track: Int = ContextCompat.getColor(context, R.color.gauge_track)

    protected fun zoneColor(): Int = when (SpeedometerStyle.rpmZone(animatedRpm, shiftRpm)) {
        0 -> ok
        1 -> warn
        else -> danger
    }

    /** Driving feed: smooth needle, cheap invalidate, no recomposition storm. */
    fun setSpeed(kmh: Float, animate: Boolean = true) {
        val target = SpeedometerStyle.clampSpeed(kmh, maxSpeed)
        if (!animate) {
            speedAnimator?.cancel()
            animatedSpeed = target
            invalidate()
            return
        }
        if (abs(target - animatedSpeed) < 0.5f) {
            animatedSpeed = target
            invalidate()
            return
        }
        speedAnimator?.cancel()
        val from = animatedSpeed
        speedAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animatedSpeed = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun setRpm(rpm: Int, shift: Int = shiftRpm) {
        shiftRpm = shift
        val clamped = SpeedometerStyle.clampRpm(rpm, rpmMax)
        if (clamped != animatedRpm) {
            animatedRpm = clamped
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val fallback = (220 * resources.displayMetrics.density).toInt()
        val size = when {
            wMode == MeasureSpec.EXACTLY && hMode == MeasureSpec.EXACTLY -> min(wSize, hSize)
            wMode == MeasureSpec.EXACTLY -> wSize
            hMode == MeasureSpec.EXACTLY -> hSize
            else -> fallback
        }
        setMeasuredDimension(size, size)
    }

    protected fun textPaint(sizePx: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }

    protected fun arcPaint(color: Int, widthPx: Float, glow: Int? = null): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.color = color
            strokeWidth = widthPx
            if (glow != null) setShadowLayer(widthPx * 0.45f, 0f, 0f, glow)
        }
}
