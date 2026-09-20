package com.catsmoker.obd2ai.ui.dashboard

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.Units
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Current simulated values, so reopening the sheet resumes where it left off. */
data class DemoSheetState(
    val speedKmh: Int,
    val rpm: Int,
    val coolantC: Int,
    val voltageTenths: Int,
    val fuelPct: Int,
    val engineOn: Boolean
)

/**
 * Demo control sheet (bottom sheet, demo mode only). All it does is let the
 * user drive simulated values; every change flows through the same
 * [com.catsmoker.obd2ai.obd.ObdDataHolder] flows the Live dashboard consumes,
 * so the Demo dashboard is pixel-identical to Live — only the source differs.
 */
class DemoControlsSheet(
    private val context: Context,
    private val imperial: Boolean,
    private val rpmMax: Int,
    initial: DemoSheetState,
    private val listener: Listener
) {
    interface Listener {
        fun onDemoSpeed(kmh: Int)
        fun onDemoRpm(rpm: Int)
        fun onDemoCoolant(celsius: Int)
        fun onDemoVoltage(tenths: Int)
        fun onDemoFuel(pct: Int)
        fun onDemoEngineChanged(on: Boolean)
    }

    companion object {
        /** Idle simulation after Start Car (a starting point, never a minimum). */
        const val IDLE_RPM = 800

        /** Coolant slider spans 50–130 °C. */
        const val COOLANT_OFFSET_C = 50
        const val COOLANT_SPAN_C = 80

        /** Voltage slider spans 9.0–16.0 V in tenths. */
        const val VOLTAGE_OFFSET_TENTHS = 90
        const val VOLTAGE_SPAN_TENTHS = 70
    }

    private var dialog: BottomSheetDialog? = null
    private var engineOn = initial.engineOn
    private var startButton: Button? = null

    private var speed = initial.speedKmh
    private var rpm = initial.rpm
    private var coolant = initial.coolantC
    private var voltageTenths = initial.voltageTenths
    private var fuel = initial.fuelPct

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun show() {
        if (isShowing()) return
        val view = LayoutInflater.from(context).inflate(R.layout.demo_controls_sheet, null)
        val sheet = BottomSheetDialog(context)
        dialog = sheet

        startButton = view.findViewById(R.id.demoSheetStart)
        val closeButton = view.findViewById<Button>(R.id.demoSheetClose)
        val speedLabel = view.findViewById<TextView>(R.id.demoSheetSpeedLabel)
        val rpmLabel = view.findViewById<TextView>(R.id.demoSheetRpmLabel)
        val coolantLabel = view.findViewById<TextView>(R.id.demoSheetCoolantLabel)
        val voltageLabel = view.findViewById<TextView>(R.id.demoSheetVoltageLabel)
        val fuelLabel = view.findViewById<TextView>(R.id.demoSheetFuelLabel)
        val speedSlider = view.findViewById<SeekBar>(R.id.demoSheetSpeed)
        val rpmSlider = view.findViewById<SeekBar>(R.id.demoSheetRpm)
        val coolantSlider = view.findViewById<SeekBar>(R.id.demoSheetCoolant)
        val voltageSlider = view.findViewById<SeekBar>(R.id.demoSheetVoltage)
        val fuelSlider = view.findViewById<SeekBar>(R.id.demoSheetFuel)

        fun renderSpeed() {
            speedLabel.text = if (imperial) {
                context.getString(R.string.demo_speed_mph, Units.kmhToMph(speed.toDouble()).toInt())
            } else {
                context.getString(R.string.demo_speed_label, speed)
            }
        }

        fun renderRpm() {
            rpmLabel.text = context.getString(R.string.demo_rpm_label, rpm)
        }

        fun renderCoolant() {
            coolantLabel.text = if (imperial) {
                context.getString(R.string.demo_coolant_f, Units.cToF(coolant.toDouble()).toInt())
            } else {
                context.getString(R.string.demo_coolant_label, coolant)
            }
        }

        fun renderVoltage() {
            voltageLabel.text = context.getString(R.string.demo_voltage_label, voltageTenths / 10f)
        }

        fun renderFuel() {
            fuelLabel.text = context.getString(R.string.demo_fuel_label, fuel)
        }

        fun renderEngine() {
            startButton?.text = context.getString(
                if (engineOn) R.string.demo_stop_car else R.string.demo_start_car
            )
        }

        speedSlider.max = 220
        rpmSlider.max = rpmMax
        coolantSlider.max = COOLANT_SPAN_C
        voltageSlider.max = VOLTAGE_SPAN_TENTHS
        fuelSlider.max = 100

        speedSlider.progress = speed.coerceIn(0, 220)
        rpmSlider.progress = rpm.coerceIn(0, rpmMax)
        coolantSlider.progress = (coolant - COOLANT_OFFSET_C).coerceIn(0, COOLANT_SPAN_C)
        voltageSlider.progress = (voltageTenths - VOLTAGE_OFFSET_TENTHS).coerceIn(0, VOLTAGE_SPAN_TENTHS)
        fuelSlider.progress = fuel.coerceIn(0, 100)
        renderSpeed()
        renderRpm()
        renderCoolant()
        renderVoltage()
        renderFuel()
        renderEngine()

        speedSlider.setOnSeekBarChangeListener(changeListener {
            speed = it
            renderSpeed()
            listener.onDemoSpeed(it)
        })
        rpmSlider.setOnSeekBarChangeListener(changeListener {
            rpm = it
            renderRpm()
            listener.onDemoRpm(it)
        })
        coolantSlider.setOnSeekBarChangeListener(changeListener {
            coolant = it + COOLANT_OFFSET_C
            renderCoolant()
            listener.onDemoCoolant(coolant)
        })
        voltageSlider.setOnSeekBarChangeListener(changeListener {
            voltageTenths = it + VOLTAGE_OFFSET_TENTHS
            renderVoltage()
            listener.onDemoVoltage(voltageTenths)
        })
        fuelSlider.setOnSeekBarChangeListener(changeListener {
            fuel = it
            renderFuel()
            listener.onDemoFuel(it)
        })

        startButton?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            engineOn = !engineOn
            if (engineOn) {
                rpm = IDLE_RPM.coerceAtMost(rpmMax)
                rpmSlider.progress = rpm
                renderRpm()
                listener.onDemoRpm(rpm)
            } else {
                rpm = 0
                rpmSlider.progress = 0
                renderRpm()
                listener.onDemoRpm(0)
            }
            listener.onDemoEngineChanged(engineOn)
            renderEngine()
        }
        closeButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dismiss()
        }
        sheet.setOnDismissListener { dialog = null }
        sheet.setContentView(view)
        sheet.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** Current values, so the fragment can re-seed the sheet after rotation. */
    fun snapshot(): DemoSheetState = DemoSheetState(speed, rpm, coolant, voltageTenths, fuel, engineOn)

    private fun changeListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
