package com.catsmoker.obd2ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.ui.theme.PanelInk
import com.catsmoker.obd2ai.ui.theme.PanelPaper
import com.catsmoker.obd2ai.ui.theme.Phosphor


@Composable
fun InstrumentPanel(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(PanelInk, RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(16.dp, 4.dp).background(Phosphor))
            Spacer(Modifier.width(8.dp))
            Text(label, color = PanelPaper, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Engraved 16-pin connector symbol; decorative, never a connection indicator. */
@Composable
fun PortDrawing(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * .06f, h * .18f); lineTo(w * .94f, h * .18f)
            lineTo(w * .83f, h * .82f); lineTo(w * .17f, h * .82f); close()
        }
        drawPath(path, Phosphor, style = Stroke(2.dp.toPx()))
        repeat(2) { row -> repeat(8) { pin ->
            drawRect(PanelPaper, Offset(w * (.19f + pin * .084f), h * (.34f + row * .19f)), Size(w * .036f, h * .09f))
        } }
    }
}
