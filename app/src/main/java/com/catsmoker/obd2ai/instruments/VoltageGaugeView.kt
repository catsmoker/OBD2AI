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

/** Electrical-system state, using the app's own voltage interpretation. */
enum class VoltState { UNKNOWN, NORMAL, BAD }

/**
 * Compact battery indicator for vehicle voltage (not a charge percentage —
 * the numeric volts stay authoritative). Caption + value over a short
 * battery outline whose fill tracks a 10–16 V display span; warn color
 * exactly when [OnlineAiManager.voltageBad] says so. A null reading renders
 * dimmed with "-- V".
 *
 * Paints are cached as fields sized in `onSizeChanged`.
 */
class VoltageGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var volts: Float? = null

    private val ink: Int = ContextCompat.getColor(context, R.color.on_surface)
    private val warn: Int = ContextCompat.getColor(context, R.color.gauge_warn)
    private val track: Int = ContextCompat.getColor(context, R.color.gauge_track)

    private val bodyRect = RectF()
    private val fillRect = RectF()
    private var cornerR = 0f
    private var strokeW = 0f
    private var nubW = 0f
    private var nubH = 0f

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var captionPaint: Paint = makeText(20f, ink)
    private var valuePaint: Paint = makeText(28f, ink, bold = true)

    fun setVoltage(v: Float?) {
        if (volts != v) {
            volts = v
            contentDescription = if (v != null) "Voltage %.1f volts".format(v) else "Voltage unknown"
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
        strokeW = h * 0.045f
        cornerR = h * 0.06f
        nubW = h * 0.06f
        nubH = h * 0.13f
        val left = w * 0.06f
        val right = w * 0.96f
        val barCy = h * 0.78f
        val barH = h * 0.17f
        bodyRect.set(left, barCy - barH / 2f, right - nubW, barCy + barH / 2f)
        outlinePaint.color = ink
        outlinePaint.strokeWidth = strokeW
        captionPaint = makeText(h * 0.20f, ink)
        valuePaint = makeText(h * 0.30f, ink, bold = true)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * 0.06f
        val dim = if (volts != null) 255 else 110
        canvas.drawText("VOLTAGE", left, h * 0.26f, captionPaint.also { it.alpha = dim })
        // Battery outline + terminal nub.
        outlinePaint.alpha = dim
        canvas.drawRoundRect(bodyRect, cornerR, cornerR, outlinePaint)
        val nubCx = bodyRect.right + nubW / 2f
        val nubCy = bodyRect.centerY()
        fillPaint.color = ink
        fillPaint.alpha = dim
        canvas.drawRect(nubCx - nubW / 2f, nubCy - nubH / 2f, nubCx + nubW / 2f, nubCy + nubH / 2f, fillPaint)
        val v = volts
        if (v != null) {
            val frac = ((v - DISPLAY_MIN_V) / (DISPLAY_MAX_V - DISPLAY_MIN_V)).coerceIn(0f, 1f)
            val inset = strokeW * 1.6f
            fillRect.set(
                bodyRect.left + inset,
                bodyRect.top + inset,
                bodyRect.left + inset + (bodyRect.width() - inset * 2f) * frac,
                bodyRect.bottom - inset
            )
            fillPaint.color = if (OnlineAiManager.voltageBad(v)) warn else ink
            fillPaint.alpha = dim
            if (fillRect.width() > 0f) {
                canvas.drawRoundRect(fillRect, cornerR / 2f, cornerR / 2f, fillPaint)
            }
            valuePaint.alpha = dim
            canvas.drawText("%.1f V".format(v), left, h * 0.58f, valuePaint)
        } else {
            valuePaint.alpha = dim
            canvas.drawText("-- V", left, h * 0.58f, valuePaint)
        }
    }

    companion object {
        /** Display span in volts (readings clamp to it; it is not a limit). */
        const val DISPLAY_MIN_V = 10f
        const val DISPLAY_MAX_V = 16f

        fun stateFor(v: Float?): VoltState = when {
            v == null -> VoltState.UNKNOWN
            OnlineAiManager.voltageBad(v) -> VoltState.BAD
            else -> VoltState.NORMAL
        }
    }
}
