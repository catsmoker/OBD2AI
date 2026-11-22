package com.catsmoker.obd2ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdMotion
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * A tappable tile: icon badge, title, and one supporting line.
 *
 * Two layouts, one signature. [isFullWidth] lays it out as a list row; the
 * default is a grid tile whose subtitle reserves two lines so neighbouring
 * tiles keep the same height. The tile springs slightly under the finger,
 * which makes a grid of cards feel like physical keys.
 */
@Composable
fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    iconContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    iconContentColor: Color = MaterialTheme.colorScheme.primary
) {
    val clickable = enabled && !isLoading
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && clickable) 0.975f else 1f,
        animationSpec = ObdMotion.Press,
        label = "tilePress"
    )

    Card(
        onClick = onClick,
        enabled = clickable,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(ObdRadius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DotGridBackground(
                modifier = Modifier.matchParentSize(),
                alpha = 0.035f
            )

            if (isFullWidth) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconBadge(
                        icon = icon,
                        containerColor = iconContainerColor,
                        contentColor = iconContentColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(
                            icon = icon,
                            containerColor = iconContainerColor,
                            contentColor = iconContentColor
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        minLines = 1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(ObdRadius.Inner))
            .background(
                Brush.verticalGradient(
                    listOf(containerColor, containerColor.copy(alpha = 0.5f))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        contentColor.copy(alpha = 0.45f),
                        contentColor.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(ObdRadius.Inner)
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Preview(name = "Tile light")
@Composable
private fun QuickActionTileLightPreview() {
    Obd2AiTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(ObdSpacing.ScreenHorizontal)) {
            QuickActionTile(
                title = "Live data",
                subtitle = "Speed, RPM, coolant and more",
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = {}
            )
        }
    }
}

@Preview(name = "Tile dark")
@Composable
private fun QuickActionTileDarkPreview() {
    Obd2AiTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(ObdSpacing.ScreenHorizontal)) {
            QuickActionTile(
                title = "Live data",
                subtitle = "Speed, RPM, coolant and more",
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = {}
            )
        }
    }
}
