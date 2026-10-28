package apincer.android.mmate.audio.headphones

import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.PeqBand
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class HeadphoneRoutingTest {
    private val bluetooth = Triple(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "AA:BB:CC:DD:EE:01", "My headphones")
    private val usb = Triple(AudioDeviceInfo.TYPE_USB_DEVICE, "card=1;device=0", "My DAC")
    private val profile = HeadphoneProfile("model", "My model", "model.txt", -5.0, listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, 3.0, 1.0)))

    @After
    fun reset() {
        HeadphoneDsp.updateLibrary(HeadphoneLibrary())
        HeadphoneDsp.updateDevices(emptyList())
        CarDsp.target = null
        CarDsp.updateRoute(emptyList())
    }

    @Test
    fun usbRequiresBothProfileEnableAndExplicitProcessingConsent() {
        val endpoint = HeadphoneEndpoint.of(listOf(usb))!!
        HeadphoneDsp.updateDevices(listOf(usb))
        assertTrue(HeadphoneDsp.current.handlesRoute)
        assertNull(HeadphoneDsp.current.settings)
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true))
        assertNull(HeadphoneDsp.current.settings)
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, usbEqAllowed = true))
        assertEquals(profile.bands, HeadphoneDsp.current.settings!!.bands)
        assertEquals(-5.0, HeadphoneDsp.current.settings!!.requestedPreampDb, 0.0)
        assertFalse(HeadphoneDsp.current.settings!!.compress)
    }

    @Test
    fun bluetoothAssignmentCannotLeakIntoAnotherDeviceOrAndroidAuto() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        assertNotNull(HeadphoneDsp.current.settings)
        HeadphoneDsp.updateDevices(listOf(bluetooth.copy(second = "OTHER")))
        assertFalse(HeadphoneDsp.current.handlesRoute)
        assertNull(HeadphoneDsp.current.settings)
        HeadphoneDsp.updateDevices(listOf(Triple(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "auto", "Car")))
        assertFalse(HeadphoneDsp.current.handlesRoute)
        assertNull(HeadphoneDsp.current.settings)
    }

    @Test
    fun bypassedOrMissingHeadphoneProfilesNeverFallBackToSpeakerEq() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        CarDsp.target = CarDspSettings(listOf(PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 5.0, 0.707)))
        CarDsp.updateRoute(listOf(mockk<AudioDeviceInfo> {
            every { type } returns bluetooth.first
            every { address } returns bluetooth.second!!
            every { productName } returns bluetooth.third!!
        }))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        bind(endpoint, HeadphoneBinding(profile.id, enabled = false))
        assertTrue(HeadphoneDsp.current.handlesRoute)
        assertNull(CarDsp.effectiveTarget())
        bind(endpoint, HeadphoneBinding("missing", enabled = true))
        assertTrue(HeadphoneDsp.current.handlesRoute)
        assertNull(CarDsp.effectiveTarget())
    }

    @Test
    fun routePublicationKeepsEligibilityAndProfileInOneSnapshot() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        val before = HeadphoneDsp.current
        val dac = mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_USB_DEVICE
            every { address } answers {
                assertSame(before, HeadphoneDsp.current)
                assertTrue(CarDsp.routeEligible)
                assertSame(before.settings, CarDsp.effectiveTarget())
                "new-usb"
            }
            every { productName } returns "Other DAC"
        }
        CarDsp.updateRoute(listOf(dac))
        assertFalse(CarDsp.routeEligible)
        assertTrue(HeadphoneDsp.current.handlesRoute)
        assertNull(CarDsp.effectiveTarget())
    }

    @Test
    fun unchangedRouteDoesNotRestartTheDspCrossfade() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, bassDb = 2.0, trebleDb = -1.0))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        val settings = HeadphoneDsp.current.settings!!
        assertEquals(profile.bands.size + 2, settings.bands.size)
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to HeadphoneBinding(profile.id, enabled = true, bassDb = 2.0, trebleDb = -1.0))))
        assertSame(settings, HeadphoneDsp.current.settings)
    }

    @Test
    fun unknownOrMixedOutputsCannotUseAHeadphoneProfile() {
        assertNull(HeadphoneEndpoint.of(emptyList()))
        assertNull(HeadphoneEndpoint.of(listOf(bluetooth.copy(second = ""))))
        assertNull(HeadphoneEndpoint.of(listOf(bluetooth, Triple(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "", "Phone"))))
        HeadphoneDsp.updateDevices(listOf(Triple(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "Headphones")))
        assertTrue(HeadphoneDsp.current.handlesRoute)
        assertNull(HeadphoneDsp.current.settings)
    }

    private fun bind(endpoint: HeadphoneEndpoint, binding: HeadphoneBinding) =
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to binding)))
}
