package apincer.android.mmate.audio.headphones

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
    fun selectionConsentAdjustmentsAndDeletionArePersistedWithoutSpeakerFallback() {
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        val endpoint = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)
        val profile = HeadphoneProfileParser.parse(
            "Preamp: -5 dB\nFilter 1: ON PK Fc 1000 Hz Gain -2 dB Q 1",
            "My headphones", "model.txt"
        )
        HeadphonePreferences.import(context, profile)
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
}
