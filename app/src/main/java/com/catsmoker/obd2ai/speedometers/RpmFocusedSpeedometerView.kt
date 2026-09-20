package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Big tachometer with a compact speed readout underneath. */
class RpmFocusedSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val arcRect = RectF()
    private var arcWidth = 10f
    private val arcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var rpmPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)
    private var speedPaint: Paint = textPaint(24f, ink, bold = true)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val pad = size * 0.10f
        arcRect.set(pad, pad, w - pad, h - pad)
        arcWidth = size * 0.07f
        arcTrackPaint.color = track
        arcTrackPaint.strokeWidth = arcWidth
        tickPaint.color = ink
        markPaint.color = danger
        markPaint.strokeWidth = size * 0.014f
        rpmPaint = textPaint(size * 0.17f, ink, bold = true)
        unitPaint = textPaint(size * 0.045f, ink)
        speedPaint = textPaint(size * 0.06f, ink, bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val rpmFrac = SpeedometerStyle.fraction(animatedRpm.toFloat(), rpmMax.toFloat())
        canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, arcTrackPaint)
        arcActivePaint.color = zoneColor()
        val sweep = SpeedometerStyle.SWEEP_ANGLE * rpmFrac
        if (sweep > 1f) {
            canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, sweep, false, arcActivePaint)
        }
        // Tachometer scale: long ticks every 1000, short ticks every 500.
        val radius = arcRect.width() / 2f
        val inner = radius - arcWidth - size * 0.01f
        for (k in 0..16) {
            val f = k / 16f
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val major = k % 2 == 0
            val len = if (major) size * 0.035f else size * 0.018f
            tickPaint.strokeWidth = if (major) size * 0.008f else size * 0.004f
            canvas.drawLine(
                cx + inner * c, cy + inner * s,
                cx + (inner - len) * c, cy + (inner - len) * s,
                tickPaint
            )
        }
        // Redline marker at the shift point.
        val shiftFrac = SpeedometerStyle.fraction(shiftRpm.toFloat(), rpmMax.toFloat())
        val markAngle = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * shiftFrac
        val rad = Math.toRadians(markAngle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        canvas.drawLine(
            cx + (radius - arcWidth) * c, cy + (radius - arcWidth) * s,
            cx + (radius + arcWidth) * c, cy + (radius + arcWidth) * s,
            markPaint
        )
        canvas.drawText((animatedRpm / 100).toString(), cx, cy + size * 0.02f, rpmPaint)
        canvas.drawText("x100 RPM", cx, cy + size * 0.10f, unitPaint)
        canvas.drawText(
            "${animatedSpeed.toInt()} $unitLabel",
            cx, cy + size * 0.20f,
            speedPaint
        )
    }
}
