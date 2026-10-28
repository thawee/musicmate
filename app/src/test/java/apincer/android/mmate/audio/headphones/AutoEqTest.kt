package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AutoEqTest {
    @get:Rule val temporary = TemporaryFolder()

    private val aero = "- [1MORE Aero (ANC Off)](./HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)) by HypetheSonics on GRAS RA0045"
    private val index = """
        # Index
        This is a list of all equalization profiles. Target is in parentheses if there are results with multiple targets
        from the same source.

        $aero
        - [AAW x Custom Art Project 4+2 (bass filter)](./crinacle/711%20in-ear/AAW%20x%20Custom%20Art%20Project%204+2%20(bass%20filter)) by crinacle on 711
        - [AüR Audio Aure](./Kazi/in-ear/A%C3%BCR%20Audio%20Aure) by Kazi
        - [Astell & Kern Rosie (2 o'clock)](./crinacle/711%20in-ear/Astell%20&%20Kern%20Rosie%20(2%20o'clock)) by crinacle on 711
        - [Bad path](./Somebody/in-ear/Bad#path) by Somebody
    """.trimIndent()

    private val hd650 = """
        Preamp: -6.1 dB
        Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
        Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
        Filter 3: ON PK Fc 118 Hz Gain -3.1 dB Q 0.50
        Filter 4: ON PK Fc 37 Hz Gain 0.7 dB Q 3.96
        Filter 5: ON PK Fc 3169 Hz Gain -1.7 dB Q 3.89
        Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
        Filter 7: ON PK Fc 1227 Hz Gain -1.2 dB Q 2.53
        Filter 8: ON PK Fc 2055 Hz Gain 1.2 dB Q 3.23
        Filter 9: ON PK Fc 587 Hz Gain 0.4 dB Q 1.19
        Filter 10: ON PK Fc 5332 Hz Gain -1.1 dB Q 5.75
    """.trimIndent() + "\n"

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_700_000_000_000L

    private fun entry(name: String, source: String = "crinacle") = AutoEqEntry(name, source, null, "$source/over-ear/x")

    @Test fun parseReadsRealIndexLinesAndSkipsTheRest() {
        assertEquals(listOf(
            AutoEqEntry("1MORE Aero (ANC Off)", "HypetheSonics", "GRAS RA0045", "HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)"),
            AutoEqEntry("AAW x Custom Art Project 4+2 (bass filter)", "crinacle", "711", "crinacle/711%20in-ear/AAW%20x%20Custom%20Art%20Project%204+2%20(bass%20filter)"),
            AutoEqEntry("AüR Audio Aure", "Kazi", null, "Kazi/in-ear/A%C3%BCR%20Audio%20Aure"),
            AutoEqEntry("Astell & Kern Rosie (2 o'clock)", "crinacle", "711", "crinacle/711%20in-ear/Astell%20&%20Kern%20Rosie%20(2%20o'clock)")
        ), AutoEqIndex.parse(index))
    }

    @Test fun profileUrlKeepsTheIndexPathEncoding() {
        val parsed = AutoEqIndex.parse(index)
        assertEquals("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)/1MORE%20Aero%20(ANC%20Off)%20ParametricEQ.txt",
            parsed[0].profileUrl)
        assertEquals("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/crinacle/711%20in-ear/AAW%20x%20Custom%20Art%20Project%204+2%20(bass%20filter)/AAW%20x%20Custom%20Art%20Project%204+2%20(bass%20filter)%20ParametricEQ.txt",
            parsed[1].profileUrl)
        assertEquals("AutoEq, by HypetheSonics on GRAS RA0045", parsed[0].credit)
        assertEquals("AutoEq, by Kazi", parsed[2].credit)
    }

    @Test fun searchNeedsEveryTokenAndRanksPrefixThenLength() {
        val entries = listOf(entry("Sennheiser HD 650"), entry("HD 650 Custom"), entry("HD 650"), entry("Sennheiser HD 600"))
        assertEquals(listOf("HD 650", "HD 650 Custom", "Sennheiser HD 650"), AutoEqIndex.search(entries, "  hd   650 ").map { it.name })
        assertEquals(listOf("Sennheiser HD 650"), AutoEqIndex.search(entries, "650 SENNHEISER").map { it.name })
        assertEquals(1, AutoEqIndex.search(entries, "hd", limit = 1).size)
        assertTrue(AutoEqIndex.search(entries, " ").isEmpty())
    }

    @Test fun searchPutsOratory1990FirstAndKeepsOtherTiesInOrder() {
        val entries = listOf(entry("HD 650", "crinacle"), entry("HD 650", "Rtings"), entry("HD 650", "oratory1990"), entry("HD 650", "Innerfidelity"))
        assertEquals(listOf("oratory1990", "crinacle", "Rtings", "Innerfidelity"), AutoEqIndex.search(entries, "hd 650").map { it.source })
    }

    @Test fun freshCacheSkipsTheDownload() {
        val cache = temporary.newFile("autoeq_index.md").apply { writeText(index); setLastModified(start) }
        val client = AutoEqClient(cache, { _, _ -> throw AssertionError("fresh cache must not download") }, { start + day })
        assertEquals(4, client.entries().size)
    }

    @Test fun staleCacheIsReplacedByADownloadAndRemembered() {
        val cache = temporary.newFile("autoeq_index.md").apply { writeText(aero); setLastModified(start) }
        val requests = mutableListOf<Pair<String, Int>>()
        val client = AutoEqClient(cache, { url, max -> requests += url to max; index }, { start + 31 * day })
        assertEquals(4, client.entries().size)
        assertEquals(4, client.entries().size)
        assertEquals(listOf("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md" to 4 * 1024 * 1024), requests)
        assertEquals(index, cache.readText())
        assertEquals(listOf("autoeq_index.md"), temporary.root.list()!!.toList())
    }

    @Test fun offlineFallsBackToAStaleCache() {
        val cache = temporary.newFile("autoeq_index.md").apply { writeText(aero); setLastModified(start) }
        val client = AutoEqClient(cache, { _, _ -> throw IOException("offline") }, { start + 31 * day })
        assertEquals(listOf("1MORE Aero (ANC Off)"), client.entries().map { it.name })
    }

    @Test fun aWifiSignInPageIsNotCachedAsTheIndex() {
        val cache = temporary.newFile("autoeq_index.md").apply { writeText(aero); setLastModified(start) }
        val client = AutoEqClient(cache, { _, _ -> "<html>Sign in to Wi-Fi</html>" }, { start + 31 * day })
        assertEquals(listOf("1MORE Aero (ANC Off)"), client.entries().map { it.name })
        assertEquals(aero, cache.readText())

        val uncached = AutoEqClient(File(temporary.root, "none.md"), { _, _ -> "<html>Sign in to Wi-Fi</html>" }, { start })
        assertThrows(IOException::class.java) { uncached.entries() }
        assertFalse(File(temporary.root, "none.md").exists())
    }

    @Test fun offlineWithoutACacheExplainsWhatToDo() {
        val client = AutoEqClient(File(temporary.root, "autoeq_index.md"), { _, _ -> throw IOException("offline") }, { start })
        val failure = assertThrows(IOException::class.java) { client.entries() }
        assertEquals("Could not reach AutoEq. Check the internet connection and try again.", failure.message)
    }

    @Test fun profileDownloadsTheParametricFileWithTheProfileCap() {
        val requests = mutableListOf<Pair<String, Int>>()
        val client = AutoEqClient(File(temporary.root, "autoeq_index.md"), { url, max -> requests += url to max; hd650 })
        val aeroEntry = AutoEqIndex.parse(aero).single()
        assertEquals(hd650, client.profile(aeroEntry))
        assertEquals(listOf(aeroEntry.profileUrl to HeadphoneProfileParser.MAX_BYTES), requests)
    }

    @Test fun profileFailureExplainsWhatToDo() {
        val client = AutoEqClient(File(temporary.root, "autoeq_index.md"), { _, _ -> throw IOException("HTTP 404") })
        val failure = assertThrows(IOException::class.java) { client.profile(AutoEqIndex.parse(aero).single()) }
        assertEquals("Could not download this profile. Try again, or import it from autoeq.app.", failure.message)
    }

    @Test fun realHd650ProfileParses() {
        val entry = AutoEqEntry("Sennheiser HD 650", "oratory1990", null, "oratory1990/over-ear/Sennheiser%20HD%20650")
        val profile = HeadphoneProfileParser.parse(hd650, entry.name, entry.credit)
        assertEquals("AutoEq, by oratory1990", profile.source)
        assertEquals(-6.1, profile.preampDb, 0.0)
        assertEquals(10, profile.bands.size)
        assertEquals(PeqBand(PeqBand.Type.LOW_SHELF, 105.0, 6.4, 0.70), profile.bands.first())
        assertEquals(PeqBand(PeqBand.Type.PEAK, 5332.0, -1.1, 5.75), profile.bands.last())
        assertNull(profile.manual)
    }
}
