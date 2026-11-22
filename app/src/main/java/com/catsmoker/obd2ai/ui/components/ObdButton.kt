package com.catsmoker.obd2ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.ui.theme.ObdMotion
import com.catsmoker.obd2ai.ui.theme.PressPink

/**
 * App-wide filled button: pill-shaped, comfortable height, and physical on
 * press — it scales down slightly (spring) and tints while the finger is
 * down. Disabled buttons never tint: a greyed-out control must keep reading
 * as unavailable.
 */
@Composable
fun ObdButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = ObdMotion.Press,
        label = "buttonPress"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        shape = shape,
        colors = if (isPressed && enabled) colors.copy(containerColor = PressPink) else colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/** Outlined counterpart with the same press contract. */
@Composable
fun ObdOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = ObdMotion.Press,
        label = "outlinedPress"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        shape = shape,
        colors = if (isPressed && enabled) colors.copy(containerColor = PressPink) else colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
