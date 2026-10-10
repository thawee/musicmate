package apincer.android.mmate.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Computes stereo RMS and peak levels for the VU meters from the PCM that reaches the audio
 * output, so 16-bit and high-resolution float playback are both measured.
 */
object AudioLevelMeter {

    /** Measures bytes [from, to) of [buffer] without changing its position. */
    fun measure(buffer: ByteBuffer, from: Int, to: Int, encoding: Int, channelCount: Int) {
        if (channelCount <= 0 || to <= from) return

        val limit = to
        var pos = from

        var sumSqL = 0.0
        var sumSqR = 0.0
        var maxL = 0f
        var maxR = 0f
        var count = 0

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = 2 * channelCount
                val step = if (limit - pos > 2048) 2 else 1
                val stepBytes = frameBytes * step

                while (pos + frameBytes <= limit) {
                    val sL = buffer.getShort(pos) / 32768f
                    val sR = if (channelCount >= 2) buffer.getShort(pos + 2) / 32768f else sL

                    val absL = kotlin.math.abs(sL)
                    val absR = kotlin.math.abs(sR)
                    if (absL > maxL) maxL = absL
                    if (absR > maxR) maxR = absR

                    sumSqL += (sL * sL)
                    sumSqR += (sR * sR)
                    count++

                    pos += stepBytes
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = 4 * channelCount
                val step = if (limit - pos > 2048) 2 else 1
                val stepBytes = frameBytes * step

                while (pos + frameBytes <= limit) {
                    val sL = buffer.getFloat(pos)
                    val sR = if (channelCount >= 2) buffer.getFloat(pos + 4) else sL

                    val absL = kotlin.math.abs(sL)
                    val absR = kotlin.math.abs(sR)
                    if (absL > maxL) maxL = absL
                    if (absR > maxR) maxR = absR

                    sumSqL += (sL * sL)
                    sumSqR += (sR * sR)
                    count++

                    pos += stepBytes
                }
            }
            C.ENCODING_PCM_24BIT -> {
                val frameBytes = 3 * channelCount
                val step = if (limit - pos > 2048) 2 else 1
                val stepBytes = frameBytes * step

                while (pos + frameBytes <= limit) {
                    val b0L = buffer.get(pos).toInt() and 0xFF
                    val b1L = buffer.get(pos + 1).toInt() and 0xFF
                    val b2L = buffer.get(pos + 2).toInt() // signed high byte
                    val sample24L = (b2L shl 16) or (b1L shl 8) or b0L
                    val sL = sample24L / 8388608f

                    val sR = if (channelCount >= 2) {
                        val b0R = buffer.get(pos + 3).toInt() and 0xFF
                        val b1R = buffer.get(pos + 4).toInt() and 0xFF
                        val b2R = buffer.get(pos + 5).toInt()
                        val sample24R = (b2R shl 16) or (b1R shl 8) or b0R
                        sample24R / 8388608f
                    } else sL

                    val absL = kotlin.math.abs(sL)
                    val absR = kotlin.math.abs(sR)
                    if (absL > maxL) maxL = absL
                    if (absR > maxR) maxR = absR

                    sumSqL += (sL * sL)
                    sumSqR += (sR * sR)
                    count++

                    pos += stepBytes
                }
            }
        }

        if (count > 0) {
            val rmsL = sqrt(sumSqL / count).toFloat()
            val rmsR = sqrt(sumSqR / count).toFloat()

            val normL = dbToVuNorm(rmsToDb(rmsL))
            val normR = dbToVuNorm(rmsToDb(rmsR))
            val peakNormL = dbToVuNorm(rmsToDb(maxL))
            val peakNormR = dbToVuNorm(rmsToDb(maxR))

            AudioTelemetryManager.updateLevels(normL, normR, peakNormL, peakNormR)
        }
    }

    fun rmsToDb(rms: Float): Float {
        return if (rms <= 0.00001f) -100f else (20f * log10(rms.toDouble())).toFloat()
    }

    /**
     * Calibrates digital decibels (dBFS) to Analog VU Meter normalized scale [0.0f..1.0f].
     * Standard studio calibration:
     * - 0 VU = -14 dBFS (norm = 0.76f)
     * - -20 dB VU = -34 dBFS (norm = 0.0f)
     * - -10 dB VU = -24 dBFS (norm = 0.23f)
     * - -3 dB VU = -17 dBFS (norm = 0.57f)
     * - +3 dB VU = -11 dBFS (norm = 1.00f)
     */
    fun dbToVuNorm(dbFs: Float): Float {
        return when {
            dbFs <= -34f -> 0f
            dbFs <= -24f -> ((dbFs + 34f) / 10f) * 0.23f
            dbFs <= -17f -> 0.23f + ((dbFs + 24f) / 7f) * (0.57f - 0.23f)
            dbFs <= -14f -> 0.57f + ((dbFs + 17f) / 3f) * (0.76f - 0.57f)
            dbFs <= -11f -> 0.76f + ((dbFs + 14f) / 3f) * (1.00f - 0.76f)
            else -> (1.00f + ((dbFs + 11f) / 6f) * 0.05f).coerceAtMost(1.05f)
        }
    }
}
