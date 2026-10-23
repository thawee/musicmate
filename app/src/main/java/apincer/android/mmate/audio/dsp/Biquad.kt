package apincer.android.mmate.audio.dsp

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One parametric EQ band. Shelves use [q] as the shelf Q; 0.707 gives the classic slope. */
@Serializable
data class PeqBand(val type: Type, val frequencyHz: Double, val gainDb: Double, val q: Double) {
    enum class Type { PEAK, LOW_SHELF, HIGH_SHELF }
}

/**
 * A biquad from the RBJ Audio EQ Cookbook, normalised so a0 = 1. Coefficients stay in double:
 * low-frequency filters at 192 kHz are not stable in float.
 */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Gain in dB at [frequencyHz] for a filter running at [sampleRate]. */
    fun magnitudeDb(frequencyHz: Double, sampleRate: Int): Double {
        val w = 2 * PI * frequencyHz / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = 1 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        return 10 * log10((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
    }

    companion object {
        @JvmStatic
        fun design(band: PeqBand, sampleRate: Int): Biquad {
            val a = 10.0.pow(band.gainDb / 40)
            val w0 = 2 * PI * band.frequencyHz / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2 * band.q)
            return when (band.type) {
                PeqBand.Type.PEAK -> normalise(
                    1 + alpha * a, -2 * cosW, 1 - alpha * a,
                    1 + alpha / a, -2 * cosW, 1 - alpha / a
                )
                PeqBand.Type.LOW_SHELF -> {
                    val k = 2 * sqrt(a) * alpha
                    normalise(
                        a * ((a + 1) - (a - 1) * cosW + k), 2 * a * ((a - 1) - (a + 1) * cosW),
                        a * ((a + 1) - (a - 1) * cosW - k),
                        (a + 1) + (a - 1) * cosW + k, -2 * ((a - 1) + (a + 1) * cosW),
                        (a + 1) + (a - 1) * cosW - k
                    )
                }
                PeqBand.Type.HIGH_SHELF -> {
                    val k = 2 * sqrt(a) * alpha
                    normalise(
                        a * ((a + 1) + (a - 1) * cosW + k), -2 * a * ((a - 1) + (a + 1) * cosW),
                        a * ((a + 1) + (a - 1) * cosW - k),
                        (a + 1) - (a - 1) * cosW + k, 2 * ((a - 1) - (a + 1) * cosW),
                        (a + 1) - (a - 1) * cosW - k
                    )
                }
            }
        }

        private fun normalise(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
