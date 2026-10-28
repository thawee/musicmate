package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HeadphoneProfileParserTest {
    private val peak = "Filter 1: ON PK Fc 1000 Hz Gain -3.0 dB Q 1.41"

    private fun parse(text: String) = HeadphoneProfileParser.parse(text, "HD 600", "AutoEq.txt")

    @Test
    fun importsAutoEqBandsPreampAndMetadata() {
        val profile = parse("Preamp: -6.3 dB\n$peak\nFilter 2: ON LSC Fc 105 Hz Gain 4.5 dB Q 0.71\nFilter 3: ON HSC Fc 10000 Hz Gain -2 dB Q 0.7")
        assertEquals("HD 600", profile.name)
        assertEquals("AutoEq.txt", profile.source)
        assertEquals(-6.3, profile.preampDb, 0.0)
        assertEquals(listOf(PeqBand.Type.PEAK, PeqBand.Type.LOW_SHELF, PeqBand.Type.HIGH_SHELF), profile.bands.map { it.type })
        assertEquals(PeqBand(PeqBand.Type.PEAK, 1000.0, -3.0, 1.41), profile.bands.first())
        assertTrue(profile.id.isNotBlank())
        assertTrue(profile.id != parse(peak).id)
    }

    @Test
    fun acceptsBomWhitespaceCommentsDisabledFiltersAndFooter() {
        val profile = parse("\uFEFF# AutoEq\r\n\r\n  $peak # correction\r\nFilter 2: OFF HP Fc 10 Hz\r\nSee https://autoeq.app for more information\r\n")
        assertEquals(1, profile.bands.size)
        assertEquals(0.0, profile.preampDb, 0.0)
    }

    @Test
    fun acceptsTheKnownAutoEqMarkdownUsageFooter() {
        val footer = "See [usage instructions](https://github.com/jaakkopasanen/AutoEq#usage) for more options and info."
        val profile = parse("Preamp: -6.3 dB\n$peak\n$footer\n")
        assertEquals(-6.3, profile.preampDb, 0.0)
        assertEquals(1, profile.bands.size)
        assertThrows(IllegalArgumentException::class.java) {
            parse("$peak\n${footer.replace("for more options and info.", "Include: other.txt")}")
        }
    }

    @Test
    fun rejectsUnsupportedActiveFiltersWithLineAndType() {
        val error = assertThrows(IllegalArgumentException::class.java) { parse("# header\n$peak\nFilter 2: ON HP Fc 50 Hz Q 1") }
        assertTrue(error.message.orEmpty().contains("3"))
        assertTrue(error.message.orEmpty().contains("HP"))
    }

    @Test
    fun rejectsMalformedOrUnsupportedCommandsInsteadOfDroppingThem() {
        listOf("GraphicEQ: 20 -4; 1000 0", "Include: other.txt", "Filter 2: ON PK Fc 200 Hz Gain 3 dB", "Filter 2: MAYBE PK Fc 200 Hz Gain 3 dB Q 1", "Preamp: nope dB", "Preamp: -2 dB\nPreamp: -3 dB").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse("$peak\n$it") }
        }
    }

    @Test
    fun rejectsUnsafeFilterNumbersAndPreamp() {
        listOf("19 Hz Gain 0 dB Q 1", "20001 Hz Gain 0 dB Q 1", "1000 Hz Gain -25 dB Q 1", "1000 Hz Gain 13 dB Q 1", "1000 Hz Gain 0 dB Q 0.09", "1000 Hz Gain 0 dB Q 12.1", "NaN Hz Gain 0 dB Q 1", "1000 Hz Gain Infinity dB Q 1").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse("Filter 1: ON PK Fc $it") }
        }
        listOf("1", "-30.1", "NaN", "Infinity").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse("Preamp: $it dB\n$peak") }
        }
    }

    @Test
    fun acceptsSafetyRangeBoundaries() {
        val profile = parse("Preamp: -30 dB\nFilter 1: ON LSC Fc 20 Hz Gain -24 dB Q 0.1\nFilter 2: ON HSC Fc 20000 Hz Gain 12 dB Q 12")
        assertEquals(2, profile.bands.size)
    }

    @Test
    fun rejectsEmptyOrExcessiveImportsAndInvalidMetadata() {
        listOf("", "# comment\nFilter 1: OFF PK Fc 100 Hz Gain 0 dB Q 1", (1..21).joinToString("\n") { peak.replace("Filter 1:", "Filter $it:") }, "#".repeat(HeadphoneProfileParser.MAX_BYTES + 1), "#" + "é".repeat(HeadphoneProfileParser.MAX_BYTES / 2)).forEach {
            assertThrows(IllegalArgumentException::class.java) { parse(it) }
        }
        listOf("", " ", "x".repeat(201), "model\nother").forEach {
            assertThrows(IllegalArgumentException::class.java) { HeadphoneProfileParser.parse(peak, it, "AutoEq.txt") }
        }
        assertThrows(IllegalArgumentException::class.java) { HeadphoneProfileParser.parse(peak, "HD 600", "") }
    }
}
