package apincer.android.mmate.audio.dsp

import kotlinx.serialization.Serializable

/**
 * Shared listening modes layered over speaker or headphone correction: at most
 * three filters and +4 dB per mode. The automatic preamp absorbs every boost. Only [NIGHT]
 * compresses to reduce louder passages.
 */
@Serializable
enum class SoundMode(val label: String, val summary: String, val compresses: Boolean = false) {
    // Keep the stored name for existing car preferences.
    REFERENCE("Neutral", "The correction alone"),
    BASS("Bass", "Deeper, punchier low end"),
    VOCAL("Vocal", "Voices forward and clear"),
    LIVELY("Lively", "Tight bass, crisp attack, sparkle"),
    RELAX("Relax", "Softer highs, warm and easy"),
    NIGHT("Night", "Soft and even, loud moments tamed", compresses = true),
    CUSTOM("Custom", "Your own bass and treble");

    /** Bands this mode adds; [bassDb] and [trebleDb] apply to [CUSTOM] only. */
    fun bands(bassDb: Double = 0.0, trebleDb: Double = 0.0): List<PeqBand> = when (this) {
        REFERENCE -> emptyList()
        BASS -> listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 80.0, 4.0, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 250.0, -1.0, 1.0)
        )
        VOCAL -> listOf(
            PeqBand(PeqBand.Type.PEAK, 2_500.0, 2.5, 1.0),
            PeqBand(PeqBand.Type.LOW_SHELF, 120.0, -1.5, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 300.0, -1.0, 1.0)
        )
        LIVELY -> listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 70.0, 3.0, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 3_000.0, 1.5, 1.0),
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 2.0, SHELF_Q)
        )
        RELAX -> listOf(
            PeqBand(PeqBand.Type.HIGH_SHELF, 6_000.0, -2.5, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 3_000.0, -1.5, 1.0),
            PeqBand(PeqBand.Type.LOW_SHELF, 150.0, 1.5, SHELF_Q)
        )
        NIGHT -> listOf(
            PeqBand(PeqBand.Type.HIGH_SHELF, 4_000.0, -4.0, SHELF_Q),
            PeqBand(PeqBand.Type.LOW_SHELF, 60.0, -2.0, SHELF_Q)
        )
        CUSTOM -> listOfNotNull(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, bassDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { bassDb != 0.0 },
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, trebleDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { trebleDb != 0.0 }
        )
    }

    companion object {
        const val TRIM_DB = 4.0
        private const val SHELF_Q = 0.707
    }
}
