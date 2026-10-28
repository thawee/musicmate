package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.Biquad
import apincer.android.mmate.audio.dsp.PeqBand
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sinh

/**
 * The car target (docs/technical/CAR_AUDIO_DSP.md, Q3): +5 dB low shelf at 100 Hz and a tilt from
 * 1 kHz down to -3 dB at 20 kHz. An assumption to confirm by listening, not a published curve.
 */
object CarTarget {
    private val shelf = Biquad.design(PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 5.0, 0.707), 48_000)

    @JvmStatic
    fun db(frequencyHz: Double): Double =
        shelf.magnitudeDb(frequencyHz, 48_000) + if (frequencyHz <= 1_000.0) 0.0 else -3.0 * ln(frequencyHz / 1_000.0) / ln(20.0)

    @JvmField
    val CURVE: DoubleArray = FrequencyGrid.HZ.map(::db).toDoubleArray()
}

/** Curves on [FrequencyGrid.HZ], all aligned to the target's level. */
class FitResult(
    val bands: List<PeqBand>,
    /** Corrected from here up to [PeqFitter.MAX_HZ]; below, the measurement was not reliable. */
    val fromHz: Double,
    val measuredDb: DoubleArray,
    val targetDb: DoubleArray,
    val predictedDb: DoubleArray,
    /** The target as far as the correction may go: boost caps, cuts-only bass and nulls applied. */
    val achievableDb: DoubleArray,
    /** Contaminated third-octave centres; correction and verification leave their margins alone. */
    val excludedBandsHz: List<Double> = emptyList()
)

class Verdict(val passed: Boolean, val worstDeviationDb: Double, val worstHz: Double)

/**
 * Fits the correction: at most 10 peaking bands, cuts to -10 dB, boosts to +3 dB, no boost at all
 * below 100 Hz (the phone mic is unreliable there, [boostCap]) and none into a narrow deep null. The limits
 * hold on the combined curve, not only at band centres.
 */
object PeqFitter {
    const val MIN_HZ = 30.0
    const val MAX_HZ = 8_000.0
    const val NO_BOOST_BELOW_HZ = 100.0
    const val MAX_BOOST_DB = 3.0
    const val MAX_CUT_DB = -10.0
    const val MAX_BANDS = 10
    const val SAMPLE_RATE = 48_000
    private const val STOP_DB = 1.0
    private const val NULL_DEPTH_DB = 6.0
    private const val NULL_MAX_POINTS = 16 // 1/3 octave on the 1/48-octave grid
    private const val NULL_MARGIN_POINTS = 4
    private const val VERIFY_FROM_HZ = 60.0
    private const val VERIFY_TOLERANCE_DB = 3.0

    private val grid = FrequencyGrid.HZ

    /** [fromHz] comes from [TakeAnalyzer.reliableFromHz]; nothing below it is corrected. */
    @JvmStatic
    @JvmOverloads
    fun fit(measuredDb: DoubleArray, fromHz: Double = MIN_HZ, excludedBandsHz: List<Double> = emptyList(), targetDb: DoubleArray = CarTarget.CURVE): FitResult {
        require(measuredDb.size == grid.size && measuredDb.all { it.isFinite() })
        require(targetDb.size == grid.size && targetDb.all { it.isFinite() })
        val excluded = BooleanArray(grid.size) { isExcluded(grid[it], excludedBandsHz) }
        val inRange = BooleanArray(grid.size) { grid[it] in maxOf(MIN_HZ, fromHz)..MAX_HZ && !excluded[it] }
        val target = targetDb.copyOf()
        val measured = align(measuredDb, target, excludedBandsHz)
        // Mask before null detection as well as fitting: contaminated depths convey no information.
        val error = DoubleArray(grid.size) { if (excluded[it]) 0.0 else target[it] - measured[it] }
        val boostCap = boostCaps(error)
        val wanted = DoubleArray(grid.size) { if (inRange[it]) error[it].coerceIn(MAX_CUT_DB, boostCap[it]) else 0.0 }

        val bands = mutableListOf<PeqBand>()
        val skipped = BooleanArray(grid.size)
        while (bands.size < MAX_BANDS) {
            val residual = minus(wanted, response(bands))
            var worst = -1
            for (g in grid.indices) {
                if (inRange[g] && !skipped[g] && (worst < 0 || abs(residual[g]) > abs(residual[worst]))) worst = g
            }
            if (worst < 0 || abs(residual[worst]) < STOP_DB) break
            val gain = residual[worst].coerceIn(MAX_CUT_DB, boostCap[worst])
            if (abs(gain) < STOP_DB) {
                skipped[worst] = true
                continue
            }
            bands += PeqBand(PeqBand.Type.PEAK, grid[worst], gain, qOf(residual, worst))
            refineGains(bands, wanted, boostCap, inRange, excluded)
        }
        enforceLimits(bands, excludedBandsHz)

        val correction = response(bands)
        return FitResult(
            bands = bands.toList(),
            fromHz = maxOf(MIN_HZ, fromHz),
            measuredDb = measured,
            targetDb = target,
            predictedDb = DoubleArray(grid.size) { measured[it] + correction[it] },
            achievableDb = DoubleArray(grid.size) { measured[it] + wanted[it] },
            excludedBandsHz = excludedBandsHz.toList()
        )
    }

