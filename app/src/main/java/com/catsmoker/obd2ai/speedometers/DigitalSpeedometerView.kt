package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet

/** Huge high-contrast readout for night driving; wide, not square. */
class DigitalSpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSpeedometerView(context, attrs, defStyleAttr) {

    private val barRect = RectF()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shiftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var speedPaint: Paint = textPaint(60f, ink, bold = true)
    private var unitPaint: Paint = textPaint(20f, ink)
    private var rpmPaint: Paint = textPaint(16f, ok)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (wMode == MeasureSpec.UNSPECIFIED) {
            (300 * resources.displayMetrics.density).toInt()
        } else {
            wSize
        }
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = if (hMode == MeasureSpec.EXACTLY) hSize else (width * 0.52f).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hf = h.toFloat()
        speedPaint = textPaint(hf * 0.42f, ink, bold = true)
        unitPaint = textPaint(hf * 0.09f, ink)
        rpmPaint = textPaint(hf * 0.075f, zoneColor())
        barTrackPaint.color = track
        shiftPaint.color = danger
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        canvas.drawText(animatedSpeed.toInt().toString(), cx, h * 0.52f, speedPaint)
        canvas.drawText(unitLabel, cx, h * 0.68f, unitPaint)
        rpmPaint.color = zoneColor()
        canvas.drawText("${animatedRpm} RPM", cx, h * 0.80f, rpmPaint)
        // Bottom progress bar mirrors the needle position at a glance.
        val frac = SpeedometerStyle.fraction(animatedSpeed, maxSpeed)
        val barTop = h * 0.88f
        val barH = h * 0.05f
        barRect.set(w * 0.08f, barTop, w * 0.92f, barTop + barH)
        canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barTrackPaint)
        barRect.right = barRect.left + (barRect.width() * frac)
        barPaint.color = zoneColor()
        canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barPaint)
        // Shift dot, same semantics as every dial.
        if (animatedRpm >= shiftRpm) {
            canvas.drawCircle(w * 0.92f, h * 0.14f, h * 0.045f, shiftPaint)
        }
    }
}
