package com.catsmoker.obd2ai.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.ui.components.ErrorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Live Data destination. Owns the [LiveSession] lifecycle: the session starts
 * when the screen composes and is torn down when it leaves, with the same
 * rotation exception the old fragment had (rotation keeps the socket and the
 * polling loop alive).
 */
@Composable
fun LiveDestination(activity: MainActivity, navController: androidx.navigation.NavHostController) {
    var errorText by remember { mutableStateOf<String?>(null) }
    val session = remember { LiveSession(activity, { text -> errorText = text }) }

    DisposableEffect(Unit) {
        session.start()
        onDispose { session.stop(activity.isChangingConfigurations) }
    }

    // Engine sound follows foreground/background like the old fragment did.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.onEnterForeground()
                Lifecycle.Event.ON_PAUSE -> session.onEnterBackground()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Keep-screen-on while the dashboard is visible (pref-gated).
    if (session.keepScreenOn) {
        DisposableEffect(Unit) {
            val window = activity.window
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LiveContent(session, onBack = { navController.navigateUp() })

    session.demoSheet.value?.let { state ->
        DemoSheet(
            imperial = session.imperial,
            rpmMax = session.rpmMax,
            initial = state,
            onSpeed = { session.pushDemoSpeed(it) },
            onRpm = { session.pushDemoRpm(it) },
            onCoolant = { session.pushDemoCoolant(it) },
            onVoltage = { session.pushDemoVoltage(it) },
            onFuel = { session.pushDemoFuel(it) },
            onEngineChanged = { on -> session.demoEngineOn = on },
            onDismiss = { session.demoSheet.value = null }
        )
    }

    ErrorDialog(message = errorText, onDismiss = { errorText = null })
}

/** Idle simulation after Start Car (a starting point, never a minimum). */
private const val DEMO_IDLE_RPM = 800

/** Coolant slider spans 50–130 °C; voltage spans 9.0–16.0 V in tenths. */
private const val DEMO_COOLANT_OFFSET_C = 50
private const val DEMO_COOLANT_SPAN_C = 80
private const val DEMO_VOLTAGE_OFFSET_TENTHS = 90
private const val DEMO_VOLTAGE_SPAN_TENTHS = 70

/** Compose demo control sheet (demo mode only). Sliders drive the same
 * [com.catsmoker.obd2ai.obd.ObdDataHolder] flows the dashboard consumes, so
 * the Demo dashboard is pixel-identical to Live — only the source differs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoSheet(
    imperial: Boolean,
    rpmMax: Int,
    initial: DemoSheetState,
    onSpeed: (Int) -> Unit,
    onRpm: (Int) -> Unit,
    onCoolant: (Int) -> Unit,
    onVoltage: (Int) -> Unit,
    onFuel: (Int) -> Unit,
    onEngineChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var speed by remember { mutableIntStateOf(initial.speedKmh) }
    var rpm by remember { mutableIntStateOf(initial.rpm) }
    var coolant by remember { mutableIntStateOf(initial.coolantC) }
    var voltageTenths by remember { mutableIntStateOf(initial.voltageTenths) }
    var fuel by remember { mutableIntStateOf(initial.fuelPct) }
    var engineOn by remember { mutableStateOf(initial.engineOn) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SliderRow(
                label = if (imperial) {
                    stringResource(R.string.demo_speed_mph, Units.kmhToMph(speed.toDouble()).toInt())
                } else {
                    stringResource(R.string.demo_speed_label, speed)
                },
                value = speed.coerceIn(0, 220).toFloat(),
                valueRange = 0f..220f,
                onChange = {
                    speed = it.toInt()
                    onSpeed(speed)
                }
            )
            SliderRow(
                label = stringResource(R.string.demo_rpm_label, rpm),
                value = rpm.coerceIn(0, rpmMax).toFloat(),
                valueRange = 0f..rpmMax.toFloat(),
                onChange = {
                    rpm = it.toInt()
                    onRpm(rpm)
                }
            )
            SliderRow(
                label = if (imperial) {
                    stringResource(R.string.demo_coolant_f, Units.cToF(coolant.toDouble()).toInt())
                } else {
                    stringResource(R.string.demo_coolant_label, coolant)
                },
                value = (coolant - DEMO_COOLANT_OFFSET_C).coerceIn(0, DEMO_COOLANT_SPAN_C).toFloat(),
                valueRange = 0f..DEMO_COOLANT_SPAN_C.toFloat(),
                onChange = {
                    coolant = it.toInt() + DEMO_COOLANT_OFFSET_C
                    onCoolant(coolant)
                }
            )
            SliderRow(
                label = stringResource(R.string.demo_voltage_label, voltageTenths / 10f),
                value = (voltageTenths - DEMO_VOLTAGE_OFFSET_TENTHS)
                    .coerceIn(0, DEMO_VOLTAGE_SPAN_TENTHS).toFloat(),
                valueRange = 0f..DEMO_VOLTAGE_SPAN_TENTHS.toFloat(),
                onChange = {
                    voltageTenths = it.toInt() + DEMO_VOLTAGE_OFFSET_TENTHS
                    onVoltage(voltageTenths)
                }
            )
            SliderRow(
                label = stringResource(R.string.demo_fuel_label, fuel),
                value = fuel.coerceIn(0, 100).toFloat(),
                valueRange = 0f..100f,
                onChange = {
                    fuel = it.toInt()
                    onFuel(fuel)
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        engineOn = !engineOn
                        rpm = if (engineOn) DEMO_IDLE_RPM.coerceAtMost(rpmMax) else 0
                        onRpm(rpm)
                        onEngineChanged(engineOn)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            if (engineOn) R.string.demo_stop_car else R.string.demo_start_car
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.demo_close))
                }
            }
        }
    }
}

/** One labeled slider row used by the demo sheet. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange
        )
    }
}
