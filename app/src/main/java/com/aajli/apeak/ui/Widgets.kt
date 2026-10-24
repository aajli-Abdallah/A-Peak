package com.aajli.apeak.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aajli.apeak.R
import com.aajli.apeak.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FMT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
fun fmtDate(ms: Long): String = toDate(ms).format(DATE_FMT)
fun signed(v: Long, cur: String): String = (if (v > 0) "+" else "") + Money.format(v, cur)
@Composable fun gainColor(v: Long): Color = if (v >= 0) incomeColor() else expenseColor()

@Composable
fun TextF(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(60)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(14)) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DateButton(label: String, millis: Long, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    val d = toDate(millis)
    OutlinedButton(
        onClick = {
            DatePickerDialog(ctx, { _, y, m, day -> onChange(dayStart(LocalDate.of(y, m + 1, day))) },
                d.year, d.monthValue - 1, d.dayOfMonth).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("$label: ${fmtDate(millis)}") }
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null,
               content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete_title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun KeyVal(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalContentColor.current.copy(alpha = 0.72f))
        Text(value, color = color, fontWeight = FontWeight.Medium)
    }
}

val Palette = listOf(Color(0xFF1D4ED8), Color(0xFF38BDF8), Color(0xFF475569), Color(0xFF0B1F3A), Color(0xFF818CF8), Color(0xFF94A3B8))
val LightPalette = listOf(Color(0xFF93C5FD), Color(0xFFE2E8F0), Color(0xFF38BDF8), Color(0xFF94A3B8), Color(0xFFA5B4FC), Color(0xFFCBD5E1))

@Composable
fun StackedBar(parts: List<Pair<String, Long>>, cur: String) {
    val total = parts.sumOf { maxOf(it.second, 0L) }
    if (total <= 0) return
    val pal = if (LocalIsDark.current) LightPalette else Palette
    Canvas(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
        var x = 0f
        parts.forEachIndexed { i, p ->
            val w = size.width * (maxOf(p.second, 0L).toFloat() / total)
            drawRect(pal[i % pal.size], Offset(x, 0f), Size(w, size.height)); x += w
        }
    }
    parts.forEachIndexed { i, p ->
        if (p.second > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(pal[i % pal.size], CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("${p.first}  ${Money.format(p.second, cur)}  (${p.second * 100 / total}%)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LineChart(values: List<Long>) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val mn = values.min(); val mx = values.max()
        val range = (mx - mn).coerceAtLeast(1L).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * 0.95f - size.height * 0.9f * ((v - mn).toFloat() / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
