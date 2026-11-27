package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.DspChain
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A speaker spot plays the left sweep, then the right one a single recording's length later, in
 * one stream. Both sides then share the route's latency, so their arrival times compare, and each
 * half of the recording reads like a recording of one sweep.
 */
class StereoSweep(private val sweep: LogSweep) {
    /** One single-sweep recording; the recorder adds the same lead and tail around the stream. */
    private val half = ((SweepRecorder.LEAD_SECONDS + SweepRecorder.TAIL_SECONDS) * sweep.sampleRate).roundToInt() + sweep.length
    /** Lets a delayed side's last samples leave the chain, so its fade-out is not cut. */
    private val drain = ceil(DspChain.MAX_DELAY_MS / 1_000 * sweep.sampleRate).toInt()

    /** Interleaved left and right; [process] changes each frame in place, as the correction does. */
    fun pcm(process: (DoubleArray) -> Unit = {}): ShortArray {
        val frames = half + sweep.length + drain
        val stream = DoubleArray(frames * 2)
        val frame = DoubleArray(2)
        for (n in 0 until frames) {
            frame[0] = if (n < sweep.length) sweep.samples[n] else 0.0
            frame[1] = if (n - half in 0 until sweep.length) sweep.samples[n - half] else 0.0
            process(frame)
            stream[2 * n] = frame[0]
            stream[2 * n + 1] = frame[1]
        }
        return sweep.toPcm16(stream)
    }

    /**
     * Accepts the spot only when both sides are usable, so a retry measures both again. A side
     * that makes no sound at all leaves the other side's take, without [Take.Accepted.sides].
     */
    fun analyze(analyzer: TakeAnalyzer, recording: DoubleArray, micDb: DoubleArray? = null): Take {
        if (recording.size < 2 * half) return Take.Rejected(Take.Reason.TOO_SHORT, "The recording stopped early")
        val left = analyzer.analyze(recording.copyOfRange(0, half), micDb)
        val right = analyzer.analyze(recording.copyOfRange(half, 2 * half), micDb)
        fun silent(take: Take) = take is Take.Rejected && take.reason == Take.Reason.NO_RESPONSE
        return when {
            left is Take.Accepted && right is Take.Accepted -> Take.Accepted(
                TakeAnalyzer.powerAverage(listOf(left.responseDb, right.responseDb)),
                DoubleArray(left.bandSnrDb.size) { min(left.bandSnrDb[it], right.bandSnrDb[it]) },
                min(left.delaySeconds, right.delaySeconds), min(left.arrivalSeconds, right.arrivalSeconds),
                Sides(left, right))
            left is Take.Accepted && silent(right) -> left
            right is Take.Accepted && silent(left) -> right
            // A clipped or noisy side needs other advice than a silent one, so it speaks first
            else -> listOf(left, right).filterIsInstance<Take.Rejected>().let { rejected ->
                rejected.firstOrNull { it.reason != Take.Reason.NO_RESPONSE } ?: rejected.first()
            }
        }
    }
}
