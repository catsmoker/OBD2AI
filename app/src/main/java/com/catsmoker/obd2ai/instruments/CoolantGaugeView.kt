package com.catsmoker.obd2ai.instruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units

/** Coolant zone; thresholds come from the app's existing warning model. */
enum class CoolantZone { COLD, NORMAL, WARN, CRITICAL }

/**
 * Compact coolant indicator: caption + value over a short horizontal
 * thermometer tube with a bulb at the left. Zones reuse the app's own
 * interpretation — the Offline-AI warn threshold and
 * [PrefsKeys.COOLANT_ALARM_C] critical. A null reading renders dimmed
 * with "--" (unknown stays unknown).
 *
 * Paints are cached as fields sized in `onSizeChanged`.
 */
class CoolantGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var tempC: Int? = null
    private var imperial: Boolean = false
    private var warnAtC: Int = PrefsKeys.DEFAULT_OFFLINE_COOLANT
    private var criticalAtC: Int = PrefsKeys.COOLANT_ALARM_C

    private val ink: Int = ContextCompat.getColor(context, R.color.on_surface)
    private val ok: Int = ContextCompat.getColor(context, R.color.gauge_ok)
    private val warn: Int = ContextCompat.getColor(context, R.color.gauge_warn)
    private val danger: Int = ContextCompat.getColor(context, R.color.gauge_danger)
    private val cold: Int = ContextCompat.getColor(context, R.color.gauge_neon)
    private val track: Int = ContextCompat.getColor(context, R.color.gauge_track)

    private val tubeRect = RectF()
    private val fillRect = RectF()
    private var tubeH = 0f
    private var bulbR = 0f
    private var bulbCx = 0f
    private var barLeft = 0f

    private val tubePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private var captionPaint: Paint = makeText(20f, ink)
    private var valuePaint: Paint = makeText(28f, ink, bold = true)

    fun setTemp(celsius: Int?, imperial: Boolean, warnAtC: Int, criticalAtC: Int) {
        val changed = tempC != celsius || this.imperial != imperial ||
            this.warnAtC != warnAtC || this.criticalAtC != criticalAtC
        tempC = celsius
        this.imperial = imperial
        this.warnAtC = warnAtC
        this.criticalAtC = criticalAtC
        contentDescription = if (celsius != null) {
            val shown = Units.displayTemp(celsius.toDouble(), imperial).toInt()
            val unit = if (imperial) "degrees Fahrenheit" else "degrees Celsius"
            "Coolant $shown $unit"
        } else {
            "Coolant unknown"
        }
        if (changed) invalidate()
    }

    private fun zone(): CoolantZone? {
        val t = tempC ?: return null
        return zoneFor(t, warnAtC, criticalAtC)
    }

    private fun zoneColor(): Int = when (zone()) {
        CoolantZone.COLD -> cold
        CoolantZone.WARN -> warn
        CoolantZone.CRITICAL -> danger
        else -> ok
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
        tubeH = h * 0.13f
        bulbR = h * 0.11f
        bulbCx = w * 0.06f + bulbR
        barLeft = bulbCx + bulbR * 0.9f
        val barRight = w * 0.96f
        val barCy = h * 0.78f
        tubeRect.set(barLeft, barCy - tubeH / 2f, barRight, barCy + tubeH / 2f)
        tubePaint.color = track
        markPaint.color = ink
        markPaint.alpha = 140
        markPaint.strokeWidth = tubeH * 0.22f
        captionPaint = makeText(h * 0.20f, ink)
        valuePaint = makeText(h * 0.30f, ink, bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dim = if (tempC != null) 255 else 110
        val left = w * 0.06f
        canvas.drawText("COOLANT", left, h * 0.26f, captionPaint.also { it.alpha = dim })
        // Bulb + horizontal tube shell.
        bulbPaint.color = track
        bulbPaint.alpha = dim
        canvas.drawCircle(bulbCx, tubeRect.centerY(), bulbR, bulbPaint)
        tubePaint.alpha = dim
        canvas.drawRoundRect(tubeRect, tubeH / 2f, tubeH / 2f, tubePaint)
        val t = tempC
        if (t != null) {
            val frac = ((t - DISPLAY_MIN_C) / (DISPLAY_MAX_C - DISPLAY_MIN_C).toFloat()).coerceIn(0f, 1f)
            fillRect.set(
                tubeRect.left,
                tubeRect.top,
                tubeRect.left + tubeRect.width() * frac,
                tubeRect.bottom
            )
            fillPaint.color = zoneColor()
            fillPaint.alpha = dim
            canvas.drawRoundRect(fillRect, tubeH / 2f, tubeH / 2f, fillPaint)
            bulbPaint.color = zoneColor()
            bulbPaint.alpha = dim
            canvas.drawCircle(bulbCx, tubeRect.centerY(), bulbR, bulbPaint)
            // Warn/critical tick marks on the tube.
            markAt(canvas, warnAtC)
            markAt(canvas, criticalAtC)
            val shown = Units.displayTemp(t.toDouble(), imperial).toInt()
            val unit = if (imperial) "°F" else "°C"
            valuePaint.alpha = dim
            canvas.drawText("$shown $unit", left, h * 0.58f, valuePaint)
        } else {
            valuePaint.alpha = dim
            canvas.drawText("-- °C", left, h * 0.58f, valuePaint)
        }
    }

    private fun markAt(canvas: Canvas, tempMarkC: Int) {
        val frac = ((tempMarkC - DISPLAY_MIN_C) / (DISPLAY_MAX_C - DISPLAY_MIN_C).toFloat()).coerceIn(0f, 1f)
        val x = tubeRect.left + tubeRect.width() * frac
        canvas.drawLine(x, tubeRect.top - tubeH * 0.35f, x, tubeRect.bottom + tubeH * 0.35f, markPaint)
    }

    companion object {
        /** Display span of the tube (readings clamp to it; it is not a limit). */
        const val DISPLAY_MIN_C = 50
        const val DISPLAY_MAX_C = 130

        /**
         * Generic cold marker: below this the engine simply hasn't warmed up.
         * Display-only warm-up hint, not a fault threshold.
         */
        const val COLD_BELOW_C = 70

        fun zoneFor(tempC: Int, warnAtC: Int, criticalAtC: Int): CoolantZone = when {
            tempC >= criticalAtC -> CoolantZone.CRITICAL
            tempC >= warnAtC -> CoolantZone.WARN
            tempC < COLD_BELOW_C -> CoolantZone.COLD
            else -> CoolantZone.NORMAL
        }
    }
}
