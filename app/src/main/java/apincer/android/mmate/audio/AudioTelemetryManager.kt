package apincer.android.mmate.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time audio levels data packet for stereo telemetry.
 */
data class AudioLevels(
    val levelL: Float = 0f,
    val levelR: Float = 0f,
    val peakL: Float = 0f,
    val peakR: Float = 0f,
    val isRealtime: Boolean = false,
    val timestampMs: Long = 0L
)

/**
 * High-performance, thread-safe telemetry hub delivering decoded PCM levels to UI widgets.
 */
object AudioTelemetryManager {
    private val _levels = MutableStateFlow(AudioLevels())
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()

    @Volatile
    private var lastUpdateMs = 0L

    fun updateLevels(normL: Float, normR: Float, peakNormL: Float, peakNormR: Float) {
        val now = System.currentTimeMillis()
        // Throttle updates to ~60fps (16ms) to avoid saturating UI coroutines
        if (now - lastUpdateMs >= 16) {
            lastUpdateMs = now
            _levels.value = AudioLevels(
                levelL = normL,
                levelR = normR,
                peakL = peakNormL,
                peakR = peakNormR,
                isRealtime = true,
                timestampMs = now
            )
        }
    }

    fun reset() {
        _levels.value = AudioLevels(isRealtime = false)
    }
}
