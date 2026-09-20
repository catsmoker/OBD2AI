package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Traditional circular instrument cluster: bezel ring, numeral scale, thin
 * needle with hub, redline segment, digital readout in the bottom gap.
 * Deliberately the only style with a needle — that is its identity.
 */
class ClassicSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val bezelRect = RectF()
    private val activeRect = RectF()
    private val redlineRect = RectF()
    private var size = 0f
    private val cx get() = width / 2f
    private val cy get() = height / 2f

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var numeralPaint: Paint = textPaint(40f, ink)
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var labelPaint: Paint = textPaint(20f, ink)
    private var shiftPaint: Paint = textPaint(20f, danger, bold = true)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size = min(w, h).toFloat()
        val activeWidth = size * 0.055f
        var pad = size * 0.02f
        bezelRect.set(pad, pad, w - pad, h - pad)
        pad = size * 0.055f + activeWidth / 2f
        activeRect.set(pad, pad, w - pad, h - pad)
        pad = size * 0.055f + activeWidth / 2f
        redlineRect.set(pad, pad, w - pad, h - pad)
        bezelPaint.color = ink
        bezelPaint.alpha = 90
        bezelPaint.strokeWidth = size * 0.012f
        activePaint.strokeWidth = activeWidth
        redlinePaint.color = danger
        redlinePaint.strokeWidth = activeWidth * 0.55f
        tickPaint.color = ink
        needlePaint.color = ink
        needlePaint.strokeWidth = size * 0.014f
        hubPaint.color = ink
        numeralPaint = textPaint(size * 0.048f, ink)
        speedPaint = textPaint(size * 0.14f, ink, bold = true)
        labelPaint = textPaint(size * 0.042f, ink)
        shiftPaint = textPaint(size * 0.05f, danger, bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val frac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        // Bezel, then the filled zone arc.
        canvas.drawArc(bezelRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, bezelPaint)
        val sweep = SpeedometerStyle.SWEEP_ANGLE * frac
        if (sweep > 1f) {
            activePaint.color = zoneColor()
            canvas.drawArc(activeRect, SpeedometerStyle.START_ANGLE, sweep, false, activePaint)
        }
        // Static redline over the top 12% — the limit is always visible.
        val redStart = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * 0.88f
        canvas.drawArc(redlineRect, redStart, SpeedometerStyle.SWEEP_ANGLE * 0.12f, false, redlinePaint)
        // Numeral scale with round steps (20s in both km/h and mph).
        val majors = SpeedometerStyle.majorTickCount(maxSpeed)
        val radius = activeRect.width() / 2f
        val inner = radius - size * 0.055f
        for (m in 0..majors) {
            val f = m / majors.toFloat()
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val majorLen = size * 0.04f
            tickPaint.strokeWidth = size * 0.009f
            canvas.drawLine(
                cx + inner * cosA, cy + inner * sinA,
                cx + (inner - majorLen) * cosA, cy + (inner - majorLen) * sinA,
                tickPaint
            )
            val label = (maxSpeed * m / majors).roundToInt().toString()
            canvas.drawText(
                label,
                cx + (inner - majorLen - size * 0.05f) * cosA,
                cy + (inner - majorLen - size * 0.05f) * sinA + size * 0.018f,
                numeralPaint
            )
            if (m < majors) {
                tickPaint.strokeWidth = size * 0.004f
                for (k in 1..3) {
                    val ff = (m + k / 4f) / majors
                    val aDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * ff
                    val r = Math.toRadians(aDeg.toDouble())
                    val c = cos(r).toFloat()
                    val s = sin(r).toFloat()
                    val minorLen = size * 0.02f
                    canvas.drawLine(
                        cx + inner * c, cy + inner * s,
                        cx + (inner - minorLen) * c, cy + (inner - minorLen) * s,
                        tickPaint
                    )
                }
            }
        }
        // Needle + hub.
        val needleDeg = SpeedometerStyle.START_ANGLE + sweep
        val needleRad = Math.toRadians(needleDeg.toDouble())
        val needleLen = inner - size * 0.11f
        canvas.drawLine(
            cx, cy,
            cx + needleLen * cos(needleRad).toFloat(),
            cy + needleLen * sin(needleRad).toFloat(),
            needlePaint
        )
        canvas.drawCircle(cx, cy, size * 0.028f, hubPaint)
        // Digital readout lives in the bottom gap, clear of needle and scale.
        canvas.drawText(animatedSpeed.toInt().toString(), cx, cy + size * 0.30f, speedPaint)
        canvas.drawText(unitLabel, cx, cy + size * 0.36f, labelPaint)
        if (animatedRpm >= shiftRpm) {
            canvas.drawText("SHIFT", cx, cy - size * 0.20f, shiftPaint)
        }
    }
}
