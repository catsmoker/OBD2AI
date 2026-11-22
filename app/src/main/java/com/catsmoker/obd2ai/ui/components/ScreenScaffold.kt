package com.catsmoker.obd2ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdMotion
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * Header-plus-content frame for secondary screens.
 *
 * A large title anchors the page; the back control is a soft tonal circle
 * rather than a bare icon, so the target is obvious and comfortably tappable.
 * The back button is nullable: root destinations have nowhere to go back to,
 * and an inert arrow is worse than none. Insets are handled once by the app
 * root, not here.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ObdSpacing.ScreenHorizontal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ObdSpacing.HeaderVertical, bottom = ObdSpacing.HeaderVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back_desc),
                    onClick = onBack
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingContent != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailingContent
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

/**
 * Round tonal icon button used across screens (back, close, overflow).
 * Press feedback is a small spring scale, so the control answers the finger
 * on devices where ripples are easy to miss.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    CircleIconButtonShell(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Painter overload for the app's own drawables (volume, tune, ...). */
@Composable
fun CircleIconButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    CircleIconButtonShell(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CircleIconButtonShell(
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ObdMotion.Press,
        label = "circlePress"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interaction
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview(name = "Scaffold light")
@Composable
private fun ScreenScaffoldLightPreview() {
    Obd2AiTheme(darkTheme = false) {
        ScreenScaffold(title = "Settings", subtitle = "Preferences", onBack = {}) {
            Box(Modifier.fillMaxSize())
        }
    }
}
