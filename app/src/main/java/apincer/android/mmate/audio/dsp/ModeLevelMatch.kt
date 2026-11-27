package apincer.android.mmate.audio.dsp

import kotlin.math.log10
import kotlin.math.pow

/** Transient, attenuation-only comparison using estimated pink-spectrum energy at 48 kHz.
 * This is a repeatable EQ comparison, not a measurement of the playing song's loudness.
 */
internal object ModeLevelMatch {
    private fun chain(s: CarDspSettings) = DspChain.of(s, 48_000, 2)

    internal fun energyDb(s: CarDspSettings): Double {
        val chain = chain(s)
        var energy = 0.0
        repeat(256) { i ->
            val hz = 20.0 * 1000.0.pow(i / 255.0)
            repeat(2) { channel ->
                val balance = if (channel == 0) -s.balanceDb.coerceAtLeast(0.0) else s.balanceDb.coerceAtMost(0.0)
                energy += 10.0.pow((chain.channelResponseDb(hz, channel) + chain.preampDb + balance) / 10.0)
            }
        }
        return 10.0 * log10(energy / 512)
    }

    fun select(selected: CarDspSettings, correction: CarDspSettings, hearCorrection: Boolean): CarDspSettings {
        val selectedEnergy = energyDb(selected)
        val correctionEnergy = energyDb(correction)
        val source = if (hearCorrection) correction else selected
        val extraCut = minOf(selectedEnergy, correctionEnergy) -
            if (hearCorrection) correctionEnergy else selectedEnergy
        return source.with(requestedPreampDb = chain(source).preampDb + extraCut)
    }
}
