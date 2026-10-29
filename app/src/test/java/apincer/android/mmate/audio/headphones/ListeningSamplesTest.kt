package apincer.android.mmate.audio.headphones

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ListeningSamplesTest {
    @Test fun instrumentsAreRepeatableBoundedThreeSecondPhrasesWithFadedEdges() {
        val instruments = ListeningSample.entries.filter { it.speechAssetPath == null }
        assertEquals(5, instruments.size)
        for (sample in instruments) {
            val pcm = ListeningSamples.render(sample)
            assertEquals(144_000, pcm.size)
            assertArrayEquals(pcm, ListeningSamples.render(sample), 0f)
            assertTrue(sample.name, pcm.all { it.isFinite() && abs(it) <= 1f })
            assertEquals(0f, pcm.first(), 0f)
            assertEquals(0f, pcm.last(), 0f)
            assertTrue(sample.name, rms(pcm) > 0.025)
            val envelopes = pcm.asList().chunked(4_800).map { block ->
                sqrt(block.sumOf { it.toDouble() * it } / block.size)
            }
            assertTrue("Phrase needs dynamics: $sample", envelopes.max() - envelopes.min() > 0.02)
            assertTrue("Phrase needs changing texture: $sample", pcm.toSet().size > 10_000)
        }
        for (a in instruments.indices) for (b in a + 1 until instruments.size) {
            val first = ListeningSamples.render(instruments[a])
            val second = ListeningSamples.render(instruments[b])
            assertTrue("Distinct instruments", first.indices.sumOf { abs(first[it] - second[it]).toDouble() } / first.size > 0.025)
        }
    }

    @Test fun voicesUseBundledRealSpeechAndRemainDistinct() {
        val voices = listOf(ListeningSample.MALE_VOICE, ListeningSample.FEMALE_VOICE).map { sample ->
            val path = requireNotNull(sample.speechAssetPath)
            val asset = listOf(File("src/main/assets", path), File("app/src/main/assets", path)).first { it.exists() }
            val bytes = asset.readBytes()
            assertEquals(288_000, bytes.size)
            val expectedHash = if (sample == ListeningSample.MALE_VOICE) {
                "d47f70947a8c1e62398f83a321b266873bdb6700168a1404b7e0eb95a8f9ed0e"
            } else "f5aae37ec727553af85ffcded17c61443f686482d5c54a7dcab209a6292205d5"
            assertEquals("Validated OSR crop", expectedHash,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
            val decoded = FloatArray(bytes.size / 2) { index ->
                val low = bytes[index * 2].toInt() and 255
                val high = bytes[index * 2 + 1].toInt()
                ((high shl 8) or low).toShort() / 32768f
            }
            val rendered = ListeningSamples.render(sample, decoded)
            assertTrue(rms(rendered) > 0.04)
            assertEquals(0f, rendered.first(), 0f)
            assertEquals(0f, rendered.last(), 0f)
            assertTrue(rendered.all { it.isFinite() && abs(it) <= 1f })
            assertArrayEquals(rendered, ListeningSamples.render(sample, decoded), 0f)
            rendered
        }
        assertFalse(voices[0].contentEquals(voices[1]))
    }

    @Test fun percussionProvidesUpperFrequencyTextureAndBassProvidesLowFrequencyBody() {
        fun differenceRatio(pcm: FloatArray): Double {
            val differences = (1 until pcm.size).sumOf { (pcm[it] - pcm[it - 1]).toDouble().let { d -> d * d } }
            return sqrt(differences / pcm.sumOf { it.toDouble() * it })
        }
        val bass = differenceRatio(ListeningSamples.render(ListeningSample.BASS_GROOVE))
        val percussion = differenceRatio(ListeningSamples.render(ListeningSample.SHAKER))
        assertTrue("Low-frequency groove", bass < 0.05)
        assertTrue("Broad high-frequency percussion", percussion > 0.5)
        assertTrue("Useful contrast for bass versus treble listening", percussion > bass * 20)
    }

    @Test fun missingOrMalformedSpeechIsRejectedInsteadOfUsingSyntheticVoice() {
        for (speech in arrayOf(null, FloatArray(12), FloatArray(144_000) { Float.NaN })) {
            try {
                ListeningSamples.render(ListeningSample.MALE_VOICE, speech)
                fail("Invalid speech accepted")
            } catch (_: IllegalArgumentException) {
                // Real speech is required for voice selections.
            }
        }
    }

    private fun rms(pcm: FloatArray): Double = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
}
