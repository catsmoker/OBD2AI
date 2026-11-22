package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.connect.ConnectFailureKind
import com.catsmoker.obd2ai.ui.theme.ObdRadius

/**
 * Compose replacement for the old ErrorDialogFragment: a titled error
 * message with a single dismiss button, driven by nullable state. Dialogs
 * live in their own window, so they use a frosted tonal container instead of
 * sampling the app backdrop.
 */
@Composable
fun ErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    // A transport failure gets a plain-language headline; anything else keeps
    // the neutral "Error" title rather than pretending to be a link problem.
    val kind = ConnectFailureKind.classify(message)
    val title = when (kind) {
        ConnectFailureKind.UNREACHABLE -> R.string.connect_failed_unreachable
        ConnectFailureKind.NOT_PAIRED -> R.string.connect_failed_not_paired
        ConnectFailureKind.BLUETOOTH_UNAVAILABLE -> R.string.connect_failed_bluetooth
        ConnectFailureKind.GENERIC -> R.string.dialog_error_title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(ObdRadius.Card),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.onboarding_button_skip))
            }
        }
    )
}
