package apincer.android.mmate.audio.dsp

import kotlinx.serialization.Serializable

/**
 * A speaker pair measured one side at a time. It replaces the combined correction only on
 * two-channel tracks; [settings] hands that combined correction to every other layout.
 */
@Serializable
data class StereoCorrection(
    val leftBands: List<PeqBand>,
    val rightBands: List<PeqBand>,
    /** At most one is non-zero: the side whose sound arrives first waits for the other. */
    val leftDelayMs: Double = 0.0,
    val rightDelayMs: Double = 0.0,
    /** Positive lowers the left side, negative the right; never a boost. */
    val balanceDb: Double = 0.0
) {
    fun settings(combined: List<PeqBand>) = CarDspSettings(emptyList(), leftBands = leftBands, rightBands = rightBands,
        balanceDb = balanceDb, monoBands = combined, leftDelayMs = leftDelayMs, rightDelayMs = rightDelayMs)
}
