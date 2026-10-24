package com.aajli.apeak.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Navy = Color(0xFF0B1F3A)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE7FF), onPrimaryContainer = Navy,
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5F0), onSecondaryContainer = Navy,
    tertiary = Color(0xFF38BDF8), onTertiary = Navy,
    background = Color(0xFFEAF0F8), onBackground = Navy,
    surface = Color.White, onSurface = Navy,
    surfaceVariant = Color(0xFFE2E8F2), onSurfaceVariant = Color(0xFF5B6B82),
    surfaceContainer = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFF94A3B8), error = Color(0xFFDC2626)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA8FF), onPrimary = Color(0xFF08142B),
    primaryContainer = Color(0xFF1B3A73), onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF9AA8BD), onSecondary = Color(0xFF0B1426),
    secondaryContainer = Color(0xFF22324D), onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFF67D4FF), onTertiary = Color(0xFF08142B),
    background = Color(0xFF08111F), onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF0E1A2E), onSurface = Color(0xFFE6ECF7),
    surfaceVariant = Color(0xFF1B2A44), onSurfaceVariant = Color(0xFF9FB0C8),
    surfaceContainer = Color(0xFF0E1A2E), surfaceContainerHighest = Color(0xFF14233B),
    outline = Color(0xFF52627C), error = Color(0xFFF87171)
)
private val AppShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))

val LocalIsDark = staticCompositionLocalOf { false }
@Composable fun incomeColor() = if (LocalIsDark.current) Color(0xFF4ADE80) else Color(0xFF15803D)
@Composable fun expenseColor() = if (LocalIsDark.current) Color(0xFFF87171) else Color(0xFFDC2626)

@Composable
fun APeakTheme(theme: String, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
