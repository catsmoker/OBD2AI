package com.catsmoker.obd2ai.speedometers

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.catsmoker.obd2ai.instruments.RpmGaugeView
import com.catsmoker.obd2ai.instruments.TachoFace
import com.catsmoker.obd2ai.prefs.Units
import com.github.anastr.speedviewlib.TubeSpeedometer

/**
 * Builds and binds whichever [SpeedometerStyle] is selected.
 *
 * Rendering here is our own Views/Canvas code. The geometry ideas were studied
 * in the reference collection (ibrahimsn circular gauge math, the
 * Android-Speedometer segmented sport arc, anastr section warn-bands) and
 * reimplemented — no reference code or assets are vendored, so there is no
 * third-party license to track for these views.
 */
object SpeedometerHost {
    /** Demo values used for the Settings previews (no adapter, no readings). */
    const val PREVIEW_SPEED_KMH = 86f
    const val PREVIEW_RPM = 3200

    fun createView(context: Context, style: SpeedometerStyle): View = when (style) {
        SpeedometerStyle.DIGITAL -> DigitalSpeedometerView(context)
        SpeedometerStyle.CLASSIC -> ClassicSpeedometerView(context)
        SpeedometerStyle.MINIMAL -> MinimalSpeedometerView(context)
        SpeedometerStyle.SPORT -> SportSpeedometerView(context)
        SpeedometerStyle.FUTURISTIC -> FuturisticSpeedometerView(context)
        SpeedometerStyle.PERFORMANCE -> PerformanceSpeedometerView(context)
        SpeedometerStyle.RPM_FOCUSED -> RpmFocusedSpeedometerView(context)
        // Automotive keeps the mature TubeSpeedometer pair; the dashboard owns
        // the real one, previews get a lightweight twin from the same library.
        SpeedometerStyle.AUTOMOTIVE -> createAutomotivePreview(context)
    }

    /** Static preview binding: same data feed as driving, no animation. */
    fun bindPreview(view: View, imperial: Boolean) {        val speed = if (imperial) Units.kmhToMph(PREVIEW_SPEED_KMH.toDouble()).toFloat() else PREVIEW_SPEED_KMH
        val max = Units.speedGaugeMax(imperial)
        when (view) {
            is BaseSpeedometerView -> {
                view.maxSpeed = max
                view.unitLabel = Units.speedUnitLabel(imperial)
                view.setSpeed(speed, animate = false)
                view.setRpm(PREVIEW_RPM)
            }
            is LinearLayout -> {
                for (i in 0 until view.childCount) {
                    val tube = view.getChildAt(i) as? TubeSpeedometer ?: continue
                    if (i == 0) {
                        tube.maxSpeed = max
                        tube.unit = Units.speedUnitLabel(imperial)
                        tube.speedTo(speed, 0)
                    } else {
                        tube.maxSpeed = 50f
                        tube.unit = "x100 RPM"
                        tube.speedTo(PREVIEW_RPM / 100f, 0)
                    }
                }
            }
        }
    }

    /**
     * Tachometer face for a speedometer style, so switching styles yields a
     * coherent cluster instead of a mismatched pair. Pure logic, tested.
     */
    fun tachoFaceFor(style: SpeedometerStyle): TachoFace = when (style) {
        SpeedometerStyle.CLASSIC -> TachoFace.NEEDLE
        SpeedometerStyle.SPORT -> TachoFace.SEGMENT
        SpeedometerStyle.FUTURISTIC -> TachoFace.NEON
        SpeedometerStyle.MINIMAL -> TachoFace.MINIMAL
        SpeedometerStyle.DIGITAL -> TachoFace.DIGITAL
        SpeedometerStyle.PERFORMANCE -> TachoFace.NEEDLE
        SpeedometerStyle.RPM_FOCUSED -> TachoFace.NEON
        SpeedometerStyle.AUTOMOTIVE -> TachoFace.NEEDLE
    }

    /** Tachometer companion for [createView] (unused for AUTOMOTIVE: tubes). */
    fun createRpmView(context: Context, style: SpeedometerStyle): RpmGaugeView =
        RpmGaugeView(context).apply { face = tachoFaceFor(style) }

    private fun createAutomotivePreview(context: Context): LinearLayout {        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addView(TubeSpeedometer(context).apply {
                layoutParams = params
                withTremble = false
            })
            addView(TubeSpeedometer(context).apply {
                layoutParams = params
                withTremble = false
            })
        }
    }
}
