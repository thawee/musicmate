package apincer.android.mmate

import apincer.music.core.model.AudioTag
import apincer.music.core.model.ExcludeRule
import apincer.music.core.model.PlaylistEntry
import apincer.music.core.model.PlaylistRule
import apincer.music.core.utils.TagVocabulary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks the bundled playlists.json against the tag editor's mood and style lists. */
class PlaylistsJsonTest {

    private val playlists: List<JsonObject> by lazy {
        val file = File("src/main/assets/playlists.json")
        assertTrue("playlists.json not found from ${File("").absolutePath}", file.isFile)
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("playlists").jsonArray.map { it.jsonObject }
    }

    private fun vocabulary(arrayName: String): Set<String> {
        val file = File("../core/src/main/res/values/arrays.xml")
        assertTrue("arrays.xml not found from ${File("").absolutePath}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val arrays = doc.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val array = arrays.item(i) as org.w3c.dom.Element
            if (array.getAttribute("name") != arrayName) continue
            val items = array.getElementsByTagName("item")
            return (0 until items.length).map { items.item(it).textContent.trim() }.toSet()
        }
        throw AssertionError("No string-array $arrayName")
    }

    private fun values(obj: JsonObject?, key: String): List<String> = when (val v = obj?.get(key)) {
        null -> emptyList()
        is JsonArray -> v.map { it.jsonPrimitive.content }
        is JsonPrimitive -> listOf(v.content)
        else -> throw AssertionError("Unexpected $key: $v")
    }

    private fun rules(playlist: JsonObject): List<JsonObject> =
        (playlist["rules"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    @Test
    fun everyMoodAndStyleIsAVocabularyName() {
        val moods = vocabulary("moods")
        val styles = vocabulary("styles")
        assertTrue(moods.isNotEmpty() && styles.isNotEmpty())
        val problems = mutableListOf<String>()
        var checked = 0
        for (playlist in playlists) {
            val name = playlist["name"]?.jsonPrimitive?.content
            for (rule in rules(playlist)) {
                val exclude = rule["exclude"] as? JsonObject
                for ((field, vocab, list) in listOf(
                    Triple(TagVocabulary.Field.MOOD, moods, values(rule, "mood") + values(exclude, "mood")),
                    Triple(TagVocabulary.Field.STYLE, styles, values(rule, "style") + values(exclude, "style")),
                )) {
                    for (value in list) {
                        if (value.trim() == "*") continue
                        checked++
                        val canonical = TagVocabulary.canonical(field, value)
                        // An alias still matches tags, but the file should name the current preset
                        if (canonical !in vocab || !canonical.equals(value.trim(), ignoreCase = true)) {
                            problems += "$name: $field \"$value\""
                        }
                    }
                }
            }
        }
        assertTrue("no mood or style values found", checked > 0)
        assertEquals("Terms outside the vocabulary", emptyList<String>(), problems)
    }

    private fun entry(playlist: JsonObject): PlaylistEntry {
        val entry = PlaylistEntry()
        entry.name = playlist.getValue("name").jsonPrimitive.content
        entry.type = playlist.getValue("type").jsonPrimitive.content
        entry.rules = rules(playlist).map { r ->
            PlaylistRule().apply {
                genre = values(r, "genre")
                mood = values(r, "mood")
                style = values(r, "style")
                (r["exclude"] as? JsonObject)?.let { e ->
                    exclude = ExcludeRule().apply {
                        mood = values(e, "mood")
                        style = values(e, "style")
                    }
                }
            }
        }
        entry.compileRules()
        return entry
    }

    private fun track(mood: String? = null, style: String? = null) = AudioTag().apply {
        this.mood = mood
        this.style = style
    }

    private fun preset(uuid: String): PlaylistEntry {
        val json = playlists.single { (it["uuid"] as? JsonElement)?.jsonPrimitive?.content == uuid }
        assertEquals("mood", json["category"]?.jsonPrimitive?.content)
        return entry(json)
    }

    @Test
    fun moodPresetsMatchByMoodAndStyleWithoutGenre() {
        val drive = preset("mood-drive")
        assertTrue(drive.isInPlaylist(track(mood = "Energetic")))
        assertTrue(drive.isInPlaylist(track(style = "Groove")))
        assertFalse(drive.isInPlaylist(track(mood = "Calm", style = "Synth-driven")))

        val windDown = preset("mood-wind-down")
        assertTrue(windDown.isInPlaylist(track(mood = "Calm")))
        assertFalse(windDown.isInPlaylist(track(mood = "Calm", style = "Live")))

        val sleep = preset("mood-sleep")
        assertTrue(sleep.isInPlaylist(track(style = "Ambient")))
        assertTrue(sleep.isInPlaylist(track(mood = "Calm")))
        assertFalse(sleep.isInPlaylist(track(mood = "Happy", style = "Ambient")))
        assertFalse(sleep.isInPlaylist(track(mood = "Calm", style = "Live")))

        assertTrue(preset("mood-focus").isInPlaylist(track(style = "Lo-Fi")))
    }

    @Test
    fun sleepStopsAfter45MinutesAndOtherMoodsRefill() {
        val minutes = playlists.associate {
            it["uuid"]?.jsonPrimitive?.content to (it["sessionMinutes"]?.jsonPrimitive?.int ?: 0)
        }
        assertEquals(45, minutes["mood-sleep"])
        assertEquals(0, minutes["mood-drive"])
        assertEquals(0, minutes["mood-focus"])
        assertEquals(0, minutes["mood-wind-down"])
    }
}
