package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Segmented dotted sport arc with a glowing active zone. */
class SportSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val oval = RectF()
    private val onPath = Path()
    private val offPath = Path()
    private val shiftBarRect = RectF()
    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shiftBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var legendPaint: Paint = textPaint(14f, ink)
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)
    private var rpmPaint: Paint = textPaint(16f, ok)
    private var shiftPaint: Paint = textPaint(20f, danger, bold = true)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val radius = size * 0.30f
        val cx = w / 2f
        val cy = h / 2f + size * 0.10f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val stroke = size * 0.055f
        onPaint.style = Paint.Style.STROKE
        onPaint.strokeWidth = stroke
        onPaint.color = sport
        onPaint.setShadowLayer(stroke * 0.5f, 0f, 0f, sport)
        offPaint.style = Paint.Style.STROKE
        offPaint.strokeWidth = stroke
        offPaint.color = track
        legendPaint = textPaint(size * 0.042f, ink)
        speedPaint = textPaint(size * 0.19f, ink, bold = true)
        unitPaint = textPaint(size * 0.05f, ink)
        rpmPaint = textPaint(size * 0.048f, zoneColor())
        shiftPaint = textPaint(size * 0.055f, danger, bold = true)
        val barW = size * 0.30f
        val barH = size * 0.022f
        shiftBarRect.set(cx - barW / 2f, cy - radius - size * 0.10f, cx + barW / 2f, cy - radius - size * 0.10f + barH)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f + size * 0.10f
        offPath.reset()
        var a = SpeedometerStyle.SPORT_START_ANGLE
        while (a < SpeedometerStyle.SPORT_START_ANGLE + SpeedometerStyle.SPORT_SWEEP_ANGLE) {
            offPath.addArc(oval, a, 2f)
            a += 4f
        }
        canvas.drawPath(offPath, offPaint)
        val frac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        val litEnd = SpeedometerStyle.SPORT_START_ANGLE + SpeedometerStyle.SPORT_SWEEP_ANGLE * frac
        onPath.reset()
        a = SpeedometerStyle.SPORT_START_ANGLE
        while (a < litEnd) {
            onPath.addArc(oval, a, 2f)
            a += 4f
        }
        canvas.drawPath(onPath, onPaint)
        // Shift light bar above the arc — the RPM relationship at a glance.
        val shifting = animatedRpm >= shiftRpm
        shiftBarPaint.color = if (shifting) danger else track
        val barH = shiftBarRect.height()
        canvas.drawRoundRect(shiftBarRect, barH / 2f, barH / 2f, shiftBarPaint)
        // Straight numerals around the arc, values rounded to the nearest 10
        // so both unit systems read cleanly (140 mph -> 0,20,...,140).
        // (A curved drawTextOnPath legend needs the reference's rotate trick
        // to land on the arc half; plain labels are unambiguous instead.)
        val radius = oval.width() / 2f
        val labelRadius = radius + size * 0.085f
        for (i in 0..7) {
            val f = i / 7f
            val angleDeg = SpeedometerStyle.SPORT_START_ANGLE + SpeedometerStyle.SPORT_SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val value = ((maxSpeed * f / 10f).roundToInt() * 10).toString()
            canvas.drawText(
                value,
                cx + labelRadius * cos(rad).toFloat(),
                cy + labelRadius * sin(rad).toFloat() + size * 0.015f,
                legendPaint
            )
        }
        canvas.drawText(animatedSpeed.toInt().toString(), cx, cy - size * 0.02f, speedPaint)
        canvas.drawText(unitLabel, cx, cy + size * 0.06f, unitPaint)
        rpmPaint.color = zoneColor()
        canvas.drawText("${animatedRpm} RPM", cx, cy + size * 0.125f, rpmPaint)
        if (shifting) {
            canvas.drawText("SHIFT", cx, cy - size * 0.20f, shiftPaint)
        }
    }
}
