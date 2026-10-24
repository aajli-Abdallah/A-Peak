package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun toDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
fun dayStart(d: LocalDate): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun todayStart(): Long = dayStart(LocalDate.now())

/** The Peak-A logo: an A whose crossbar is three rising bars. */
@Composable
fun ApeakMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary,
              accent: Color = MaterialTheme.colorScheme.tertiary) {
    Canvas(modifier) {
        val s = size.minDimension
        fun p(v: Float) = v / 100f * s
        val path = Path().apply { moveTo(p(20f), p(88f)); lineTo(p(50f), p(14f)); lineTo(p(80f), p(88f)) }
        drawPath(path, color, style = Stroke(width = p(8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawRect(accent, Offset(p(38f), p(72f)), Size(p(6f), p(8f)))
        drawRect(accent, Offset(p(47f), p(66f)), Size(p(6f), p(14f)))
        drawRect(accent, Offset(p(56f), p(60f)), Size(p(6f), p(20f)))
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ico.Back, onBack)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ToggleRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val m = Modifier.weight(1f)
            if (i == selected) Button(onClick = { onSelect(i) }, modifier = m) { Text(o, maxLines = 1) }
            else OutlinedButton(onClick = { onSelect(i) }, modifier = m) { Text(o, maxLines = 1) }
        }
    }
}

@Composable
fun PickerField(label: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}
