package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualSoundModeTest {
    private val correction = listOf(PeqBand(PeqBand.Type.PEAK, 200.0, -3.0, 1.0))

    @After
    fun resetRoute() {
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
        HeadphoneDsp.updateDevices(emptyList())
    }

    @Test
    fun carNightKeepsCalibrationAndCompression() {
        val choice = CarDspChoice(enabled = true, mode = SoundMode.REFERENCE, nightListening = true, correction = correction)
        val settings = choice.settings()!!
        assertEquals(correction, settings.bands)
        assertEquals(true, settings.compress)
        assertNull(choice.copy(enabled = false).settings())
    }

    @Test
    fun headphoneCustomKeepsSavedTrimsAndCalibrationPreamp() {
        val profile = HeadphoneProfile("test", "Test", "test.txt", -4.0, correction)
        val binding = HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.CUSTOM,
            bassDb = 2.5, trebleDb = -1.5)
        val settings = HeadphoneDsp.settingsFor(profile, binding)
        assertEquals(correction + HeadphoneTone(bassDb = 2.5, trebleDb = -1.5).bands(), settings.bands)
        assertEquals(-4.0, settings.requestedPreampDb, 0.0)
        assertEquals(false, settings.compress)
    }

    @Test
    fun usbConsentAppliesSavedNightModeAfterRouteReconnect() {
        val usb = listOf(Triple(AudioDeviceInfo.TYPE_USB_DEVICE, "card=1", "DAC"))
        val endpoint = HeadphoneEndpoint.of(usb)!!
        val profile = HeadphoneProfile("test", "Test", "test.txt", -4.0, correction)
        val binding = HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.REFERENCE, nightListening = true)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to binding)))
        HeadphoneDsp.updateDevices(usb)
        assertNull(HeadphoneDsp.current.settings)

        val consented = binding.copy(usbEqAllowed = true)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to consented)))
        HeadphoneDsp.updateDevices(emptyList())
        HeadphoneDsp.updateDevices(usb)

        val settings = HeadphoneDsp.current.settings!!
        assertEquals(consented, HeadphoneDsp.state.value.binding)
        assertEquals(correction, settings.bands)
        assertEquals(true, settings.compress)
        assertEquals(-4.0, settings.requestedPreampDb, 0.0)
    }
}
