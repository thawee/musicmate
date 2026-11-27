package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.StereoCorrection
import kotlin.math.abs

/** Each side's own fit, and the correction a profile saves for two-channel tracks. */
class StereoFit(val left: FitResult, val right: FitResult, val correction: StereoCorrection) {
    /** Checks each side against its own fit; the worse side speaks for both. */
    fun verify(original: Sides, corrected: Sides): Verdict? {
        val l = PeqFitter.verify(left, original.left, corrected.left) ?: return null
        val r = PeqFitter.verify(right, original.right, corrected.right) ?: return null
        val worse = if (l.worstDeviationDb >= r.worstDeviationDb) l else r
        return Verdict(l.passed && r.passed, worse.worstDeviationDb, worse.worstHz, l.partial || r.partial)
    }

    companion object {
        /** More than this between the sides points at the mic or a fault, not the speakers. */
        const val MAX_BALANCE_DB = 6.0
        /** About 2 m of path difference; a larger gap is a reflection read as the direct sound. */
        const val MAX_DELAY_MS = 6.0

        /**
         * Fits each side's spots with [fit], levels the louder side down to the quieter one, and
         * delays the side heard first at the centre spot, which is [spots]' first.
         */
        fun of(spots: List<Sides>, weights: List<Double>, excludedBandsHz: List<Double>, fit: (List<DoubleArray>) -> FitResult): StereoFit {
            val left = spots.map { it.left.responseDb }
            val right = spots.map { it.right.responseDb }
            val (leftDelay, rightDelay) = delaysMs(spots.first())
            val leftFit = fit(left)
            val rightFit = fit(right)
            return StereoFit(leftFit, rightFit, StereoCorrection(leftFit.bands, rightFit.bands, leftDelay, rightDelay,
                balanceDb(TakeAnalyzer.powerAverage(left, weights), TakeAnalyzer.powerAverage(right, weights), excludedBandsHz)))
        }

        /** Positive when the left side is louder; the chain then attenuates the left. */
        fun balanceDb(leftDb: DoubleArray, rightDb: DoubleArray, excludedBandsHz: List<Double> = emptyList()): Double {
            val grid = FrequencyGrid.HZ
            val differences = grid.indices
                .filter { grid[it] in 200.0..2_000.0 && !PeqFitter.isExcluded(grid[it], excludedBandsHz) }
                .map { leftDb[it] - rightDb[it] }.sorted()
            return if (differences.isEmpty()) 0.0 else differences[differences.size / 2].coerceIn(-MAX_BALANCE_DB, MAX_BALANCE_DB)
        }

        /** The delay in ms for the left and the right side, so both arrive together at [centre]. */
        fun delaysMs(centre: Sides): Pair<Double, Double> {
            val gapMs = (centre.right.arrivalSeconds - centre.left.arrivalSeconds) * 1_000
            return when {
                abs(gapMs) > MAX_DELAY_MS -> 0.0 to 0.0
                gapMs > 0 -> gapMs to 0.0
                else -> 0.0 to -gapMs
            }
        }
    }
}
