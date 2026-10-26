package com.ydh.salvio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** 사용자가 선택할 수 있는 테마 모드. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private fun materialSchemeFrom(c: SalvioColors) = if (c.isLight) {
    lightColorScheme(
        primary = c.accent,
        onPrimary = Color.White,
        secondary = c.success,
        tertiary = c.done,
        background = c.canvas,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceHover,
        onSurfaceVariant = c.textSecondary,
        outline = c.border,
        outlineVariant = c.borderMuted,
        error = c.danger,
        onError = Color.White
    )
} else {
    darkColorScheme(
        primary = c.accent,
        onPrimary = c.canvas,
        secondary = c.success,
        tertiary = c.done,
        background = c.canvas,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceHover,
        onSurfaceVariant = c.textSecondary,
        outline = c.border,
        outlineVariant = c.borderMuted,
        error = c.danger,
        onError = c.text
    )
}

@Composable
fun SalvioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalSalvioColors provides colors) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(colors),
            typography = Typography,
            content = content
        )
    }
}

/** 화면에서 시맨틱 색 토큰에 접근하는 진입점: `SalvioTheme.colors.text` */
object SalvioTheme {
    val colors: SalvioColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSalvioColors.current
}
