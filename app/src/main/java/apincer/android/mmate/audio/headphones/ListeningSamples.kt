package apincer.android.mmate.audio.headphones

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class ListeningSample(val label: String, val speechAssetPath: String? = null) {
    BASS_GROOVE("Kick + bass groove"),
    ACOUSTIC("Acoustic plucks"),
    MALE_VOICE("Male voice", "listening/male_voice.pcm"),
    FEMALE_VOICE("Female voice", "listening/female_voice.pcm"),
    PIANO("Piano"),
    SHAKER("Shaker + cymbals"),
    MUSIC("Musical mix")
}

/** Repeatable mono phrases. The player applies output gain, channel routing and EQ afterwards. */
object ListeningSamples {
    const val RATE = 48_000
    const val DURATION_SECONDS = 3
    private const val FRAMES = RATE * DURATION_SECONDS

    fun render(sample: ListeningSample, speech: FloatArray? = null): FloatArray {
        val pcm = if (sample.speechAssetPath != null) {
            require(speech != null && speech.size == FRAMES && speech.all { it.isFinite() }) {
                "Voice samples require three seconds of decoded bundled speech"
            }
            speech.copyOf()
        } else {
            FloatArray(FRAMES).also { output ->
                when (sample) {
                    ListeningSample.BASS_GROOVE -> bass(output, 1.0)
                    ListeningSample.ACOUSTIC -> acoustic(output, 1.0)
                    ListeningSample.PIANO -> piano(output, 1.0)
                    ListeningSample.SHAKER -> percussion(output, 1.0)
                    ListeningSample.MUSIC -> {
                        bass(output, 0.7)
                        acoustic(output, 0.5)
                        piano(output, 0.35)
                        percussion(output, 0.45)
                    }
                    else -> error("Speech handled above")
                }
            }
        }
        // Ten millisecond fades include exact zero endpoints, including decoded recordings.
        val fade = RATE / 100
        for (i in pcm.indices) pcm[i] *= min(1.0, min(i, pcm.lastIndex - i).toDouble() / fade).toFloat()
        val peak = pcm.maxOf { abs(it) }
        if (peak > 0f) for (i in pcm.indices) pcm[i] *= 0.92f / peak
        return pcm
    }

    private fun bass(out: FloatArray, gain: Double) {
        // Original D-minor eighth-note bass line, with a downward-sweeping kick each beat.
        val notes = doubleArrayOf(73.416, 73.416, 110.0, 130.813, 65.406, 65.406, 110.0, 65.406)
        notes.forEachIndexed { note, frequency ->
            event(out, note * 0.375, 0.35) { t ->
                val attack = min(1.0, t / 0.008)
                gain * 0.6 * attack * exp(-t * 6) *
                    (sin(2 * PI * frequency * t) + 0.28 * sin(4 * PI * frequency * t) + 0.1 * sin(6 * PI * frequency * t))
            }
        }
        repeat(4) { beat ->
            event(out, beat * 0.75, 0.45) { t ->
                val phase = 2 * PI * (48 * t + 110 * (1 - exp(-t * 32)) / 32)
                gain * 0.85 * min(1.0, t / 0.002) * exp(-t * 14) * sin(phase)
            }
        }
    }

    private fun acoustic(out: FloatArray, gain: Double) {
        // Dm/F arpeggio: damped harmonics with a brief deterministic pick transient.
        val notes = doubleArrayOf(146.832, 220.0, 293.665, 349.228, 261.626, 220.0, 174.614, 261.626)
        val random = Noise(0x13579)
        notes.forEachIndexed { note, frequency ->
            event(out, note * 0.375, 1.1) { t ->
                var string = 0.0
                for (harmonic in 1..12) {
                    string += sin(2 * PI * frequency * harmonic * t + 0.15 * harmonic) *
                        exp(-t * (2.5 + harmonic * 0.65)) / harmonic.toDouble().pow(1.25)
                }
                gain * 0.5 * min(1.0, t / 0.003) * (string + random.next() * 0.16 * exp(-t * 180))
            }
        }
    }

    private fun piano(out: FloatArray, gain: Double) {
        // Two voiced chords, with stretched upper partials and a soft hammer attack.
        val chords = arrayOf(doubleArrayOf(146.832, 220.0, 293.665, 349.228), doubleArrayOf(174.614, 261.626, 349.228, 440.0))
        chords.forEachIndexed { chord, notes ->
            notes.forEachIndexed { voice, frequency ->
                event(out, chord * 1.5 + voice * 0.025, 2.8) { t ->
                    var tone = 0.0
                    for (harmonic in 1..10) {
                        val stretched = harmonic * (1 + 0.00012 * harmonic * harmonic)
                        tone += sin(2 * PI * frequency * stretched * t) *
                            exp(-t * (1.8 + harmonic * 0.55)) / harmonic.toDouble().pow(1.6)
                    }
                    gain * 0.3 * (1 - exp(-t * 500)) * tone
                }
            }
        }
    }

    private fun percussion(out: FloatArray, gain: Double) {
        val random = Noise(0x2468)
        repeat(16) { hit ->
            var previous = 0.0
            event(out, hit * 0.1875, if (hit % 4 == 0) 0.38 else 0.12) { t ->
                val noise = random.next()
                val highPassed = noise - previous
                previous = noise
                val shaker = highPassed * exp(-t * 55) * (if (hit % 2 == 0) 0.25 else 0.16)
                val cymbal = if (hit % 4 == 0) {
                    (highPassed * 0.12 + sin(2 * PI * 7_231 * t) * sin(2 * PI * 10_913 * t) * 0.15) * exp(-t * 14)
                } else 0.0
                gain * min(1.0, t / 0.001) * (shaker + cymbal)
            }
        }
    }

    private inline fun event(out: FloatArray, start: Double, duration: Double, signal: (Double) -> Double) {
        val from = (start * RATE).toInt()
        val end = min(out.size, from + (duration * RATE).toInt())
        val fade = RATE / 100
        for (i in from until end) {
            val release = min(1.0, (end - 1 - i).toDouble() / fade)
            out[i] += (signal((i - from).toDouble() / RATE) * release).toFloat()
        }
    }

    private class Noise(private var state: Int) {
        fun next(): Double {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return state.toDouble() / Int.MAX_VALUE
        }
    }
}
