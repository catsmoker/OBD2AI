package com.catsmoker.obd2ai.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.catsmoker.obd2ai.R

/**
 * Compose replacement for the old ErrorDialogFragment: a titled error
 * message with a single dismiss button, driven by nullable state.
 */
@Composable
fun ErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_error_title)) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.onboarding_button_skip))
            }
        }
    )
}
