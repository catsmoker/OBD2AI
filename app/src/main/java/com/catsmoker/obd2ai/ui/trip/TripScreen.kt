package com.catsmoker.obd2ai.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.ui.components.CardGroup
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.glass.GlassCard
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import com.catsmoker.obd2ai.vehicle.TripComputer
import kotlinx.coroutines.delay

/**
 * Trip computer screen: start/stop/reset plus time, distance, average and
 * top speed, top RPM and idle time. Samples the shared SI flows once a
 * second; display converts to the user's units. Nothing is persisted.
 *
 * The two numbers a driver actually watches — elapsed time and distance —
 * get a glass hero; everything else is secondary and lives in a compact grid.
 */
@Composable
fun TripScreen(
    imperial: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    demoMode: Boolean = false
) {
    // The trip is owned by TripSession, not by this composition: leaving the
    // screen (or rotating) no longer wipes it. Sampling happens once a second
    // in the app root while the app is resumed.
    val snapshot by TripSession.snapshot.collectAsStateWithLifecycle()
    val running by TripSession.isRunning.collectAsStateWithLifecycle()
    // Distance only grows while the dashboard polls; say so instead of looking
    // stuck. Demo mode keeps receiving values, so it needs no hint.
    val showDistanceHint = TripNotice.showDistanceHint(running, demoMode)

    ScreenScaffold(
        title = stringResource(R.string.trip_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        trailingContent = {
            StatusBadge(
                label = if (running) {
                    stringResource(R.string.trip_running)
                } else {
                    stringResource(R.string.trip_idle_state)
                },
                active = running
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (showDistanceHint) {
                SectionCard {
                    Text(
                        text = stringResource(R.string.trip_distance_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(ObdSpacing.CardGap))
            }

            GlassCard(
                shape = RoundedCornerShape(ObdRadius.Card),
                elevation = 4.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HeroStat(
                            label = stringResource(R.string.trip_duration),
                            value = TripComputer.formatDuration(snapshot.durationMs),
                            modifier = Modifier.weight(1f)
                        )
                        HeroStat(
                            label = stringResource(R.string.trip_distance),
                            value = if (imperial) {
                                stringResource(R.string.trip_dist_mi, Units.kmhToMph(snapshot.distanceKm))
                            } else {
                                stringResource(R.string.trip_dist_km, snapshot.distanceKm)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            SectionLabel(text = stringResource(R.string.trip_section_stats), accent = true)
            CardGroup {
                SectionCard {
                    StatRow(
                        label = stringResource(R.string.trip_avg_speed),
                        value = displaySpeed(snapshot.avgKmh, imperial)
                    )
                    StatDivider()
                    StatRow(
                        label = stringResource(R.string.trip_max_speed),
                        value = displaySpeed(snapshot.maxKmh, imperial)
                    )
                    StatDivider()
                    StatRow(
                        label = stringResource(R.string.trip_max_rpm),
                        value = snapshot.maxRpm.toString()
                    )
                    StatDivider()
                    StatRow(
                        label = stringResource(R.string.trip_idle),
                        value = TripComputer.formatDuration(snapshot.idleMs)
                    )
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)
            ) {
                ObdButton(
                    onClick = { TripSession.start(System.currentTimeMillis()) },
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.trip_start))
                }
                ObdOutlinedButton(
                    onClick = { TripSession.stop(System.currentTimeMillis()) },
                    enabled = running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.trip_stop))
                }
                ObdOutlinedButton(
                    onClick = { TripSession.reset(System.currentTimeMillis()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.trip_reset))
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))
        }
    }
}

/** The one number a user looks at: big, dot-matrix, labelled above. */
@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Compact label/value row inside the stats card. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Parse the leading numeric token from a flow value like "120 km/h" or "--". */
/** Speed converted to the user's units, as plain text. */
@Composable
private fun displaySpeed(kmh: Double, imperial: Boolean): String {
    val display = Units.displaySpeed(kmh, imperial).toInt()
    return if (imperial) {
        stringResource(R.string.trip_speed_mph, display)
    } else {
        stringResource(R.string.trip_speed_kmh, display)
    }
}
