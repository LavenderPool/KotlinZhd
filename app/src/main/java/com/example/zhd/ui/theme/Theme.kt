package com.example.zhd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StationColors = lightColorScheme(
    primary = Color(0xFF14645E), onPrimary = Color.White,
    secondary = Color(0xFF5C7169), background = Color(0xFFF5F7F4),
    surface = Color.White, onSurface = Color(0xFF1D3139),
    outline = Color(0xFFACBDB6)
)
@Composable fun ZhdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StationColors, typography = Typography, content = content)
}
