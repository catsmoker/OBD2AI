package com.catsmoker.obd2ai.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.instruments.CoolantGaugeView
import com.catsmoker.obd2ai.instruments.FuelGaugeView
import com.catsmoker.obd2ai.instruments.VoltageGaugeView
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.BaseSpeedometerView
import com.catsmoker.obd2ai.speedometers.SpeedometerHost
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.github.anastr.speedviewlib.TubeSpeedometer

/**
 * Pure-Compose Live dashboard UI, rendered from [LiveSession] state. Visual
 * layout is unchanged from the previous fragment-hosted version.
 */
@Composable
internal fun LiveContent(session: LiveSession) {
    val speed = session.speedKmh.value
    val rpm = session.rpmReading.value
    val coolant = session.coolantC.value
    val volts = session.voltageV.value
    val fuel = session.fuelPct.value
    val load = session.loadPct.value
    val bannerState = session.banner.value
    val onlineText = session.onlineCardText.value
    val isMuted = session.muted.value
    val zone = session.rpmColor.value

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(session)
                    Spacer(modifier = Modifier.weight(1f))
                    if (session.demoMode) {
                        IconButton(onClick = { session.showDemoSheet() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tune),
                                contentDescription = stringResource(R.string.demo_controls_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { session.toggleMute() }) {
                        Icon(
                            painter = painterResource(
                                if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                            ),
                            contentDescription = stringResource(
                                if (isMuted) R.string.live_data_unmute else R.string.live_data_mute
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (session.gpsSpeedSource || session.speedometerStyle == SpeedometerStyle.AUTOMOTIVE) {
                    SpeedGauge(
                        session = session,
                        speedKmh = speed,
                        rpm = rpm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedGauge(
                            session = session,
                            speedKmh = speed,
                            rpm = rpm,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        TachoGauge(
                            session = session,
                            rpm = rpm,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValueTile(
                        label = stringResource(R.string.live_data_speed_label),
                        value = speedText(speed, session.imperial),
                        modifier = Modifier.weight(1f)
                    )
                    if (!session.gpsSpeedSource) {
                        ValueTile(
                            label = stringResource(R.string.live_data_load_label),
                            value = if (load != null) "$load %" else LiveLogic.UNKNOWN,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!session.gpsSpeedSource) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AndroidView(
                            factory = { ctx -> CoolantGaugeView(ctx) },
                            update = { gauge ->
                                gauge.setTemp(
                                    coolant,
                                    session.imperial,
                                    session.coolantThreshold,
                                    PrefsKeys.COOLANT_ALARM_C
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        AndroidView(
                            factory = { ctx -> VoltageGaugeView(ctx) },
                            update = { gauge -> gauge.setVoltage(volts) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        AndroidView(
                            factory = { ctx -> FuelGaugeView(ctx) },
                            update = { gauge -> gauge.setFuel(fuel) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                    }
                } else {
                    val temp = session.deviceTempC.value
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (temp != null) {
                            stringResource(R.string.device_temp, temp)
                        } else {
                            LiveLogic.UNKNOWN
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Cue cards are pinned above the bottom edge so a warning can never
        // scroll out of sight while driving.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onlineText != null) {
                OnlineAiCard(
                    text = onlineText,
                    seconds = session.onlineSeconds.value,
                    onSkip = { session.skipOnlineAi() }
                )
            }
            if (bannerState != null) {
                OfflineAiBannerCard(
                    banner = bannerState,
                    seconds = session.bannerSeconds.value,
                    onSkip = { session.skipOfflineAi() }
                )
            }
        }

        // Edge flash: bright on the edge change, then a faint reminder tint.
        // A permanent frame while cruising would be noise.
        val alarm = session.coolantAlarm.value
        val edgeColor = if (alarm) LiveLogic.RPM_RED else zone
        val peak = when {
            alarm -> 0.95f
            zone == LiveLogic.RPM_GREEN -> 0f
            zone == LiveLogic.RPM_AMBER -> 0.6f
            else -> 0.9f
        }
        val flash = remember { Animatable(0f) }
        LaunchedEffect(peak) {
            if (peak <= 0f) {
                flash.snapTo(0f)
            } else {
                flash.snapTo(peak)
                flash.animateTo(peak * 0.25f, tween(durationMillis = 450))
            }
        }
        if (flash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, Color(edgeColor).copy(alpha = flash.value))
            )
        }
    }
}

@Composable
private fun StatusPill(session: LiveSession) {
    val label = when {
        session.demoMode -> stringResource(R.string.error_overview_demo_badge)
        session.gpsSpeedSource -> stringResource(R.string.live_data_status_gps)
        else -> stringResource(R.string.live_data_status_obd)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun ValueTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OfflineAiBannerCard(
    banner: LiveBanner,
    seconds: Int,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.offline_ai_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = banner.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (banner.actionLabel != null && banner.action != null) {
                TextButton(
                    onClick = {
                        banner.action?.invoke()
                        onSkip()
                    }
                ) { Text(banner.actionLabel) }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = seconds.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.popup_skip))
            }
        }
    }
}

@Composable
private fun OnlineAiCard(text: String, seconds: Int, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.online_ai_card_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = seconds.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.popup_skip))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun SpeedGauge(
    session: LiveSession,
    speedKmh: Float?,
    rpm: Int?,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> SpeedometerHost.createView(ctx, session.speedometerStyle) },
        update = { view ->
            val displaySpeed = if (speedKmh != null) {
                Units.displaySpeed(speedKmh.toDouble(), session.imperial).toFloat()
            } else {
                0f
            }
            when (view) {
                is BaseSpeedometerView -> {
                    view.maxSpeed = Units.speedGaugeMax(session.imperial)
                    view.unitLabel = Units.speedUnitLabel(session.imperial)
                    view.rpmMax = session.rpmMax
                    view.setSpeed(displaySpeed, animate = speedKmh != null)
                    view.setRpm(rpm ?: 0, session.shiftRpm)
                }
                is android.widget.LinearLayout -> {
                    for (i in 0 until view.childCount) {
                        val tube = view.getChildAt(i) as? TubeSpeedometer ?: continue
                        if (i == 0) {
                            tube.maxSpeed = Units.speedGaugeMax(session.imperial)
                            tube.unit = Units.speedUnitLabel(session.imperial)
                            tube.speedTo(displaySpeed, 400)
                        } else {
                            tube.maxSpeed = session.rpmMax / 100f
                            tube.unit = "x100 RPM"
                            tube.speedTo((rpm ?: 0) / 100f, 400)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun TachoGauge(session: LiveSession, rpm: Int?, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> SpeedometerHost.createRpmView(ctx, session.speedometerStyle) },
        update = { gauge ->
            gauge.rpmMax = session.rpmMax
            gauge.shiftRpm = session.shiftRpm
            gauge.setRpm(rpm, session.shiftRpm)
        }
    )
}

private fun speedText(speed: Float?, imperial: Boolean): String {
    if (speed == null) return LiveLogic.UNKNOWN
    val value = Units.displaySpeed(speed.toDouble(), imperial)
    return String.format(
        java.util.Locale.US,
        "%.0f %s",
        value,
        Units.speedUnitLabel(imperial)
    )
}
