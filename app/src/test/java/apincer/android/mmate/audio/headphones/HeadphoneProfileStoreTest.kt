package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.SoundMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HeadphoneProfileStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun profile() = HeadphoneProfileParser.parse("Preamp: -5.5 dB\nFilter 1: ON PK Fc 1000 Hz Gain -3 dB Q 1.4", "HD 600", "AutoEq.txt")

    @Test fun legacyModesMigrateWithoutLosingOtherBindingsOrUserSettings() {
        val file = File(folder.root, "legacy.json")
        file.writeText("""{"bindings":{
            "bt:bass":{"soundMode":"BASS","trebleDb":-2.5,"enabled":true},
            "usb:night":{"soundMode":"NIGHT","usbEqAllowed":true,"bassDb":1.5},
            "bt:sleep":{"soundMode":"SLEEP"},
            "bt:drive":{"soundMode":"DRIVE"},
            "bt:focus":{"soundMode":"FOCUS"}
        }}""")
        val store = HeadphoneProfileStore(file)
        val migrated = store.load()
        assertEquals(HeadphoneBinding(enabled = true, soundMode = SoundMode.CUSTOM, bassDb = 4.0, trebleDb = -2.5), migrated.bindings["bt:bass"])
        assertEquals(HeadphoneBinding(soundMode = SoundMode.REFERENCE, usbEqAllowed = true, bassDb = 1.5, nightListening = true), migrated.bindings["usb:night"])
        assertEquals(true, migrated.bindings.getValue("bt:sleep").nightListening)
        assertEquals(SoundMode.LIVELY, migrated.bindings.getValue("bt:drive").soundMode)
        assertEquals(SoundMode.RELAX, migrated.bindings.getValue("bt:focus").soundMode)
        assertEquals(migrated, store.load())
        assertEquals(false, file.readText().contains("NIGHT"))
        store.save(migrated.copy(bindings = migrated.bindings.mapValues { it.value.copy(nightListening = false) }))
        assertEquals(false, store.load().bindings.values.any { it.nightListening })
    }

    @Test fun outputAndCalibrationMethodSurviveReopenIncludingMeasuredFlatCorrections() {
        val flat = profile().copy(bands = emptyList(),
            output = apincer.android.mmate.audio.calibration.CalibrationOutput.SPEAKER,
            method = apincer.android.mmate.audio.calibration.CalibrationMethod.MICROPHONE)
        val store = HeadphoneProfileStore(File(folder.root, "profiles.json"))
        store.save(HeadphoneLibrary(listOf(flat)))
        assertEquals(flat, store.load().profiles.single())
    }

    @Test
    fun persistsProfilesAndIndependentOutputBindingsAcrossInstances() {
        val file = File(folder.root, "settings/headphones.json")
        val profile = profile()
        val library = HeadphoneLibrary(listOf(profile), mapOf(
            "usb:dac" to HeadphoneBinding(profile.id, true, 2.0, -1.0, true, SoundMode.RELAX, nightListening = true, crossfeed = true),
            "bt:AA:BB" to HeadphoneBinding(profile.id, false, -3.0, 1.0)
        ))
        HeadphoneProfileStore(file).save(library)
        assertEquals(library, HeadphoneProfileStore(file).load())
        HeadphoneProfileStore(file).save(HeadphoneLibrary())
        assertEquals(HeadphoneLibrary(), HeadphoneProfileStore(file).load())
        assertEquals(listOf("headphones.json"), file.parentFile!!.listFiles()!!.map { it.name })
    }

    @Test
    fun absentMalformedAndTruncatedFilesLoadSafely() {
        val file = File(folder.root, "headphones.json")
        val store = HeadphoneProfileStore(file)
        assertEquals(HeadphoneLibrary(), store.load())
        listOf("not json", "{\"profiles\":[", "{\"profiles\":null}").forEach {
            file.writeText(it)
            assertEquals(HeadphoneLibrary(), store.load())
        }
    }

    @Test
    fun legacyJsonWithoutASoundModePreservesCustomTrims() {
        val file = File(folder.root, "headphones.json")
        val profile = profile()
        val old = HeadphoneLibrary(listOf(profile), mapOf("bt:old" to
            HeadphoneBinding(profile.id, enabled = true, bassDb = 5.5, trebleDb = -6.0)))
        // Default-valued soundMode is omitted just as in released JSON files.
        val json = Json.encodeToString(old)
        org.junit.Assert.assertFalse(json.contains("soundMode"))
        file.writeText(json)
        val restored = HeadphoneProfileStore(file).load()
        assertEquals(old, restored)
        val binding = restored.bindings.getValue("bt:old")
        assertEquals(SoundMode.CUSTOM, binding.soundMode)
        assertEquals(5.5, binding.bassDb, 0.0)
        assertEquals(-6.0, binding.trebleDb, 0.0)
    }

    @Test
    fun validatesDeserializedProfilesAndBindings() {
        val file = File(folder.root, "headphones.json")
        val good = profile()
        val invalidLibraries = listOf(
            HeadphoneLibrary(listOf(good.copy(preampDb = 1.0))),
            HeadphoneLibrary(listOf(good.copy(preampDb = -31.0))),
            HeadphoneLibrary(listOf(good.copy(bands = emptyList()))),
            HeadphoneLibrary(listOf(good.copy(bands = listOf(good.bands.first().copy(q = 0.0))))),
            HeadphoneLibrary(listOf(good.copy(bands = listOf(good.bands.first().copy(frequencyHz = 0.0))))),
            HeadphoneLibrary(listOf(good.copy(bands = listOf(good.bands.first().copy(gainDb = 13.0))))),
            HeadphoneLibrary(listOf(good.copy(bands = List(21) { good.bands.first() }))),
            HeadphoneLibrary(listOf(good.copy(name = ""))),
            HeadphoneLibrary(listOf(good.copy(source = ""))),
            HeadphoneLibrary(listOf(good.copy(id = ""))),
            HeadphoneLibrary(listOf(good, good)),
            HeadphoneLibrary(profiles = (1..101).map { good.copy(id = "$it") }),
            HeadphoneLibrary(bindings = (1..101).associate { "$it" to HeadphoneBinding() }),
            HeadphoneLibrary(bindings = mapOf("usb:dac" to HeadphoneBinding(bassDb = 6.1))),
            HeadphoneLibrary(bindings = mapOf("bt:AA" to HeadphoneBinding(trebleDb = -6.1))),
            HeadphoneLibrary(bindings = mapOf("" to HeadphoneBinding()))
        )
        invalidLibraries.forEach {
            file.writeText(Json.encodeToString(it))
            assertEquals(HeadphoneLibrary(), HeadphoneProfileStore(file).load())
        }
    }

    @Test
    fun rejectsInvalidSaveWithoutReplacingPreviousLibrary() {
        val file = File(folder.root, "headphones.json")
        val store = HeadphoneProfileStore(file)
        val good = HeadphoneLibrary(listOf(profile()))
        store.save(good)
        assertThrows(IllegalArgumentException::class.java) { store.save(HeadphoneLibrary(bindings = mapOf("usb:dac" to HeadphoneBinding(bassDb = Double.NaN)))) }
        assertEquals(good, store.load())
    }

    @Test
    fun rejectsExcessiveProfileAndBindingCounts() {
        val store = HeadphoneProfileStore(File(folder.root, "headphones.json"))
        val good = profile()
        assertThrows(IllegalArgumentException::class.java) { store.save(HeadphoneLibrary(profiles = (1..101).map { good.copy(id = "$it") })) }
        assertThrows(IllegalArgumentException::class.java) { store.save(HeadphoneLibrary(bindings = (1..101).associate { "$it" to HeadphoneBinding() })) }
    }

    @Test
    fun profileIdsAreNeverUsedAsFilePaths() {
        val file = File(folder.root, "x")
        val library = HeadphoneLibrary(listOf(profile().copy(id = "../outside")))
        val store = HeadphoneProfileStore(file)
        store.save(library)
        assertEquals(library, store.load())
        assertEquals(listOf("x"), folder.root.listFiles()!!.map { it.name })
    }

    @Test
    fun rejectsOversizedStoredInput() {
        val file = File(folder.root, "headphones.json")
        file.writeText(" ".repeat(4 * 1024 * 1024 + 1))
        assertEquals(HeadphoneLibrary(), HeadphoneProfileStore(file).load())
    }
}
