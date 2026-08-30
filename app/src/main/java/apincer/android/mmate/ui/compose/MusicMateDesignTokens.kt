package apincer.android.mmate.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MusicMate Centralized Design Tokens
 * Defines official chromatic hierarchy, audiophile semantics, surfaces, and typography specs.
 */
object MusicMateDesignTokens {

    /**
     * Surface & Glassmorphic Elevation Palette (Obsidian Base)
     */
    object Surfaces {
        val Canvas = Color(0xFF121212)
        val Surface = Color(0xFF1E1E1E)
        val SurfaceElevated = Color(0xFF2C2C2C)
        val GlassCard = Color(0xD9101010)
        val GlassPill = Color(0xD9141414)
        val BorderPrimary = Color(0x33FFFFFF)
        val BorderSubtle = Color(0x1AFFFFFF)
        val BorderElevated = Color(0x4DFFFFFF)
    }

    /**
     * Brand & Interactive Accents (Warm Gold & Acoustic Amber)
     */
    object Brand {
        val Gold = Color(0xFFFFD700)
        val GoldAmber = Color(0xFFFFB300)
        val WarmAmber = Color(0xFFF57C00)
        val AcousticTeal = Color(0xFF80CBC4)
        val TealContainer = Color(0xFF1E3A39)
        val TealLight = Color(0xFFB2DFDB)
    }

    /**
     * Semantic Audio Provenance & Stream Quality Palette
     */
    object AudioProvenance {
        val DsdCyan = Color(0xFF00E5FF)
        val HiResGold = Color(0xFFFFD700)
        val Studio24Bit = Color(0xFFFFD54F)
        val MqaMagenta = Color(0xFFE040FB)
        val MqaStudio = Color(0xFF42A5F5)
        val CdSkyBlue = Color(0xFF64B5F6)
        val LossySlate = Color(0xFF9E9E9E)
        val BitPerfectGreen = Color(0xFF00E676)
    }

    /**
     * Dynamic Range (DR) Acoustic Meter Palette (Temperature Spectrum)
     */
    object DynamicRange {
        val RedCompressed = Color(0xFFFF3300) // DR 1-4
        val OrangeWarning = Color(0xFFFF9900)  // DR 5-8
        val AmberStandard = Color(0xFFFFCC00)  // DR 9-12
        val SkyBlueHigh = Color(0xFF33CCFF)    // DR 13-16
        val RoyalBlueDynamic = Color(0xFF3366FF) // DR 17-20
        val PurpleAudiophile = Color(0xFF9933CC) // DR 21+
    }

    /**
     * Text & Typography High-Contrast Scale
     */
    object Text {
        val Primary = Color(0xFFFFFFFF)
        val Secondary = Color(0xFFAAAAAA)
        val Muted = Color(0xFF757575)
        val Telemetry = Color(0xFFCCCCCC)
    }

    /**
     * Elevation & Radii Tokens
     */
    object Geometry {
        val RadiusSmall = 6.dp
        val RadiusMedium = 10.dp
        val RadiusLarge = 16.dp
        val RadiusPill = 999.dp
        val BorderHairline = 0.5.dp
        val BorderStandard = 0.75.dp
        val BorderFocus = 1.25.dp
    }
}
