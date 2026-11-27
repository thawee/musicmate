package apincer.android.mmate.audio.calibration

import android.content.Context

/** Release builds always record from the real microphone. */
object SimulatedRoom {
    @Suppress("UNUSED_PARAMETER")
    fun capture(context: Context, pcm: ShortArray, channels: Int): SweepRecorder.Capture? = null
}
