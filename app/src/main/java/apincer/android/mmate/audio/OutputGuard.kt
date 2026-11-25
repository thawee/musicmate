package apincer.android.mmate.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10

/** When the safety limiter last pulled the output down, and when a sample last hit full scale; 0 is never. */
data class OutputGuardState(val limitedAtMs: Long = 0L, val clippedAtMs: Long = 0L,
                            val reductionDb: Double = 0.0)

/**
 * What the processed output had to do to stay within full scale, for a clip light. The limiter
 * counts from 1 dB of reduction. The live lookahead limiter includes a conservative true-peak
 * allowance, so full-scale material can light this indicator even without EQ boost. A clip is a sample
 * clamped at full scale. ReplayGain uses the limiter when clipping protection is enabled.
 */
object OutputGuard {
    private val _state = MutableStateFlow(OutputGuardState())
    val state: StateFlow<OutputGuardState> = _state.asStateFlow()

    /** From the audio thread after each processed block, with the block's lowest limiter gain. */
    fun report(lowestLimiterGain: Double, clipped: Boolean) {
        val limited = lowestLimiterGain < LIMITING_GAIN
        if (!limited && !clipped) return
        val now = System.currentTimeMillis()
        val last = _state.value
        val next = OutputGuardState(
            if (limited && now - last.limitedAtMs >= THROTTLE_MS) now else last.limitedAtMs,
            if (clipped && now - last.clippedAtMs >= THROTTLE_MS) now else last.clippedAtMs,
            if (limited && now - last.limitedAtMs >= THROTTLE_MS)
                -20.0 * log10(lowestLimiterGain.coerceIn(0.000001, 1.0)) else last.reductionDb
        )
        if (next != last) _state.value = next
    }

    fun reset() {
        _state.value = OutputGuardState()
    }

    /** -1 dB. */
    private const val LIMITING_GAIN = 0.891251
    private const val THROTTLE_MS = 250L
}
