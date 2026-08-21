package apincer.android.mmate.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import apincer.android.mmate.R

val NotoSansThai = FontFamily(
    Font(R.font.noto_sans_thai_regular, FontWeight.Normal),
    Font(R.font.noto_sans_thai_semi_bold, FontWeight.SemiBold),
    Font(R.font.noto_sans_thai_bold, FontWeight.Bold),
    Font(R.font.noto_sans_thai_thin, FontWeight.Thin)
)

private val defaultTypography = Typography()
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = NotoSansThai),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = NotoSansThai),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = NotoSansThai),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = NotoSansThai),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = NotoSansThai),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = NotoSansThai),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = NotoSansThai),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = NotoSansThai),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = NotoSansThai),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = NotoSansThai),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = NotoSansThai),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = NotoSansThai),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = NotoSansThai),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = NotoSansThai),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = NotoSansThai)
)

/** Shared dark colour scheme that matches the app's dark blurred background style. */
private val AppDarkColors = darkColorScheme(
    primary            = Color(0xFF80CBC4),   // teal accent
    onPrimary          = Color.Black,
    primaryContainer   = Color(0xFF1E3A39),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary          = Color(0xFFFFD700),   // gold
    onSecondary        = Color.Black,
    background         = Color(0xFF121212),
    onBackground       = Color.White,
    surface            = Color(0xFF1E1E1E),
    onSurface          = Color.White,
    surfaceVariant     = Color(0xFF2C2C2C),
    onSurfaceVariant   = Color(0xFFAAAAAA),
    outline            = Color(0xFF3A3A3A),
    error              = Color(0xFFEF5350),
    onError            = Color.White
)

/**
 * Wrap all Compose UI in this theme so that MaterialTheme.colorScheme.*
 * resolves to readable dark-on-dark values instead of the default light theme.
 */
@Composable
fun MusicMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        typography = AppTypography,
        content = content
    )
}
