package apincer.android.mmate.audio.calibration

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 FFT. Twiddles come from a table, not a recurrence, so a 2^20 transform stays accurate. */
internal class Fft(val size: Int) {
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two: $size" }
        cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
        sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    }

    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, -1.0)

    /** Inverse transform, scaled by 1/size. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, 1.0)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, sign: Double) {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sign * sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += length
            }
            length = length shl 1
        }
    }
}
