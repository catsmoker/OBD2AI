package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * Bottom sheet for the phone "More" section (Console, Settings, About).
 * Replaces the PopupMenu for better accessibility, keyboard navigation,
 * and Material 3 compliance.
 */
@Composable
fun MoreBottomSheet(
    onDismiss: () -> Unit,
    onConsoleClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(ObdSpacing.ScreenHorizontal, ObdSpacing.CardGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dock_more),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(ObdSpacing.CardGap))

        // Console
        MoreSheetItem(
            icon = Icons.Outlined.Terminal,
            label = stringResource(R.string.obd_console_title),
            onClick = { onConsoleClick(); onDismiss() }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

        // Settings
        MoreSheetItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.settings_title),
            onClick = { onSettingsClick(); onDismiss() }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

        // About
        MoreSheetItem(
            icon = Icons.Outlined.Info,
            label = stringResource(R.string.settings_about_title),
            onClick = { onAboutClick(); onDismiss() }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(ObdSpacing.CardGap))
    }
}

@Composable
private fun MoreSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = ObdSpacing.ScreenHorizontal)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Preview for the MoreBottomSheet component.
 */
@androidx.compose.ui.tooling.preview.Preview(name = "MoreBottomSheet Light")
@Composable
private fun MoreBottomSheetLightPreview() {
    Obd2AiTheme(darkTheme = false) {
        MoreBottomSheet(
            onDismiss = {},
            onConsoleClick = {},
            onSettingsClick = {},
            onAboutClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "MoreBottomSheet Dark")
@Composable
private fun MoreBottomSheetDarkPreview() {
    Obd2AiTheme(darkTheme = true) {
        MoreBottomSheet(
            onDismiss = {},
            onConsoleClick = {},
            onSettingsClick = {},
            onAboutClick = {}
        )
    }
}