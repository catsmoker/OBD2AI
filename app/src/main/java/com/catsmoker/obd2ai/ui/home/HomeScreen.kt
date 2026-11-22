package com.catsmoker.obd2ai.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.common.ConnectionState
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.QuickActionTile
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.glass.GlassCard
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdMotion
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * Welcome screen and state-aware main menu — the hub of the app.
 *
 * The connection card is the hero: a liquid-glass panel over the ambient
 * wash, carrying the state, the explanation and the one action that matters.
 * Feature tiles appear only while linked, so a disconnected Home never shows
 * doors that open onto an empty room.
 */
@Composable
fun HomeScreen(
    state: ConnectionState,
    onConnect: () -> Unit,
    onTryDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSavedDiagnostics: () -> Unit = onOpenDiagnostics,
    onOpenTrip: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    showTiles: Boolean = true,
    lossReason: String? = null,
    hasSavedScan: Boolean = false,
    modifier: Modifier = Modifier
) {
    val linked = state != ConnectionState.DISCONNECTED
    val haptic = LocalHapticFeedback.current
    val tap: (() -> Unit) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        action()
    }

    val columns = LocalContext.current.resources.getInteger(R.integer.home_grid_columns)
    val savedDiagnosticsOnly = HomeTiles.showSavedDiagnostics(state, hasSavedScan)
    val tiles = buildList {
        if (!showTiles && savedDiagnosticsOnly) {
            // Offline review of the last scan: a document, not a live reading.
            add(
                HomeTile(
                    title = stringResource(R.string.home_card_saved_scan_title),
                    subtitle = stringResource(R.string.home_card_saved_scan_desc),
                    icon = Icons.Outlined.CarRepair,
                    onClick = onOpenSavedDiagnostics
                )
            )
        }
        if (showTiles) {
            add(
                HomeTile(
                    title = stringResource(R.string.dashboard_title),
                    subtitle = stringResource(R.string.home_card_dashboard_desc),
                    icon = Icons.Outlined.Speed,
                    onClick = onOpenDashboard
                )
            )
            add(
                HomeTile(
                    title = stringResource(R.string.error_overview_title),
                    subtitle = stringResource(R.string.home_card_diagnostics_desc),
                    icon = Icons.Outlined.CarRepair,
                    onClick = onOpenDiagnostics
                )
            )
            add(
                HomeTile(
                    title = stringResource(R.string.trip_title),
                    subtitle = stringResource(R.string.home_card_trip_desc),
                    icon = Icons.Outlined.Route,
                    onClick = onOpenTrip
                )
            )
            add(
                HomeTile(
                    title = stringResource(R.string.obd_console_title),
                    subtitle = stringResource(R.string.home_card_console_desc),
                    icon = Icons.Outlined.Terminal,
                    onClick = onOpenConsole
                )
            )
        }
        // About lives in the header (info icon next to the settings gear),
        // not in the tile grid — one consistent pattern for both secondary
        // destinations, and the connected grid stays a full 2×2.
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ObdSpacing.ScreenHorizontal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = ObdSpacing.SectionGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    label = when (state) {
                        ConnectionState.DEMO -> stringResource(R.string.error_overview_demo_badge)
                        ConnectionState.CONNECTED -> stringResource(R.string.home_status_connected)
                        ConnectionState.DISCONNECTED -> stringResource(R.string.home_status_offline)
                    },
                    active = linked
                )
                Spacer(Modifier.padding(horizontal = 2.dp))
                // Settings and About live here, not in the tile grid: one
                // canonical path each, so no tile duplicates them.
                IconButton(onClick = { tap(onOpenAbout) }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.settings_about_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { tap(onOpenSettings) }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Hero: liquid glass over the ambient wash. Dot grid keeps the
        // instrument texture without competing with the text.
        GlassCard(
            shape = RoundedCornerShape(ObdRadius.Card),
            blurPx = 18.dp,
            refraction = 20.dp,
            elevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.onboarding_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(22.dp))

                if (linked) {
                    ObdOutlinedButton(
                        onClick = { tap(onDisconnect) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                if (state == ConnectionState.DEMO) R.string.menu_exit_demo else R.string.menu_disconnect
                            )
                        )
                    }
                } else {
                    if (lossReason != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LinkOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                // The adapter's own words: a diagnostic app
                                // should not paraphrase a broken pipe.
                                text = stringResource(R.string.home_connection_lost, lossReason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    ObdButton(
                        onClick = { tap(onConnect) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.connect_title))
                    }
                    Spacer(Modifier.height(ObdSpacing.CardGap))
                    ObdOutlinedButton(
                        onClick = { tap(onTryDemo) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.onboarding_try_demo))
                    }
                }
            }
        }

        // Tiles exist only while linked; disconnected Home is just the
        // connection hero, so the empty section is never shown.
        AnimatedVisibility(
            visible = tiles.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 }),
            exit = fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(ObdSpacing.SectionGap))
                SectionLabel(
                    text = stringResource(R.string.home_section_title),
                    accent = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)) {
                    tiles.chunked(columns).forEach { rowTiles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)) {
                            rowTiles.forEach { tile ->
                                QuickActionTile(
                                    title = tile.title,
                                    subtitle = tile.subtitle,
                                    icon = tile.icon,
                                    onClick = { tap(tile.onClick) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Keep a lone tile in the last row from stretching.
                            repeat(columns - rowTiles.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ObdSpacing.SectionGap))
    }
}

private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Preview(name = "Home offline light", showBackground = true)
@Composable
private fun HomeOfflineLightPreview() {
    Obd2AiTheme(darkTheme = false) {
        HomeScreen(
            state = ConnectionState.DISCONNECTED,
            onConnect = {},
            onTryDemo = {},
            onDisconnect = {},
            onOpenDashboard = {},
            onOpenDiagnostics = {},
            onOpenTrip = {},
            onOpenConsole = {},
            onOpenSettings = {},
            onOpenAbout = {}
        )
    }
}

@Preview(name = "Home connected dark", showBackground = true)
@Composable
private fun HomeConnectedDarkPreview() {
    Obd2AiTheme(darkTheme = true) {
        HomeScreen(
            state = ConnectionState.CONNECTED,
            onConnect = {},
            onTryDemo = {},
            onDisconnect = {},
            onOpenDashboard = {},
            onOpenDiagnostics = {},
            onOpenTrip = {},
            onOpenConsole = {},
            onOpenSettings = {},
            onOpenAbout = {}
        )
    }
}
