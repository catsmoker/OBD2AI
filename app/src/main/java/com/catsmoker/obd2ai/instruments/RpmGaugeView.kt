package com.catsmoker.obd2ai.instruments

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Visual family for the tachometer, matched to the selected speedometer style. */
enum class TachoFace { NEEDLE, SEGMENT, NEON, MINIMAL, DIGITAL }

/**
 * Dedicated tachometer: the RPM half of the twin-gauge cluster.
 *
 * Scale is 0–8 (x1000 RPM) with major ticks each 1000 and minors each 500.
 * The active sweep follows the same shift-point zone interpretation as the
 * speed gauges ([SpeedometerStyle.rpmZone]); the tick at [shiftRpm] is the
 * user's own setting. The hatched band at the top is a generic upper-range
 * marker — it does NOT claim to be this vehicle's exact redline.
 * A null reading renders dimmed with "--" (unknown stays unknown).
 *
 * Paints are cached as fields sized in `onSizeChanged`, so `onDraw` never
 * allocates — same performance standard as the speedometer views.
 */
class RpmGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var face: TachoFace = TachoFace.NEEDLE
    var rpmMax: Int = 8000
    var shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM

    private var animatedRpm: Float = 0f
    private var known: Boolean = false
    private var rpmAnimator: ValueAnimator? = null

    private val ink: Int = ContextCompat.getColor(context, R.color.on_surface)
    private val ok: Int = ContextCompat.getColor(context, R.color.gauge_ok)
    private val warn: Int = ContextCompat.getColor(context, R.color.gauge_warn)
    private val danger: Int = ContextCompat.getColor(context, R.color.gauge_danger)
    private val sport: Int = ContextCompat.getColor(context, R.color.gauge_sport)
    private val neon: Int = ContextCompat.getColor(context, R.color.gauge_neon)
    private val track: Int = ContextCompat.getColor(context, R.color.gauge_track)

    private val arcRect = RectF()
    private val barRect = RectF()
    private var size = 0f

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
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var numeralPaint: Paint = makeText(40f, ink)
    private var valuePaint: Paint = makeText(60f, ink, bold = true)
    private var labelPaint: Paint = makeText(20f, ink)

    /** Null = unknown: sweep parks dimmed and the readout shows "--". */
    fun setRpm(rpm: Int?, shift: Int = shiftRpm) {
        shiftRpm = shift
        val target = rpm?.let { SpeedometerStyle.clampRpm(it, rpmMax).toFloat() }
        if (target == null) {
            rpmAnimator?.cancel()
            known = false
            contentDescription = "RPM unknown"
            invalidate()
            return
        }
        known = true
        contentDescription = "${target.roundToInt()} RPM"
        if (!isLaidOut) {
            animatedRpm = target
            invalidate()
            return
        }
        if (abs(target - animatedRpm) < 20f) {
            animatedRpm = target
            invalidate()
            return
        }
        rpmAnimator?.cancel()
        val from = animatedRpm
        rpmAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animatedRpm = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun zoneColor(): Int = when (SpeedometerStyle.rpmZone(animatedRpm.roundToInt(), shiftRpm)) {
        0 -> ok
        1 -> warn
        else -> danger
    }

    private fun makeText(sizePx: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val fallback = (220 * resources.displayMetrics.density).toInt()
        val size = when {
            wMode == MeasureSpec.EXACTLY && hMode == MeasureSpec.EXACTLY -> min(wSize, hSize)
            wMode == MeasureSpec.EXACTLY -> wSize
            hMode == MeasureSpec.EXACTLY -> hSize
            else -> fallback
        }
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size = min(w, h).toFloat()
        val pad = size * 0.10f
        arcRect.set(pad, pad, w - pad, h - pad)
        val arcWidth = if (face == TachoFace.MINIMAL) size * 0.028f else size * 0.07f
        arcTrackPaint.color = track
        arcTrackPaint.strokeWidth = arcWidth
        arcActivePaint.strokeWidth = arcWidth
        tickPaint.color = ink
        needlePaint.color = ink
        needlePaint.strokeWidth = size * 0.014f
        hubPaint.color = ink
        markPaint.color = danger
        markPaint.strokeWidth = size * 0.014f
        segmentPaint.strokeWidth = size * 0.055f
        barTrackPaint.color = track
        numeralPaint = makeText(size * 0.048f, ink)
        valuePaint = makeText(if (face == TachoFace.DIGITAL) size * 0.24f else size * 0.15f, ink, bold = true)
        labelPaint = makeText(size * 0.045f, ink)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val dim = if (known) 255 else 110
        val frac = SpeedometerStyle.fraction(animatedRpm, rpmMax.toFloat())
        val sweep = SpeedometerStyle.SWEEP_ANGLE * frac
        if (face == TachoFace.DIGITAL) {
            drawDigital(canvas, cx, cy, frac, dim)
        } else if (face == TachoFace.SEGMENT) {
            drawSegments(canvas, cx, cy, frac, dim)
            drawShiftMark(canvas, cx, cy, dim)
        } else {
            drawArcDial(canvas, sweep, dim)
            drawShiftMark(canvas, cx, cy, dim)
            if (face == TachoFace.NEEDLE && known) drawNeedle(canvas, cx, cy, sweep, dim)
        }
        if (face != TachoFace.DIGITAL) drawTicks(canvas, cx, cy, dim)
        drawReadout(canvas, cx, cy, dim)
    }

    private fun arcWidth(): Float =
        if (face == TachoFace.MINIMAL) size * 0.028f else size * 0.07f

    private fun drawArcDial(canvas: Canvas, sweep: Float, dim: Int) {
        arcTrackPaint.alpha = dim
        canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, SpeedometerStyle.SWEEP_ANGLE, false, arcTrackPaint)
        if (known && sweep > 1f) {
            arcActivePaint.color = when (face) {
                TachoFace.NEON -> neon
                TachoFace.MINIMAL -> ink
                else -> zoneColor()
            }
            if (face == TachoFace.NEON) {
                arcActivePaint.setShadowLayer(arcWidth() * 0.45f, 0f, 0f, neon)
            } else {
                arcActivePaint.clearShadowLayer()
            }
            arcActivePaint.alpha = dim
            canvas.drawArc(arcRect, SpeedometerStyle.START_ANGLE, sweep, false, arcActivePaint)
        }
        drawRedlineBand(canvas, dim)
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, sweep: Float, dim: Int) {
        val needleDeg = SpeedometerStyle.START_ANGLE + sweep
        val needleRad = Math.toRadians(needleDeg.toDouble())
        val radius = arcRect.width() / 2f
        val inner = radius - arcWidth() - size * 0.01f
        val needleLen = inner - size * 0.11f
        needlePaint.alpha = dim
        canvas.drawLine(
            cx, cy,
            cx + needleLen * cos(needleRad).toFloat(),
            cy + needleLen * sin(needleRad).toFloat(),
            needlePaint
        )
        hubPaint.alpha = dim
        canvas.drawCircle(cx, cy, size * 0.028f, hubPaint)
    }

    private fun drawSegments(canvas: Canvas, cx: Float, cy: Float, frac: Float, dim: Int) {
        val blocks = 24
        val shiftFrac = SpeedometerStyle.fraction(shiftRpm.toFloat(), rpmMax.toFloat())
        for (i in 0 until blocks) {
            val f0 = i / blocks.toFloat()
            val startDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f0
            val lit = known && f0 < frac
            segmentPaint.color = when {
                !lit -> track
                f0 >= shiftFrac -> danger
                else -> sport
            }
            segmentPaint.alpha = if (!lit) dim.coerceAtMost(160) else dim
            canvas.drawArc(arcRect, startDeg, SpeedometerStyle.SWEEP_ANGLE / blocks * 0.72f, false, segmentPaint)
        }
        drawRedlineBand(canvas, dim)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, dim: Int) {
        val radius = arcRect.width() / 2f
        val inner = radius - arcWidth() - size * 0.01f
        // Majors each 1000 RPM with numerals 0..8, minors each 500.
        for (k in 0..16) {
            val f = k / 16f
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val major = k % 2 == 0
            val len = if (major) size * 0.035f else size * 0.018f
            tickPaint.strokeWidth = if (major) size * 0.008f else size * 0.004f
            tickPaint.alpha = dim
            canvas.drawLine(
                cx + inner * c, cy + inner * s,
                cx + (inner - len) * c, cy + (inner - len) * s,
                tickPaint
            )
            if (major) {
                numeralPaint.alpha = dim
                canvas.drawText(
                    (k / 2).toString(),
                    cx + (inner - len - size * 0.05f) * c,
                    cy + (inner - len - size * 0.05f) * s + size * 0.018f,
                    numeralPaint
                )
            }
        }
    }

    /**
     * Generic upper-range band (hatched danger ticks across the top 1000 RPM).
     * Display-only: it marks "high revs", never this vehicle's exact redline.
     */
    private fun drawRedlineBand(canvas: Canvas, dim: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = arcRect.width() / 2f
        val startFrac = (rpmMax - REDLINE_BAND_RPM) / rpmMax.toFloat()
        markPaint.alpha = dim.coerceAtMost(200)
        for (i in 0..4) {
            val f = startFrac + (1f - startFrac) * i / 4f
            val angleDeg = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * f
            val rad = Math.toRadians(angleDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            canvas.drawLine(
                cx + (radius - arcWidth()) * c, cy + (radius - arcWidth()) * s,
                cx + (radius + arcWidth() * 0.6f) * c, cy + (radius + arcWidth() * 0.6f) * s,
                markPaint
            )
        }
    }

    /** Shift marker at the user's own shift point. */
    private fun drawShiftMark(canvas: Canvas, cx: Float, cy: Float, dim: Int) {
        val shiftFrac = SpeedometerStyle.fraction(shiftRpm.toFloat(), rpmMax.toFloat())
        val markAngle = SpeedometerStyle.START_ANGLE + SpeedometerStyle.SWEEP_ANGLE * shiftFrac
        val rad = Math.toRadians(markAngle.toDouble())
        val radius = arcRect.width() / 2f
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        markPaint.alpha = dim
        canvas.drawLine(
            cx + (radius - arcWidth()) * c, cy + (radius - arcWidth()) * s,
            cx + (radius + arcWidth()) * c, cy + (radius + arcWidth()) * s,
            markPaint
        )
    }

    private fun drawDigital(canvas: Canvas, cx: Float, cy: Float, frac: Float, dim: Int) {
        val barH = size * 0.05f
        val barTop = cy + size * 0.28f
        barRect.set(cx - size * 0.36f, barTop, cx + size * 0.36f, barTop + barH)
        barTrackPaint.alpha = dim
        canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barTrackPaint)
        if (known) {
            barRect.right = barRect.left + barRect.width() * frac
            barPaint.color = zoneColor()
            barPaint.alpha = dim
            canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barPaint)
        }
    }

    private fun drawReadout(canvas: Canvas, cx: Float, cy: Float, dim: Int) {
        valuePaint.alpha = dim
        labelPaint.alpha = dim
        if (face == TachoFace.DIGITAL) {
            canvas.drawText(if (known) animatedRpm.roundToInt().toString() else "--", cx, cy + size * 0.02f, valuePaint)
            canvas.drawText("RPM", cx, cy + size * 0.10f, labelPaint)
        } else {
            canvas.drawText(
                if (known) "%.1f".format(animatedRpm / 1000f) else "--",
                cx, cy + size * 0.24f, valuePaint
            )
            canvas.drawText("x1000 RPM", cx, cy + size * 0.31f, labelPaint)
        }
    }

    companion object {
        /** Generic top-band width in RPM (display-only, not the vehicle's redline). */
        const val REDLINE_BAND_RPM = 1000

        /** Fraction where the shift zone starts for a given shift point. */
        fun shiftBandStartFrac(shiftRpm: Int, rpmMax: Int = 8000): Float =
            SpeedometerStyle.fraction(shiftRpm.toFloat(), rpmMax.toFloat())
    }
}
