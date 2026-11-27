package apincer.android.mmate.audio.calibration

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/** A voice effect a phone may attach to a recording session. */
internal class MicEffect(val name: String, val isAvailable: () -> Boolean, val create: (Int) -> AudioEffect?)

internal val MIC_EFFECTS = listOf(
    MicEffect("AGC", AutomaticGainControl::isAvailable, AutomaticGainControl::create),
    MicEffect("NS", NoiseSuppressor::isAvailable, NoiseSuppressor::create),
    MicEffect("AEC", AcousticEchoCanceler::isAvailable, AcousticEchoCanceler::create)
)

/**
 * Turns off each of [effects] the phone offers on [session], which some phones leave on even for
 * an unprocessed source. The effects stay disabled only while held: release every one returned.
 */
internal fun disableMicEffects(session: Int, effects: List<MicEffect> = MIC_EFFECTS): List<AudioEffect> {
    val held = mutableListOf<AudioEffect>()
    val report = effects.joinToString { effect ->
        val status = try {
            if (!effect.isAvailable()) "not available"
            else when (val created = effect.create(session)) {
                null -> "not created"
                else -> {
                    held += created
                    if (created.setEnabled(false) == AudioEffect.SUCCESS) "disabled" else "could not be disabled"
                }
            }
        } catch (failure: Exception) {
            "failed (${failure.message})"
        }
        "${effect.name} $status"
    }
    Log.i(TAG, "mic processing: $report")
    return held
}

private const val TAG = "CarCalibration"
