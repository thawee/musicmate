package apincer.android.mmate.audio.dsp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.headphones.*
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AutoSoundPreferencesTest {
    private var saved: String? = null
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val prefs = mockk<SharedPreferences> {
        every { getString("config", null) } answers { saved }
        every { edit() } returns editor
    }
    private val context = mockk<Context> {
        every { getSharedPreferences("auto_sound_mode", Context.MODE_PRIVATE) } returns prefs
    }
    private val config = AutoSoundConfig(enabled = true, rules = listOf(
        AutoSoundRule(AutoSoundField.GENRE, "Rock", SoundMode.LIVELY),
        AutoSoundRule(AutoSoundField.STYLE, "Ambient", SoundMode.RELAX)
    ))

    init {
        every { editor.putString("config", any()) } answers {
            saved = secondArg()
            editor
        }
    }

    @After fun reset() {
        AutoSoundPreferences.saveSettings(context, AutoSoundConfig())
        AutoSoundPreferences.resetSession()
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
        HeadphoneDsp.updateDevices(emptyList())
        CarDsp.target = null
    }

    @Test fun holdSurvivesTrackChangesAndResumeUsesLatestTrack() {
        AutoSoundPreferences.saveSettings(context, config)
        AutoSoundPreferences.updateTrack("Rock", null)
        assertEquals(SoundMode.LIVELY, AutoSoundPreferences.effectiveMode(SoundMode.NIGHT))
        AutoSoundPreferences.hold()
        AutoSoundPreferences.updateTrack(null, "Ambient")
        assertTrue(AutoSoundPreferences.state.value.held)
        assertEquals(SoundMode.LIVELY, AutoSoundPreferences.state.value.resolvedMode)
        AutoSoundPreferences.resume()
        assertEquals(SoundMode.RELAX, AutoSoundPreferences.state.value.resolvedMode)
        AutoSoundPreferences.hold()
        AutoSoundPreferences.resetSession()
        assertFalse(AutoSoundPreferences.state.value.held)
        assertEquals(SoundMode.REFERENCE, AutoSoundPreferences.state.value.resolvedMode)
        assertEquals(config, AutoSoundPreferences.decode(saved))
        AutoSoundPreferences.setEnabled(context, false)
        assertEquals(SoundMode.NIGHT, AutoSoundPreferences.effectiveMode(SoundMode.NIGHT))
    }

    @Test fun autoRefreshesCurveWithoutChangingManualChoiceOrUsbConsent() {
        val usb = Triple(AudioDeviceInfo.TYPE_USB_DEVICE, "card=1", "DAC")
        val endpoint = HeadphoneEndpoint.of(listOf(usb))!!
        val profile = HeadphoneProfile("test", "Test", "test.txt", -4.0, emptyList())
        val binding = HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.NIGHT)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(profiles = listOf(profile), bindings = mapOf(endpoint.key to binding)))
        HeadphoneDsp.updateDevices(listOf(usb))
        AutoSoundPreferences.saveSettings(context, config)
        AutoSoundPreferences.updateTrack("Rock", null)
        assertNull(CarDsp.effectiveTarget())
        assertEquals(binding, HeadphoneDsp.state.value.binding)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(profiles = listOf(profile), bindings = mapOf(endpoint.key to binding.copy(usbEqAllowed = true))))
        assertEquals(SoundMode.LIVELY.bands(), CarDsp.effectiveTarget()!!.bands)
        AutoSoundPreferences.updateTrack(null, "Ambient")
        assertEquals(SoundMode.RELAX.bands(), CarDsp.effectiveTarget()!!.bands)
        assertEquals(SoundMode.NIGHT, HeadphoneDsp.state.value.binding.soundMode)
        assertEquals(profile.bands + SoundMode.RELAX.bands(), HeadphoneDsp.settingsFor(profile, binding).bands)
        assertNull(CarDspChoice(enabled = false).settings())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSettingsAreRejectedBeforeWriting() {
        AutoSoundPreferences.saveSettings(context, config.copy(rules = listOf(AutoSoundRule(AutoSoundField.GENRE, " ", SoundMode.BASS))))
    }
}
