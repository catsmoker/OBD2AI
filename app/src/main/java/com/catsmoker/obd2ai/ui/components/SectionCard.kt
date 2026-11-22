package com.catsmoker.obd2ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * The rounded surface every content card is built from. Cards are tonal, not
 * glassy: a scrolling list of refracted panels is noise. Glass is reserved
 * for floating chrome and hero surfaces.
 *
 * [enabled] dims the container instead of the content so an unavailable
 * control still reads as present and can explain itself.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: Dp = ObdSpacing.CardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ObdRadius.Card),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/** Stacked cards using the app-wide card gap. */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap),
        content = content
    )
}

/**
 * Section heading: a small accent bar plus a label. Never uppercased or
 * letter-spaced — that breaks Arabic joining and is a no-op for CJK.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Row(
        modifier = modifier.padding(start = 4.dp, bottom = ObdSpacing.LabelGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (accent) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A [SectionCard] whose body folds away behind its heading. Long settings
 * lists collapse to a handful of rows, and the chevron plus the card chrome
 * keep the rest discoverable; the body animates so the list does not jump.
 */
@Composable
fun CollapsibleSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    expandLabel: String? = null,
    collapseLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(modifier = modifier) {
        val chevron by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            label = "section-chevron"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ObdRadius.Inner))
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = if (expanded) collapseLabel else expandLabel,
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(vertical = ObdSpacing.LabelGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = ObdSpacing.LabelGap)) {
                content()
            }
        }
    }
}
