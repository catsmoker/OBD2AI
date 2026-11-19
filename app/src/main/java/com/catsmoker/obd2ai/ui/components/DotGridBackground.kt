package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp

/**
 * The faint dot texture behind quick-action tiles. One 10 dp tile is rendered
 * once and repeated by a shader instead of issuing a draw call per dot.
 */
@Composable
fun DotGridBackground(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    Spacer(
        modifier = modifier.drawWithCache {
            val step = 10.dp.toPx()
            val dotRadius = 1.dp.toPx()

            val tile = ImageBitmap(step.toInt(), step.toInt())
            val canvas = Canvas(tile)
            val paint = Paint().apply { color = dotColor }
            canvas.drawCircle(
                center = Offset(step / 2f, step / 2f),
                radius = dotRadius,
                paint = paint
            )

            val brush = ShaderBrush(
                ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
            )
            onDrawBehind { drawRect(brush) }
        }
    )
}
