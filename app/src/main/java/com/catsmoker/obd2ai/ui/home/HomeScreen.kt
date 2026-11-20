package com.catsmoker.obd2ai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * Welcome screen and state-aware main menu.
 *
 * Disconnected: connection actions plus Settings/About. Connected or demo:
 * the four data screens become available and the connection actions are
 * replaced by Disconnect/Exit demo — no action ever appears twice.
 */
@Composable
fun HomeScreen(
    state: ConnectionState,
    onConnect: () -> Unit,
    onTryDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenTrip: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linked = state != ConnectionState.DISCONNECTED
    val haptic = LocalHapticFeedback.current
    val tap: (() -> Unit) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        action()
    }

    val columns = LocalContext.current.resources.getInteger(R.integer.home_grid_columns)
    val tiles = buildList {
        if (linked) {
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
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onSurface
            )
            StatusBadge(
                label = when (state) {
                    ConnectionState.DEMO -> stringResource(R.string.error_overview_demo_badge)
                    ConnectionState.CONNECTED -> stringResource(R.string.home_status_connected)
                    ConnectionState.DISCONNECTED -> stringResource(R.string.home_status_offline)
                },
                active = linked
            )
            // Settings lives here, not in the tile grid: one canonical path
            // (this gear), so no tile duplicates it.
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

        SectionCard {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(ObdSpacing.LabelGap))
                Text(
                    text = stringResource(R.string.home_nav_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(ObdSpacing.SectionGap))

        // Tiles exist only while linked; disconnected Home is just the
        // connection card, so hide the empty section entirely.
        if (tiles.isNotEmpty()) {
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
                        // Keep the last, incomplete row from stretching a lone tile.
                        repeat(columns - rowTiles.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))
        }
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
