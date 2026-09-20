package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Outer speed ring plus inner RPM ring in one dial. */
class PerformanceSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val outerRect = RectF()
    private val innerRect = RectF()
    private var outerWidth = 10f
    private var innerWidth = 8f
    private val outerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outerActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val innerActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)
    private var rpmPaint: Paint = textPaint(20f, ok, bold = true)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val pad = size * 0.09f
        outerRect.set(pad, pad, w - pad, h - pad)
        val innerPad = size * 0.26f
        innerRect.set(innerPad, innerPad, w - innerPad, h - innerPad)
        outerWidth = size * 0.06f
        innerWidth = size * 0.05f
        outerTrackPaint.color = track
        outerTrackPaint.strokeWidth = outerWidth
        outerActivePaint.color = ink
        outerActivePaint.strokeWidth = outerWidth
        innerTrackPaint.color = track
        innerTrackPaint.strokeWidth = innerWidth
        tipPaint.color = ink
        redlinePaint.color = danger
        redlinePaint.strokeWidth = size * 0.014f
        speedPaint = textPaint(size * 0.17f, ink, bold = true)
        unitPaint = textPaint(size * 0.045f, ink)
        rpmPaint = textPaint(size * 0.045f, zoneColor(), bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val speedFrac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        val rpmFrac = SpeedometerStyle.fraction(animatedRpm.toFloat(), rpmMax.toFloat())
        canvas.drawArc(outerRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, outerTrackPaint)
        val speedSweep = SpeedometerStyle.SWEEP_ANGLE * speedFrac
        if (speedSweep > 1f) {
            canvas.drawArc(outerRect, SpeedometerStyle.START_ANGLE, speedSweep, false, outerActivePaint)
            // Tip dot marks the exact speed position on the outer ring.
            val tipDeg = SpeedometerStyle.START_ANGLE + speedSweep
            val tipRad = Math.toRadians(tipDeg.toDouble())
            val tipRadius = outerRect.width() / 2f
            canvas.drawCircle(
                cx + tipRadius * cos(tipRad).toFloat(),
                cy + tipRadius * sin(tipRad).toFloat(),
                outerWidth * 0.62f,
                tipPaint
            )
        }
        canvas.drawArc(innerRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, innerTrackPaint)
        innerActivePaint.color = zoneColor()
        val rpmSweep = SpeedometerStyle.SWEEP_ANGLE * rpmFrac
        if (rpmSweep > 1f) {
            canvas.drawArc(innerRect, SpeedometerStyle.START_ANGLE, rpmSweep, false, innerActivePaint)
        }
        // Redline tick on the inner ring at the shift point.
        val shiftFrac = SpeedometerStyle.fraction(shiftRpm.toFloat(), rpmMax.toFloat())
        val markDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * shiftFrac
        val markRad = Math.toRadians(markDeg.toDouble())
        val markRadius = innerRect.width() / 2f
        val c = cos(markRad).toFloat()
        val s = sin(markRad).toFloat()
        canvas.drawLine(
            cx + (markRadius - innerWidth) * c, cy + (markRadius - innerWidth) * s,
            cx + (markRadius + innerWidth) * c, cy + (markRadius + innerWidth) * s,
            redlinePaint
        )
        canvas.drawText(animatedSpeed.toInt().toString(), cx, cy + size * 0.02f, speedPaint)
        canvas.drawText(unitLabel, cx, cy + size * 0.10f, unitPaint)
        rpmPaint.color = zoneColor()
        canvas.drawText("${animatedRpm} RPM", cx, cy + size * 0.175f, rpmPaint)
    }
}
