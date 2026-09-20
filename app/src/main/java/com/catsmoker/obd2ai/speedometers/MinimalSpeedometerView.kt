package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Thin arc, quiet ticks, calm center readout. */
class MinimalSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val arcRect = RectF()
    private val arcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)
    private var rpmPaint: Paint = textPaint(16f, ok)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val pad = size * 0.12f
        arcRect.set(pad, pad, w - pad, h - pad)
        val widthPx = size * 0.028f
        arcTrackPaint.color = track
        arcTrackPaint.strokeWidth = widthPx
        arcPaint.color = ink
        arcPaint.strokeWidth = widthPx
        tickPaint.color = ink
        tickPaint.alpha = 110
        tickPaint.strokeWidth = size * 0.006f
        speedPaint = textPaint(size * 0.24f, ink, bold = true)
        unitPaint = textPaint(size * 0.055f, ink)
        rpmPaint = textPaint(size * 0.045f, zoneColor())
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val frac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, arcTrackPaint)
        val sweep = SpeedometerStyle.SWEEP_ANGLE * frac
        if (sweep > 1f) {
            canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, sweep, false, arcPaint)
        }
        // Four quiet ticks, no numerals — decoration stays out of the way.
        val radius = arcRect.width() / 2f
        for (m in 0..4) {
            val f = m / 4f
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val len = size * 0.03f
            canvas.drawLine(cx + radius * c, cy + radius * s, cx + (radius - len) * c, cy + (radius - len) * s, tickPaint)
        }
        canvas.drawText(animatedSpeed.toInt().toString(), cx, cy + size * 0.05f, speedPaint)
        canvas.drawText(unitLabel, cx, cy + size * 0.14f, unitPaint)
        rpmPaint.color = zoneColor()
        canvas.drawText("${animatedRpm} RPM", cx, cy + size * 0.21f, rpmPaint)
    }
}
