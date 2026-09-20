package com.catsmoker.obd2ai.instruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.OnlineAiManager

/** Fuel level state, using the app's own low-fuel interpretation. */
enum class FuelState { UNKNOWN, NORMAL, LOW }

/**
 * Compact fuel indicator: caption + value over a short E–F segmented bar.
 * Low is exactly [OnlineAiManager.fuelLow] (<= 15%). A null reading renders
 * dimmed with "-- %" — never 0%, never invented.
 *
 * Paints are cached as fields sized in `onSizeChanged`.
 */
class FuelGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pct: Int? = null

    private val ink: Int = ContextCompat.getColor(context, R.color.on_surface)
    private val ok: Int = ContextCompat.getColor(context, R.color.gauge_ok)
    private val warn: Int = ContextCompat.getColor(context, R.color.gauge_warn)
    private val track: Int = ContextCompat.getColor(context, R.color.gauge_track)

    private val segRect = RectF()
    private var segGap = 0f
    private var cornerR = 0f
    private var barH = 0f

    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var glyphStroke = 0f
    private var captionPaint: Paint = makeText(20f, ink)
    private var valuePaint: Paint = makeText(28f, ink, bold = true)
    private var endPaint: Paint = makeText(18f, ink, bold = true)

    fun setFuel(percent: Int?) {
        val clamped = percent?.coerceIn(0, 100)
        if (pct != clamped) {
            pct = clamped
            contentDescription = if (clamped != null) "Fuel $clamped percent" else "Fuel unknown"
            invalidate()
        }
    }

    private fun makeText(sizePx: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            textAlign = Paint.Align.LEFT
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        segGap = h * 0.03f
        cornerR = h * 0.03f
        barH = h * 0.17f
        glyphStroke = (h * 0.035f).coerceAtLeast(2f)
        glyphPaint.strokeWidth = glyphStroke
        captionPaint = makeText(h * 0.20f, ink)
        valuePaint = makeText(h * 0.30f, ink, bold = true)
        endPaint = makeText(h * 0.16f, ink, bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * 0.06f
        val dim = if (pct != null) 255 else 110
        val p = pct
        val low = p != null && OnlineAiManager.fuelLow(p)
        val caption = if (low) "FUEL LOW" else "FUEL"
        captionPaint.color = if (low) warn else ink
        captionPaint.alpha = dim
        canvas.drawText(caption, left, h * 0.26f, captionPaint)
        // Compact fuel-pump glyph, top-right: body + hose + nozzle + base.
        glyphPaint.color = ink
        glyphPaint.alpha = dim
        val gx = w * 0.97f
        val gyTop = h * 0.06f
        val gyBot = h * 0.30f
        val gw = (gx - w * 0.82f).coerceAtLeast(glyphStroke * 4f)
        val gxLeft = gx - gw
        canvas.drawRect(gxLeft, gyTop, gx, gyBot, glyphPaint)
        canvas.drawLine(gx, gyTop + (gyBot - gyTop) * 0.25f, gx + gw * 0.45f, gyTop, glyphPaint)
        canvas.drawLine(gxLeft - gw * 0.25f, gyBot, gx + gw * 0.10f, gyBot, glyphPaint)
        // E–F segmented bar.
        val barTop = h * 0.70f
        val eW = w * 0.07f
        val fX = w * 0.96f
        val totalW = fX - eW - left
        val segW = (totalW - segGap * (SEGMENTS - 1)) / SEGMENTS
        val litCount = if (p != null) (p / 100f * SEGMENTS).toInt().coerceIn(0, SEGMENTS) else 0
        for (i in 0 until SEGMENTS) {
            val segLeft = left + eW + i * (segW + segGap)
            segRect.set(segLeft, barTop, segLeft + segW, barTop + barH)
            segPaint.color = when {
                p == null -> track
                i < litCount -> if (low) warn else ok
                else -> track
            }
            segPaint.alpha = if (p == null) dim else 255
            canvas.drawRoundRect(segRect, cornerR, cornerR, segPaint)
        }
        endPaint.alpha = dim
        canvas.drawText("E", left, barTop + barH * 0.95f, endPaint)
        canvas.drawText("F", fX - w * 0.03f, barTop + barH * 0.95f, endPaint)
        valuePaint.alpha = dim
        canvas.drawText(if (p != null) "$p %" else "-- %", left, h * 0.58f, valuePaint)
    }

    companion object {
        const val SEGMENTS = 8

        fun stateFor(percent: Int?): FuelState = when {
            percent == null -> FuelState.UNKNOWN
            OnlineAiManager.fuelLow(percent) -> FuelState.LOW
            else -> FuelState.NORMAL
        }
    }
}
