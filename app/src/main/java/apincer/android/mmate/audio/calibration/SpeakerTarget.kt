package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.Biquad
import apincer.android.mmate.audio.dsp.PeqBand

/** What a speaker tuning aims for. [id] is stored in [CarProfile.targetId]; never rename one. */
enum class SpeakerTarget(val id: String, val label: String, val curve: DoubleArray) {
    CAR("car", "Car", CarTarget.CURVE),

    /**
     * Olive, Welti and McMullin (AES convention paper 8994, 2013): listeners preferred a flat
     * in-room response with +6.6 dB of bass below 105 Hz and -2.4 dB of treble above 2.5 kHz. The
     * gains come from summaries of the paper (only its abstract is public); the shelf Qs are the
     * Harman 2015 filter convention.
     */
    ROOM("room", "Room", shelves(
        PeqBand(PeqBand.Type.LOW_SHELF, 105.0, 6.6, 0.71),
        PeqBand(PeqBand.Type.HIGH_SHELF, 2_500.0, -2.4, 0.4))),

    FLAT("flat", "Flat", DoubleArray(FrequencyGrid.HZ.size));

    companion object {
        /** Profiles saved before targets could be chosen aimed for [CAR]. */
        @JvmStatic
        fun of(id: String?): SpeakerTarget = entries.firstOrNull { it.id == id } ?: CAR
    }
}

private fun shelves(vararg bands: PeqBand): DoubleArray {
    val filters = bands.map { Biquad.design(it, 48_000) }
    return FrequencyGrid.HZ.map { hz -> filters.sumOf { it.magnitudeDb(hz, 48_000) } }.toDoubleArray()
}
