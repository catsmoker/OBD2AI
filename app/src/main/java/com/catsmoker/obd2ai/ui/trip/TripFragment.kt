package com.catsmoker.obd2ai.ui.trip

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.vehicle.TripComputer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trip computer screen: start/stop/reset plus time, distance, average and
 * top speed, top RPM and idle time. Samples the shared SI flows once a
 * second; display converts to the user's units. Nothing is persisted.
 */
class TripFragment : Fragment() {
    private val trip = TripComputer()
    private var imperial = false
    private lateinit var durationView: TextView
    private lateinit var distanceView: TextView
    private lateinit var avgView: TextView
    private lateinit var maxSpeedView: TextView
    private lateinit var maxRpmView: TextView
    private lateinit var idleView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_trip, container, false)
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        imperial = Units.isImperial(prefs)

        durationView = view.findViewById(R.id.tripDuration)
        distanceView = view.findViewById(R.id.tripDistance)
        avgView = view.findViewById(R.id.tripAvg)
        maxSpeedView = view.findViewById(R.id.tripMaxSpeed)
        maxRpmView = view.findViewById(R.id.tripMaxRpm)
        idleView = view.findViewById(R.id.tripIdle)
        startButton = view.findViewById(R.id.tripStart)
        stopButton = view.findViewById(R.id.tripStop)

        startButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.start(System.currentTimeMillis())
            refreshButtons()
        }
        stopButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.stop(System.currentTimeMillis())
            refreshButtons()
            render(System.currentTimeMillis())
        }
        view.findViewById<Button>(R.id.tripReset).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.reset()
            refreshButtons()
            render(System.currentTimeMillis())
        }
        refreshButtons()
        render(System.currentTimeMillis())

        lifecycleScope.launch {
            while (isAdded) {
                val speed = ObdDataHolder.speedFlow.value.split(" ")[0].toFloatOrNull()?.toDouble()
                val rpm = ObdDataHolder.rpmFlow.value.split(" ")[0].toIntOrNull()
                trip.sample(speed, rpm, System.currentTimeMillis())
                activity?.runOnUiThread { render(System.currentTimeMillis()) }
                delay(1000)
            }
        }
        return view
    }

    private fun refreshButtons() {
        startButton.isEnabled = !trip.isRunning
        stopButton.isEnabled = trip.isRunning
    }

    private fun speedText(kmh: Double): String {
        return if (imperial) {
            getString(R.string.trip_speed_mph, Units.displaySpeed(kmh, true).toInt())
        } else {
            getString(R.string.trip_speed_kmh, kmh.toInt())
        }
    }

    private fun render(nowMs: Long) {
        val snap = trip.snapshot(nowMs)
        durationView.text = getString(R.string.trip_duration, TripComputer.formatDuration(snap.durationMs))
        distanceView.text = getString(
            R.string.trip_distance,
            if (imperial) getString(R.string.trip_dist_mi, Units.kmhToMph(snap.distanceKm))
            else getString(R.string.trip_dist_km, snap.distanceKm)
        )
        avgView.text = getString(R.string.trip_avg_speed, speedText(snap.avgKmh))
        maxSpeedView.text = getString(R.string.trip_max_speed, speedText(snap.maxKmh))
        maxRpmView.text = getString(R.string.trip_max_rpm, snap.maxRpm)
        idleView.text = getString(R.string.trip_idle, TripComputer.formatDuration(snap.idleMs))
    }
}
