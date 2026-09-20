package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Neon dual arc with needle for a HUD feel. */
class FuturisticSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val outerRect = RectF()
    private val innerRect = RectF()
    private var outerWidth = 10f
    private var innerWidth = 3f
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
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val pad = size * 0.10f
        outerRect.set(pad, pad, w - pad, h - pad)
        val innerPad = size * 0.22f
        innerRect.set(innerPad, innerPad, w - innerPad, h - innerPad)
        outerWidth = size * 0.05f
        innerWidth = size * 0.012f
        outerTrackPaint.color = track
        outerTrackPaint.strokeWidth = outerWidth
        outerActivePaint.color = neon
        outerActivePaint.strokeWidth = outerWidth
        outerActivePaint.setShadowLayer(outerWidth * 0.45f, 0f, 0f, neon)
        innerTrackPaint.color = track
        innerTrackPaint.strokeWidth = innerWidth
        innerActivePaint.color = neon
        innerActivePaint.strokeWidth = innerWidth
        needlePaint.color = ink
        needlePaint.strokeWidth = size * 0.01f
        hubPaint.color = neon
        speedPaint = textPaint(size * 0.15f, ink, bold = true)
        unitPaint = textPaint(size * 0.045f, ink)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val frac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        canvas.drawArc(outerRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, outerTrackPaint)
        val sweep = SpeedometerStyle.SWEEP_ANGLE * frac
        if (sweep > 1f) {
            canvas.drawArc(outerRect, SpeedometerStyle.START_ANGLE, sweep, false, outerActivePaint)
        }
        // Inner thin echo arc follows for depth.
        canvas.drawArc(innerRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, innerTrackPaint)
        if (sweep > 1f) {
            canvas.drawArc(innerRect, SpeedometerStyle.START_ANGLE, sweep, false, innerActivePaint)
        }
        // Dotted HUD tick ring between the arcs: lit dots track the value.
        val dotRadius = (outerRect.width() / 2f + innerRect.width() / 2f) / 2f
        val dots = 24
        for (i in 0 until dots) {
            val f = i / (dots - 1f)
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            dotPaint.color = if (f <= frac) neon else track
            canvas.drawCircle(
                cx + dotRadius * cos(rad).toFloat(),
                cy + dotRadius * sin(rad).toFloat(),
                size * 0.008f,
                dotPaint
            )
        }
        // Needle.
        val angleDeg = SpeedometerStyle.START_ANGLE + sweep
        val rad = Math.toRadians(angleDeg.toDouble())
        val radius = outerRect.width() / 2f - outerWidth
        canvas.drawLine(
            cx, cy,
            cx + radius * cos(rad).toFloat(), cy + radius * sin(rad).toFloat(),
            needlePaint
        )
        canvas.drawCircle(cx, cy, size * 0.025f, hubPaint)
        canvas.drawText(animatedSpeed.toInt().toString(), cx, cy + size * 0.24f, speedPaint)
        canvas.drawText(unitLabel, cx, cy + size * 0.31f, unitPaint)
    }
}
