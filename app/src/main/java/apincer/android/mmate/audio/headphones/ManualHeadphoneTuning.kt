package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.PeqBand
import kotlinx.serialization.Serializable

@Serializable
enum class HeadphoneKind { OVER_EAR, EARBUDS, IN_EAR }

@Serializable
data class HeadphoneTone(val bassDb: Double = 0.0, val midDb: Double = 0.0, val trebleDb: Double = 0.0) {
    fun bands(): List<PeqBand> = buildList {
        if (bassDb != 0.0) add(PeqBand(PeqBand.Type.LOW_SHELF, 100.0, bassDb, 0.707))
        if (midDb != 0.0) add(PeqBand(PeqBand.Type.PEAK, 1_000.0, midDb, 0.707))
        if (trebleDb != 0.0) add(PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, trebleDb, 0.707))
    }
}

@Serializable
data class ManualHeadphoneTuning(
    val kind: HeadphoneKind = HeadphoneKind.OVER_EAR,
    val left: HeadphoneTone = HeadphoneTone(),
    val right: HeadphoneTone = HeadphoneTone(),
    val balanceDb: Double = 0.0
) {
    fun settings(): CarDspSettings = CarDspSettings(emptyList(),
        leftBands = left.bands(), rightBands = right.bands(), balanceDb = balanceDb,
        monoBands = HeadphoneTone((left.bassDb + right.bassDb) / 2,
            (left.midDb + right.midDb) / 2, (left.trebleDb + right.trebleDb) / 2).bands())

    internal fun validate() {
        for (tone in listOf(left, right)) {
            require(listOf(tone.bassDb, tone.midDb, tone.trebleDb).all { it.isFinite() && it in -6.0..6.0 }) {
                "Bass, mids and treble must be between -6 and +6 dB."
            }
        }
        require(balanceDb.isFinite() && balanceDb in -12.0..12.0) { "Balance must be between -12 and +12 dB." }
    }
}

/** Common starting sounds. The DIY controls shape only bass (100 Hz shelf), mids (1 kHz) and treble (8 kHz shelf). */
@Serializable
enum class HeadphoneTonePreset(val label: String, val tone: HeadphoneTone) {
    FLAT("Flat", HeadphoneTone()),
    WARM("Warm", HeadphoneTone(bassDb = 3.0, trebleDb = -2.0)),
    BASS("Bass boost", HeadphoneTone(bassDb = 5.0)),
    U_SHAPE("U-shape", HeadphoneTone(bassDb = 4.0, midDb = -1.0, trebleDb = 3.0)),
    V_SHAPE("V-shape", HeadphoneTone(bassDb = 5.0, midDb = -3.0, trebleDb = 5.0)),
    VOCAL("Vocal", HeadphoneTone(bassDb = -1.0, midDb = 3.0, trebleDb = 1.0)),
    BRIGHT("Bright", HeadphoneTone(trebleDb = 3.0)),
    SMOOTH("Smooth", HeadphoneTone(trebleDb = -3.0));

    companion object {
        fun of(tone: HeadphoneTone): HeadphoneTonePreset? = entries.firstOrNull { it.tone == tone }
    }
}
