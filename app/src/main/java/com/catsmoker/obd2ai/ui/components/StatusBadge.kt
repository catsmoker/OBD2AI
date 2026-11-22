package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.ui.theme.NothingRed
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme

/**
 * Small state chip: a dot plus a label on a soft tonal pill. [active] keeps
 * the dot neutral when a capability is unavailable, while the label still
 * says what the state is — color is never the only signal.
 */
@Composable
fun StatusBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = NothingRed
) {
    Row(
        modifier = modifier
            .background(
                color = if (active) {
                    activeColor.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Badges light")
@Composable
private fun StatusBadgeLightPreview() {
    Obd2AiTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBadge(label = "Connected", active = true)
            StatusBadge(label = "Offline", active = false)
        }
    }
}
