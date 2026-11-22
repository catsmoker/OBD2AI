package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.connect.ConnectFailureKind
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * One transport failure, described the same way everywhere it surfaces.
 *
 * The headline comes from [ConnectFailureKind] so Connect, Live, Diagnostics
 * and Trip all name the same cause; the raw exception text is kept as the
 * small second line instead of being the whole message. [genericHeadline]
 * supplies screen-specific wording for the one case classification cannot
 * explain (the link died for a reason the message does not reveal).
 */
@Composable
fun TransportFailureNotice(
    detail: String?,
    genericHeadline: Int,
    modifier: Modifier = Modifier,
    actionLabel: Int? = null,
    onAction: (() -> Unit)? = null
) {
    val kind = ConnectFailureKind.classify(detail.orEmpty())
    val headline = when (kind) {
        ConnectFailureKind.UNREACHABLE -> R.string.connect_failed_unreachable
        ConnectFailureKind.NOT_PAIRED -> R.string.connect_failed_not_paired
        ConnectFailureKind.BLUETOOTH_UNAVAILABLE -> R.string.connect_failed_bluetooth
        ConnectFailureKind.GENERIC -> genericHeadline
    }
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Spacer(Modifier.size(ObdSpacing.CardGap))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    text = stringResource(headline),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(10.dp))
                    ObdOutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(actionLabel))
                    }
                }
            }
        }
    }
}
