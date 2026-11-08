package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.SoundMode
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HeadphonePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun processingWithoutCorrectionAndRemovingCorrectionPreserveModeNightAndConsent() {
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        val endpoint = HeadphoneEndpoint("usb:optional-correction", "DAC", usb = true)
        val profile = HeadphoneProfile.createManual("Optional correction", ManualHeadphoneTuning())
        val store = HeadphoneProfileStore(File(temporary.root, "headphone_profiles.json"))
        HeadphonePreferences.setSoundMode(context, endpoint, SoundMode.VOCAL)
        HeadphonePreferences.setNightListening(context, endpoint, true)
        assertFalse(store.load().bindings.getValue(endpoint.key).usbEqAllowed)
        HeadphonePreferences.setEnabled(context, endpoint, true)
        val withoutCorrection = store.load().bindings.getValue(endpoint.key)
        assertEquals(null, withoutCorrection.profileId)
        assertTrue(withoutCorrection.enabled)
        assertTrue(withoutCorrection.usbEqAllowed)
        assertTrue(withoutCorrection.nightListening)
        HeadphonePreferences.import(context, profile)
        HeadphonePreferences.select(context, endpoint, profile.id)
        HeadphonePreferences.select(context, endpoint, null)
        assertEquals(withoutCorrection, store.load().bindings.getValue(endpoint.key))
        HeadphonePreferences.select(context, endpoint, profile.id)
        HeadphonePreferences.delete(context, profile.id)
        assertEquals(withoutCorrection, store.load().bindings.getValue(endpoint.key))
        HeadphonePreferences.setEnabled(context, endpoint, false)
        assertFalse(store.load().bindings.getValue(endpoint.key).usbEqAllowed)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
    }

    @Test
    fun selectionConsentAdjustmentsAndDeletionArePersistedWithoutSpeakerFallback() {
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        val endpoint = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)
        val profile = HeadphoneProfileParser.parse(
            "Preamp: -5 dB\nFilter 1: ON PK Fc 1000 Hz Gain -2 dB Q 1",
            "My headphones", "model.txt"
        )
        HeadphonePreferences.import(context, profile)
        HeadphonePreferences.import(context, profile)
        assertEquals(1, HeadphonePreferences.state.value.profiles.count { it.id == profile.id })
        HeadphonePreferences.select(context, endpoint, profile.id)
        assertFalse(HeadphonePreferences.state.value.bindings.getValue(endpoint.key).enabled)
        HeadphonePreferences.setEnabled(context, endpoint, true)
        HeadphonePreferences.adjust(context, endpoint, 2.0, -1.0)
        val store = HeadphoneProfileStore(File(temporary.root, "headphone_profiles.json"))
        assertEquals(HeadphonePreferences.state.value, store.load())
        assertTrue(store.load().bindings.getValue(endpoint.key).usbEqAllowed)
        HeadphonePreferences.setEnabled(context, endpoint, false)
        assertFalse(store.load().bindings.getValue(endpoint.key).usbEqAllowed)
        HeadphonePreferences.delete(context, profile.id)
        val binding = store.load().bindings.getValue(endpoint.key)
        assertEquals(null, binding.profileId)
        assertFalse(binding.enabled)
        assertTrue(store.load().profiles.isEmpty())
        val firstPair = HeadphoneProfile.createManual("DIY over-ear", ManualHeadphoneTuning(left = HeadphoneTone(bassDb = 2.0)))
        val secondPair = HeadphoneProfile.createManual("DIY earbuds", ManualHeadphoneTuning(kind = HeadphoneKind.EARBUDS,
            right = HeadphoneTone(trebleDb = -2.0)))
        HeadphonePreferences.saveManual(context, firstPair, endpoint)
        assertFalse(store.load().bindings.getValue(endpoint.key).enabled)
        HeadphonePreferences.setEnabled(context, endpoint, true)
        HeadphonePreferences.saveManual(context, firstPair.copy(name = "Renamed pair"), endpoint)
        assertTrue(store.load().bindings.getValue(endpoint.key).usbEqAllowed)
        HeadphonePreferences.saveManual(context, secondPair, endpoint)
        assertFalse(store.load().bindings.getValue(endpoint.key).enabled)
        HeadphonePreferences.select(context, endpoint, firstPair.id)
        assertEquals(firstPair.manual, store.load().profiles.first { it.id == firstPair.id }.manual)
        assertEquals(secondPair.manual, store.load().profiles.first { it.id == secondPair.id }.manual)
        HeadphonePreferences.delete(context, firstPair.id)
        HeadphonePreferences.delete(context, secondPair.id)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
    }

    @Test
    fun savingADiyProfileTurnsItsEqOnExceptForUsbWhichNeedsConsent() {
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        val bluetooth = HeadphoneEndpoint("bt:00:11", "DIY BT", usb = false)
        val wired = HeadphoneEndpoint("wired", "Wired headphones", usb = false, wired = true)
        val usb = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)
        val pair = HeadphoneProfile.createManual("DIY pair", ManualHeadphoneTuning(left = HeadphoneTone(bassDb = 2.0)))
        val store = HeadphoneProfileStore(File(temporary.root, "headphone_profiles.json"))
        HeadphonePreferences.saveManual(context, pair, bluetooth)
        assertTrue(store.load().bindings.getValue(bluetooth.key).enabled)
        HeadphonePreferences.saveManual(context, pair, wired)
        assertTrue(store.load().bindings.getValue(wired.key).enabled)
        HeadphonePreferences.saveManual(context, pair, usb)
        val usbBinding = store.load().bindings.getValue(usb.key)
        assertFalse(usbBinding.enabled)
        assertFalse(usbBinding.usbEqAllowed)
        HeadphonePreferences.delete(context, pair.id)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
    }

    @Test
    fun choosingSoundModeKeepsCalibrationAndConsentWhileAdjustingReturnsToCustom() {
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        val endpoint = HeadphoneEndpoint("usb:sound-modes", "DAC", usb = true)
        val pair = HeadphoneProfile.createManual("Calibrated pair", ManualHeadphoneTuning(
            left = HeadphoneTone(2.0, -1.0), right = HeadphoneTone(trebleDb = -3.0), balanceDb = 2.0))
        val store = HeadphoneProfileStore(File(temporary.root, "headphone_profiles.json"))
        HeadphonePreferences.saveManual(context, pair, endpoint)
        HeadphonePreferences.adjust(context, endpoint, 5.5, -6.0)
        for (enabled in listOf(false, true)) {
            HeadphonePreferences.setEnabled(context, endpoint, enabled)
            val before = store.load()
            for (preset in SoundMode.selectable) {
                HeadphonePreferences.setSoundMode(context, endpoint, preset)
                val saved = store.load()
                assertEquals(before.profiles, saved.profiles)
                assertEquals(before.bindings.getValue(endpoint.key).copy(soundMode = preset), saved.bindings.getValue(endpoint.key))
            }
        }
        HeadphonePreferences.setSoundMode(context, endpoint, SoundMode.BASS)
        HeadphonePreferences.adjust(context, endpoint, 1.0, -2.0)
        assertEquals(SoundMode.CUSTOM, store.load().bindings.getValue(endpoint.key).soundMode)
        assertEquals(pair, store.load().profiles.first { it.id == pair.id })
        HeadphonePreferences.delete(context, pair.id)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
    }
}
