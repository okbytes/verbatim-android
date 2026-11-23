package app.verbatim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class VerbatimColors(
    val recite: Color,
    val onRecite: Color,
    val verbatim: Color,
    val missedWord: Color,
    val missedHighlight: Color,
    val extraWord: Color,
    val onExtraWord: Color,
    val readingSurface: Color,
    val groupedBackdrop: Color,
    val groupedCard: Color,
    val badgeContainer: Color,
    val badgeContent: Color,
    val dueTint: Color,
)

private val LightVerbatimColors =
    VerbatimColors(
        recite = Color(0xFFD93025),
        onRecite = Color(0xFFFFFFFF),
        verbatim = Color(0xFF1A803D),
        missedWord = Color(0xFF8C1212),
        missedHighlight = Color(0x248C1212),
        extraWord = Color(0xFF8F520F),
        onExtraWord = Color(0xFFFFFFFF),
        readingSurface = Color(0xFFFFFFFF),
        groupedBackdrop = Color(0xFFF3F4F4),
        groupedCard = Color(0xFFFFFFFF),
        badgeContainer = Color(0xFFD8EBE7),
        badgeContent = Color(0xFF125B57),
        dueTint = Color(0xFFD93025),
    )

private val DarkVerbatimColors =
    VerbatimColors(
        recite = Color(0xFFFF5C52),
        onRecite = Color(0xFF280504),
        verbatim = Color(0xFF6BDB8F),
        missedWord = Color(0xFFE66B63),
        missedHighlight = Color(0x2EE66B63),
        extraWord = Color(0xFFF2B057),
        onExtraWord = Color(0xFF141210),
        readingSurface = Color(0xFF121313),
        groupedBackdrop = Color(0xFF0D0F0F),
        groupedCard = Color(0xFF1A1D1D),
        badgeContainer = Color(0xFF214541),
        badgeContent = Color(0xFFB4EBE2),
        dueTint = Color(0xFFFF7A70),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF125B57),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD4EDE9),
        onPrimaryContainer = Color(0xFF0A3835),
        secondary = Color(0xFF4A6360),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E4),
        onSecondaryContainer = Color(0xFF051F1D),
        background = Color(0xFFF3F4F4),
        onBackground = Color(0xFF161A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF161A1A),
        surfaceVariant = Color(0xFFE7ECEB),
        onSurfaceVariant = Color(0xFF525D5C),
        outline = Color(0xFF707B7A),
        outlineVariant = Color(0xFFDCE2E1),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF8ED8CE),
        onPrimary = Color(0xFF0B3734),
        primaryContainer = Color(0xFF1A4E4A),
        onPrimaryContainer = Color(0xFFB7F0E8),
        secondary = Color(0xFFB1CCC8),
        onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF334B48),
        onSecondaryContainer = Color(0xFFCCE8E4),
        background = Color(0xFF0D0F0F),
        onBackground = Color(0xFFE6EAEA),
        surface = Color(0xFF121313),
        onSurface = Color(0xFFE6EAEA),
        surfaceVariant = Color(0xFF222727),
        onSurfaceVariant = Color(0xFFA7B3B1),
        outline = Color(0xFF788482),
        outlineVariant = Color(0xFF2B3131),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )

val LocalVerbatimColors = staticCompositionLocalOf { LightVerbatimColors }

object VerbatimTypography {
    val libraryWordmark =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.4).sp,
        )

    val entryLargeTitle =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 35.sp,
            letterSpacing = (-0.2).sp,
        )

    val entryBody =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 29.sp,
        )

    val metadata =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp,
        )

    val badge =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.2.sp,
        )
}

@Composable
fun VerbatimTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val verbatimColors = if (darkTheme) DarkVerbatimColors else LightVerbatimColors

    CompositionLocalProvider(LocalVerbatimColors provides verbatimColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
