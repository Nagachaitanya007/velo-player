package app.lumen.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.lumen.player.R

val Ink = Color(0xFF07080A)
val InkRaised = Color(0xFF14161C)
val Cream = Color(0xFFF3EFE6)
val CreamDim = Color(0xFFA39E93)
val Tungsten = Color(0xFFE4A15A)
val Hairline = Color(0x22F3EFE6)

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(
    resId = R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Outfit = FontFamily(
    outfit(420),
    outfit(520),
    outfit(620),
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 34.sp, lineHeight = 38.sp, color = Cream),
    titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 22.sp, color = Cream),
    titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(520), fontSize = 16.sp, color = Cream),
    bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(420), fontSize = 16.sp, lineHeight = 22.sp, color = CreamDim),
    bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(420), fontSize = 14.sp, color = Cream),
    labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 15.sp, color = Ink),
    labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight(520), fontSize = 13.sp, color = Cream),
)

private val Colors = darkColorScheme(
    primary = Tungsten,
    onPrimary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = InkRaised,
    onSurface = Cream,
    surfaceContainer = Color(0xFF1C1F27),
    outline = Hairline,
)

@Composable
fun VeloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

fun formatTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0) / 1000
    val s = safe % 60
    val m = (safe / 60) % 60
    val h = safe / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
