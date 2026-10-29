package apincer.android.mmate.audio.calibration

/** Keeps level retries from sending the listener back and forth between volume settings. */
internal class CalibrationTakeGuidance {
    private var clipped = false
    private var noiseLimited = false

    fun reset() {
        clipped = false
        noiseLimited = false
    }

    fun advice(take: Take.Rejected): String {
        when (take.reason) {
            Take.Reason.CLIPPED -> clipped = true
            Take.Reason.TOO_QUIET -> noiseLimited = true
            else -> Unit
        }
        if (clipped && noiseLimited && (take.reason == Take.Reason.CLIPPED || take.reason == Take.Reason.TOO_QUIET)) {
            return "${take.detail}. Recent attempts detected both recording overload and low signal-to-noise. " +
                "Keep the lower volume setting. Reduce room and handling noise, hold the microphone still " +
                "and check its placement before retrying."
        }
        return when (take.reason) {
            Take.Reason.CLIPPED -> "The recording overloaded the microphone. Hold the microphone still and measure again; if it clips again, turn the volume down a little."
            Take.Reason.TOO_QUIET -> "${take.detail}. Reduce room and handling noise (in a car, the engine and fan), keep the microphone still, and measure again."
            Take.Reason.NO_RESPONSE -> "${take.detail}. Check the volume and that the phone is playing through the speaker."
            Take.Reason.TOO_SHORT -> "${take.detail}. Measure again; if it repeats, reconnect Android Auto."
            Take.Reason.CAPTURE -> take.detail
        }
    }
}
