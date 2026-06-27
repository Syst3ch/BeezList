package com.beezlist.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val BeezNavy = Color(0xFF101830)
val BeezNavyDark = Color(0xFF0A0F20)
val BeezYellow = Color(0xFFFFC400)
val BeezYellowDark = Color(0xFFE0A000)
val BeezBlack = Color(0xFF18140A)
val BeezSignal = Color(0xFF00E0C4)
val BeezWhite = Color(0xFFF5F7FA)

private val BeezListColorScheme = darkColorScheme(
    primary = BeezYellow,
    onPrimary = BeezBlack,
    secondary = BeezSignal,
    onSecondary = BeezBlack,
    background = BeezNavy,
    onBackground = BeezWhite,
    surface = BeezNavyDark,
    onSurface = BeezWhite,
)

@Composable
fun BeezListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeezListColorScheme,
        content = content,
    )
}