    /** Checks a measurement taken with the correction applied against the achievable target. */
    @JvmStatic
    fun verify(fit: FitResult, take: Take.Accepted): Verdict? {
        val takes = listOf(take)
        if (TakeAnalyzer.unreliableBands(takes).any { it !in fit.excludedBandsHz } ||
            TakeAnalyzer.reliableFromHz(takes) > maxOf(VERIFY_FROM_HZ, fit.fromHz)) return null
        return verify(fit, take.responseDb)
    }

    /** Checks an already validated response; callers with SNR information should use the take overload. */
    @JvmStatic
    fun verify(fit: FitResult, correctedDb: DoubleArray): Verdict {
        val corrected = align(correctedDb, fit.targetDb, fit.excludedBandsHz)
        var worst = 0.0
        var worstHz = 0.0
        for (g in grid.indices) {
            if (grid[g] < maxOf(VERIFY_FROM_HZ, fit.fromHz) || grid[g] > MAX_HZ || isExcluded(grid[g], fit.excludedBandsHz)) continue
            val deviation = abs(corrected[g] - fit.achievableDb[g])
            if (deviation > worst) {
                worst = deviation
                worstHz = grid[g]
            }
        }
        return Verdict(worst <= VERIFY_TOLERANCE_DB, worst, worstHz)
    }

    /** Combined gain of [bands] in dB on the grid. */
    @JvmStatic
    fun response(bands: List<PeqBand>): DoubleArray {
        val filters = bands.map { Biquad.design(it, SAMPLE_RATE) }
        return DoubleArray(grid.size) { g -> filters.sumOf { it.magnitudeDb(grid[g], SAMPLE_RATE) } }
    }

    /** A third-octave band plus its smoothing margin occupies a quarter octave on either side. */
    @JvmStatic
    fun isExcluded(frequencyHz: Double, excludedBandsHz: List<Double>): Boolean =
        frequencyHz > 0.0 && excludedBandsHz.any { centre ->
            centre > 0.0 && abs(ln(frequencyHz / centre) / ln(2.0)) <= 0.25 + 1e-12
        }

    /** Aligns over reliable 200 Hz-2 kHz points, or the remaining fit range if all are masked. */
    @JvmStatic
    @JvmOverloads
    fun align(curve: DoubleArray, target: DoubleArray, excludedBandsHz: List<Double> = emptyList()): DoubleArray {
        val reliable = grid.indices.filter { !isExcluded(grid[it], excludedBandsHz) }
        val normal = reliable.filter { grid[it] in 200.0..2_000.0 }
        val points = normal.ifEmpty { reliable.filter { grid[it] in MIN_HZ..MAX_HZ } }
        val differences = points.map { target[it] - curve[it] }.sorted()
        val offset = if (differences.isEmpty()) 0.0 else differences[differences.size / 2]
        return DoubleArray(curve.size) { curve[it] + offset }
    }

    /**
     * The most boost allowed at [frequencyHz]: none below 100 Hz, rising to +3 dB at 200 Hz. The ramp
     * keeps a boost just above 100 Hz from spilling below it and gives the fit a continuous goal.
     */
    @JvmStatic
    fun boostCap(frequencyHz: Double): Double = when {
        frequencyHz < NO_BOOST_BELOW_HZ -> 0.0
        frequencyHz < 2 * NO_BOOST_BELOW_HZ -> MAX_BOOST_DB * ln(frequencyHz / NO_BOOST_BELOW_HZ) / ln(2.0)
        else -> MAX_BOOST_DB
    }

    /** [boostCap], and 0 in a narrow deep null (with a small margin). */
    private fun boostCaps(error: DoubleArray): DoubleArray {
        val caps = DoubleArray(grid.size) { boostCap(grid[it]) }
        var g = 0
        while (g < grid.size) {
            if (error[g] <= NULL_DEPTH_DB) {
                g++
                continue
            }
            val start = g
            while (g < grid.size && error[g] > NULL_DEPTH_DB) g++
            if (g - start < NULL_MAX_POINTS) {
                for (i in max(0, start - NULL_MARGIN_POINTS) until minOf(grid.size, g + NULL_MARGIN_POINTS)) caps[i] = 0.0
            }
        }
        return caps
    }

