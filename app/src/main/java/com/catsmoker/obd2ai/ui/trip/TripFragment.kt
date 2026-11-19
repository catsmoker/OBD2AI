package com.catsmoker.obd2ai.ui.trip

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.vehicle.TripComputer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trip computer screen: start/stop/reset plus time, distance, average and
 * top speed, top RPM and idle time. Samples the shared SI flows once a
 * second; display converts to the user's units. Nothing is persisted.
 *
 * Mirrors the XML fragment's behavior exactly: speed/RPM come from
 * [ObdDataHolder] flows ("--"/"ERROR" parse to unknown), the screen owns the
 * [TripComputer] instance, and units are read once from preferences.
 */
class TripFragment : Fragment() {

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val imperial = Units.isImperial(prefs)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    TripScreen(
                        imperial = imperial,
                        onBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}

/** Compose content for the trip screen. */
@Composable
fun TripScreen(
    imperial: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Lifecycle-aware collection of the live OBD flows ("--"/"ERROR" = unknown).
    val speedText by ObdDataHolder.speedFlow.collectAsStateWithLifecycle("-- km/h")
    val rpmText by ObdDataHolder.rpmFlow.collectAsStateWithLifecycle("-- RPM")

    val trip = remember { TripComputer() }
    var snapshot by remember { mutableStateOf(trip.snapshot(System.currentTimeMillis())) }

    // Sample once a second and re-render; cancelled when the composition ends.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val speed = parseSpeed(speedText)
            val rpm = parseRpm(rpmText)
            trip.sample(speed, rpm, now)
            snapshot = trip.snapshot(now)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.trip_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionCard {
                SectionLabel(text = stringResource(R.string.trip_title), accent = true)
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(label = stringResource(R.string.trip_duration), value = TripComputer.formatDuration(snapshot.durationMs))
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(
                    label = stringResource(R.string.trip_distance),
                    value = if (imperial) {
                        stringResource(R.string.trip_dist_mi, Units.kmhToMph(snapshot.distanceKm))
                    } else {
                        stringResource(R.string.trip_dist_km, snapshot.distanceKm)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(label = stringResource(R.string.trip_avg_speed), value = null) {
                    speedReadout(snapshot.avgKmh, imperial)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(label = stringResource(R.string.trip_max_speed), value = null) {
                    speedReadout(snapshot.maxKmh, imperial)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(label = stringResource(R.string.trip_max_rpm), value = snapshot.maxRpm.toString())
                Spacer(modifier = Modifier.height(8.dp))
                TripRow(label = stringResource(R.string.trip_idle), value = TripComputer.formatDuration(snapshot.idleMs))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObdButton(
                    onClick = {
                        trip.start(System.currentTimeMillis())
                        snapshot = trip.snapshot(System.currentTimeMillis())
                    },
                    enabled = !trip.isRunning,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.trip_start))
                }
                ObdOutlinedButton(
                    onClick = {
                        trip.stop(System.currentTimeMillis())
                        snapshot = trip.snapshot(System.currentTimeMillis())
                    },
                    enabled = trip.isRunning,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.trip_stop))
                }
                ObdOutlinedButton(
                    onClick = {
                        trip.reset()
                        snapshot = trip.snapshot(System.currentTimeMillis())
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.trip_reset))
                }
            }
        }
    }
}

/** Parse the leading numeric token from a flow value like "120 km/h" or "--". */
private fun parseSpeed(s: String): Double? = s.split(" ").firstOrNull()?.toFloatOrNull()?.toDouble()
private fun parseRpm(s: String): Int? = s.split(" ").firstOrNull()?.toIntOrNull()

/** Speed value converted to the user's units, rendered as a Text. */
@Composable
private fun speedReadout(kmh: Double, imperial: Boolean) {
    val context = LocalContext.current
    val display = Units.displaySpeed(kmh, imperial).toInt()
    val text = if (imperial) {
        stringResource(R.string.trip_speed_mph, display)
    } else {
        stringResource(R.string.trip_speed_kmh, display)
    }
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
}

/** One stat row: label + right-aligned value or trailing content. */
@Composable
private fun TripRow(label: String, value: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        trailing()
    }
}
