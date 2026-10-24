package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ico { Overview, Bank, Stocks, Calendar, Settings, Back, Edit, Delete, Add, Minus, Up, Down, Percent, Coin, Close, Prev, Next, Refresh, Chart, List }

/** Outline icons drawn on a 24x24 grid. They take the current content color by default. */
@Composable
fun AppIcon(ico: Ico, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val sw = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, o(x1, y1), o(x2, y2), 2f * k, StrokeCap.Round)
        fun poly(vararg p: Float, closed: Boolean = false) {
            val path = Path()
            path.moveTo(p[0] * k, p[1] * k)
            var i = 2
            while (i < p.size) { path.lineTo(p[i] * k, p[i + 1] * k); i += 2 }
            if (closed) path.close()
            drawPath(path, tint, style = sw)
        }
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
            drawRoundRect(tint, o(x, y), Size(w * k, h * k), CornerRadius(r * k), style = sw)
        fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * k, o(cx, cy), style = sw)
        when (ico) {
            Ico.Overview -> { box(3.5f, 3.5f, 7f, 7f); box(13.5f, 3.5f, 7f, 4f); box(13.5f, 10.5f, 7f, 10f); box(3.5f, 13.5f, 7f, 7f) }
            Ico.Bank -> {
                poly(3f, 10f, 12f, 4f, 21f, 10f, closed = true)
                line(6.5f, 12.5f, 6.5f, 17.5f); line(10.2f, 12.5f, 10.2f, 17.5f)
                line(13.8f, 12.5f, 13.8f, 17.5f); line(17.5f, 12.5f, 17.5f, 17.5f)
                line(3.5f, 20.5f, 20.5f, 20.5f)
            }
            Ico.Stocks -> { poly(3f, 17f, 9f, 11f, 13f, 15f, 20.5f, 6f); poly(15.5f, 6f, 20.5f, 6f, 20.5f, 11f) }
            Ico.Calendar -> { box(3.5f, 5f, 17f, 15.5f, 3f); line(3.5f, 10f, 20.5f, 10f); line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f) }
            Ico.Settings -> {
                for ((y, kx) in listOf(6f to 9f, 12f to 15f, 18f to 8f)) {
                    line(4f, y, kx - 2.8f, y); line(kx + 2.8f, y, 20f, y); ring(kx, y, 2f)
                }
            }
            Ico.Back -> { line(19f, 12f, 5f, 12f); poly(11f, 6f, 5f, 12f, 11f, 18f) }
            Ico.Edit -> { poly(4f, 20f, 5f, 15f, 15.5f, 4.5f, 19.5f, 8.5f, 9f, 19f, closed = true); line(13f, 7f, 17f, 11f) }
            Ico.Delete -> {
                line(4f, 7f, 20f, 7f); poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                poly(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f); line(10f, 11f, 10f, 16f); line(14f, 11f, 14f, 16f)
            }
            Ico.Add -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Ico.Minus -> line(5f, 12f, 19f, 12f)
            Ico.Up -> { line(12f, 19f, 12f, 5f); poly(6f, 11f, 12f, 5f, 18f, 11f) }
            Ico.Down -> { line(12f, 5f, 12f, 19f); poly(6f, 13f, 12f, 19f, 18f, 13f) }
            Ico.Percent -> { ring(7f, 7f, 2.5f); ring(17f, 17f, 2.5f); line(18f, 5f, 6f, 19f) }
            Ico.Coin -> { ring(12f, 12f, 9f); ring(12f, 12f, 4.5f) }
            Ico.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ico.Prev -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            Ico.Next -> poly(9f, 5f, 16f, 12f, 9f, 19f)
            Ico.Refresh -> { drawArc(tint, 40f, 270f, false, o(4f, 4f), Size(16f * k, 16f * k), style = sw); poly(16.5f, 2f, 17.1f, 5.9f, 21f, 5.2f) }
            Ico.Chart -> { box(5f, 12f, 3.5f, 8f, 1f); box(10.5f, 7f, 3.5f, 13f, 1f); box(16f, 3.5f, 3.5f, 16.5f, 1f) }
            Ico.List -> { line(9f, 6f, 20f, 6f); line(9f, 12f, 20f, 12f); line(9f, 18f, 20f, 18f); ring(5f, 6f, 0.8f); ring(5f, 12f, 0.8f); ring(5f, 18f, 0.8f) }
        }
    }
}

@Composable
fun IconBtn(ico: Ico, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick) { AppIcon(ico, Modifier.size(22.dp), tint) }
}

@Composable
fun IconTile(ico: Ico, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        AppIcon(ico, Modifier.size(size * 0.52f), tint)
    }
}

/** Navy gradient card with a faint mountain shape. Content is white. */
@Composable
fun HeroCard(content: @Composable BoxScope.() -> Unit) {
    val brush = Brush.linearGradient(listOf(Color(0xFF0B1F3A), Color(0xFF1D4ED8)))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(brush)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val a = Path().apply { moveTo(w * 0.55f, h); lineTo(w * 0.78f, h * 0.2f); lineTo(w, h); close() }
            drawPath(a, Color.White.copy(alpha = 0.07f))
            val b = Path().apply { moveTo(w * 0.38f, h); lineTo(w * 0.6f, h * 0.5f); lineTo(w * 0.82f, h); close() }
            drawPath(b, Color.White.copy(alpha = 0.05f))
        }
        CompositionLocalProvider(LocalContentColor provides Color.White, LocalIsDark provides true) { content() }
    }
}
