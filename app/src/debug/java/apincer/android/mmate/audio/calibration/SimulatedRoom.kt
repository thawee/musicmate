package apincer.android.mmate.audio.calibration

import android.content.Context
import apincer.android.mmate.audio.dsp.Biquad
import apincer.android.mmate.audio.dsp.PeqBand
import java.io.File
import java.util.Random

/**
 * Debug builds only: stands in for the microphone so the calibration screens can be walked on an
 * emulator, which cannot hear its own speaker. Turn it on with
 * `adb shell run-as apincer.android.mmate touch files/simulated_room` and remove the file to record
 * for real. Each side plays through a made-up car: a bass boom, a mid dip and a treble roll-off,
 * with the right side 2 dB quieter and 1.5 ms later. Release builds have a version that returns null.
 */
object SimulatedRoom {
    private class Side(val bands: List<PeqBand>, val gain: Double, val lateSamples: Int)

    private val left = Side(listOf(
        PeqBand(PeqBand.Type.PEAK, 55.0, 7.0, 1.4), PeqBand(PeqBand.Type.PEAK, 400.0, 3.0, 3.0),
        PeqBand(PeqBand.Type.PEAK, 2_500.0, -5.0, 2.0), PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, -4.0, 0.7)), 0.4, 0)
    private val right = Side(listOf(
        PeqBand(PeqBand.Type.PEAK, 70.0, 5.0, 1.2), PeqBand(PeqBand.Type.PEAK, 1_200.0, -4.0, 2.5),
        PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, -6.0, 0.7)), 0.4 * 0.794, 72)

    fun capture(context: Context, pcm: ShortArray, channels: Int): SweepRecorder.Capture? {
        if (!File(context.filesDir, "simulated_room").exists()) return null
        val rate = SweepRecorder.RATE
        val lead = (rate * SweepRecorder.LEAD_SECONDS).toInt()
        val frames = pcm.size / channels
        val random = Random(7)
        val recording = DoubleArray(lead + frames + (rate * SweepRecorder.TAIL_SECONDS).toInt()) { random.nextGaussian() * 1e-4 }
        listOf(left, right).take(channels).forEachIndexed { channel, side ->
            var signal = DoubleArray(frames) { pcm[it * channels + channel] / 32_767.0 }
            side.bands.forEach { signal = filter(Biquad.design(it, rate), signal) }
            for (n in 0 until frames) recording[lead + side.lateSamples + n] += signal[n] * side.gain
        }
        Thread.sleep(1_000)
        return SweepRecorder.Capture(recording, null)
    }

    private fun filter(q: Biquad, x: DoubleArray): DoubleArray {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        return DoubleArray(x.size) { n ->
            val y = q.b0 * x[n] + q.b1 * x1 + q.b2 * x2 - q.a1 * y1 - q.a2 * y2
            x2 = x1; x1 = x[n]; y2 = y1; y1 = y
            y
        }
    }
}
