package apincer.android.mmate.audio.dsp

/** Manual tonal compensation for low-volume listening, independent of compression. */
object QuietListening {
    fun normalize(strength: Double): Double = if (strength.isFinite()) strength.coerceIn(0.0, 1.0) else 0.0

    fun bands(strength: Double): List<PeqBand> {
        val amount = normalize(strength)
        if (amount == 0.0) return emptyList()
        return listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 4.0 * amount, 0.707),
            PeqBand(PeqBand.Type.HIGH_SHELF, 6_000.0, amount, 0.707)
        )
    }
}