    /** Q from the width where the residual lobe stays above half its peak. */
    private fun qOf(residual: DoubleArray, centre: Int): Double {
        val half = residual[centre] / 2
        var left = centre
        while (left > 0 && residual[left - 1] * half > 0 && abs(residual[left - 1]) >= abs(half)) left--
        var right = centre
        while (right < grid.size - 1 && residual[right + 1] * half > 0 && abs(residual[right + 1]) >= abs(half)) right++
        val octaves = max(1, right - left).toDouble() / 48
        return (1 / (2 * sinh(ln(2.0) / 2 * octaves))).coerceIn(0.5, 6.0)
    }

    /** A few damped Gauss-Newton steps on the gains, centres and Q fixed, within each band's caps. */
    private fun refineGains(bands: MutableList<PeqBand>, wanted: DoubleArray, boostCap: DoubleArray, inRange: BooleanArray, excluded: BooleanArray) {
        val points = grid.indices.filter { inRange[it] || excluded[it] }
        repeat(6) {
            val residual = minus(wanted, response(bands))
            val n = bands.size
            val jacobian = Array(n) { b ->
                val step = 0.1
                val up = single(bands[b].copy(gainDb = bands[b].gainDb + step))
                val down = single(bands[b].copy(gainDb = bands[b].gainDb - step))
                DoubleArray(grid.size) { (up[it] - down[it]) / (2 * step) }
            }
            val normal = Array(n) { i -> DoubleArray(n) { j -> points.sumOf { jacobian[i][it] * jacobian[j][it] * if (excluded[it]) 8.0 else 1.0 } } }
            val rhs = DoubleArray(n) { i -> points.sumOf { jacobian[i][it] * residual[it] * if (excluded[it]) 8.0 else 1.0 } }
            for (i in 0 until n) normal[i][i] += 1e-3 * max(normal[i][i], 1e-6)
            val delta = solve(normal, rhs) ?: return
            for (b in 0 until n) {
                val centre = grid.indices.minBy { abs(grid[it] - bands[b].frequencyHz) }
                bands[b] = bands[b].copy(gainDb = (bands[b].gainDb + delta[b]).coerceIn(MAX_CUT_DB, boostCap[centre]))
            }
        }
    }

    /** Enforces boost caps and protected-band limits on combined tails, including mask edges. */
    private fun enforceLimits(bands: MutableList<PeqBand>, excludedBandsHz: List<Double>) {
        val frequencies = grid.toList() + excludedBandsHz.flatMap { centre ->
            // Finer than the measurement grid, with exact protected-region boundaries.
            (-48..48).map { centre * 2.0.pow(it / 192.0) }
        }
        val excluded = BooleanArray(frequencies.size) { isExcluded(frequencies[it], excludedBandsHz) }
        repeat(if (excludedBandsHz.isEmpty()) 200 else 400) {
            val filters = bands.map { Biquad.design(it, SAMPLE_RATE) }
            val contributions = filters.map { filter -> DoubleArray(frequencies.size) { filter.magnitudeDb(frequencies[it], SAMPLE_RATE) } }
            val combined = DoubleArray(frequencies.size) { g -> contributions.sumOf { it[g] } }
            var worst = -1
            var excess = 0.0
            var direction = 1.0
            for (g in frequencies.indices) {
                val overBoost = combined[g] - boostCap(frequencies[g])
                val overProtected = if (excluded[g]) abs(combined[g]) - 0.98 else 0.0
                val over = max(overBoost, overProtected)
                if (over > excess + 1e-3) {
                    excess = over
                    worst = g
                    direction = if (overProtected > overBoost && combined[g] < 0.0) -1.0 else 1.0
                }
            }
            if (worst < 0) return
            var shrunk = false
            for (b in bands.indices) {
                if (contributions[b][worst] * direction > 0.01) {
                    bands[b] = bands[b].copy(gainDb = bands[b].gainDb * 0.9)
                    shrunk = true
                }
            }
            if (!shrunk) return
        }
    }

    private fun single(band: PeqBand) = response(listOf(band))

    private fun minus(a: DoubleArray, b: DoubleArray) = DoubleArray(a.size) { a[it] - b[it] }

    /** Gaussian elimination with partial pivoting; null if singular. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { a[it].copyOf() }
        val x = b.copyOf()
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(m[it][col]) }
            if (abs(m[pivot][col]) < 1e-12) return null
            m[col] = m[pivot].also { m[pivot] = m[col] }
            x[col] = x[pivot].also { x[pivot] = x[col] }
            for (row in col + 1 until n) {
                val factor = m[row][col] / m[col][col]
                for (k in col until n) m[row][k] -= factor * m[col][k]
                x[row] -= factor * x[col]
            }
        }
        for (row in n - 1 downTo 0) {
            var sum = x[row]
            for (k in row + 1 until n) sum -= m[row][k] * x[k]
            x[row] = sum / m[row][row]
        }
        return x
    }
}
