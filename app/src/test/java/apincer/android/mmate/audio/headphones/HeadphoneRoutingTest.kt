package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.SoundMode
import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.DspChain
import apincer.android.mmate.audio.dsp.PeqBand
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class HeadphoneRoutingTest {
    private fun assertSettingsEqual(expected: CarDspSettings?, actual: CarDspSettings?) {
        requireNotNull(expected)
        requireNotNull(actual)
        assertEquals(expected.bands, actual.bands)
        assertEquals(expected.leftBands, actual.leftBands)
        assertEquals(expected.rightBands, actual.rightBands)
        assertEquals(expected.monoBands, actual.monoBands)
        assertEquals(expected.requestedPreampDb, actual.requestedPreampDb, 0.0)
        assertEquals(expected.balanceDb, actual.balanceDb, 0.0)
        assertEquals(expected.compress, actual.compress)
        assertEquals(expected.crossfeed, actual.crossfeed)
    }


    @Test
    fun customComparisonPreservesCalibrationNightPreampAndUsbConsentWithoutPersistence() {
        val endpoint = HeadphoneEndpoint.of(listOf(usb))!!
        val custom = HeadphoneBinding(profile.id, enabled = true, usbEqAllowed = true,
            bassDb = 3.0, trebleDb = -2.0, nightListening = true, crossfeed = true)
        bind(endpoint, custom)
        HeadphoneDsp.updateDevices(listOf(usb))
        val original = HeadphoneDsp.current.settings!!
        HeadphoneDsp.setCustomComparison(endpoint.key, true)
        assertTrue(HeadphoneDsp.state.value.comparingCustom)
        assertEquals(custom, HeadphoneDsp.state.value.binding)
        assertEquals(profile.bands, HeadphoneDsp.current.settings!!.bands)
        assertEquals(original.requestedPreampDb, HeadphoneDsp.current.settings!!.requestedPreampDb, 0.0)
        assertTrue(HeadphoneDsp.current.settings!!.compress)
        assertTrue(HeadphoneDsp.current.settings!!.crossfeed)
        HeadphoneDsp.updateDevices(emptyList())
        HeadphoneDsp.updateDevices(listOf(usb))
        assertFalse(HeadphoneDsp.state.value.comparingCustom)
        assertSettingsEqual(original, HeadphoneDsp.current.settings)
        HeadphoneDsp.setCustomComparison(endpoint.key, true)
        bind(endpoint, custom.copy(usbEqAllowed = false))
        assertFalse(HeadphoneDsp.state.value.comparingCustom)
        assertNull(HeadphoneDsp.current.settings)
        HeadphoneDsp.setCustomComparison(endpoint.key, true)
        assertNull(HeadphoneDsp.current.settings)
    }

    @Test
    fun trimPreviewIsImmediateAndClearsOnCancellationSettingsAndRouteChanges() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        val binding = HeadphoneBinding(profile.id, enabled = true, bassDb = 1.0, nightListening = true)
        bind(endpoint, binding)
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        val saved = HeadphoneDsp.current.settings
        HeadphoneDsp.previewCustomTrims(endpoint.key, 4.0, -3.0)
        assertEquals(4.0, HeadphoneDsp.state.value.binding.bassDb, 0.0)
        assertNotEquals(saved!!.bands, HeadphoneDsp.current.settings!!.bands)
        HeadphoneDsp.clearCustomPreview(endpoint.key)
        assertSettingsEqual(saved, HeadphoneDsp.current.settings)
        HeadphoneDsp.previewCustomTrims(endpoint.key, 4.0, -3.0)
        bind(endpoint, binding.copy(trebleDb = 2.0))
        assertEquals(1.0, HeadphoneDsp.state.value.binding.bassDb, 0.0)
        HeadphoneDsp.setCustomComparison("other", true)
        assertFalse(HeadphoneDsp.state.value.comparingCustom)
        HeadphoneDsp.previewCustomTrims(endpoint.key, 4.0, -3.0)
        HeadphoneDsp.updateDevices(emptyList())
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        assertEquals(1.0, HeadphoneDsp.state.value.binding.bassDb, 0.0)
    }

    @Test
    fun comparisonKeepsManualStereoCorrectionAndDoesNotAlterNamedModes() {
        val manual = HeadphoneProfile.createManual("Manual", ManualHeadphoneTuning(
            left = HeadphoneTone(2.0, -1.0), right = HeadphoneTone(trebleDb = -3.0), balanceDb = 2.0))
        val binding = HeadphoneBinding(bassDb = 3.0, nightListening = true)
        val correction = manual.manual!!.settings()
        val comparing = HeadphoneDsp.settingsFor(manual, binding, comparingCustom = true)
        assertEquals(correction.bands, comparing.bands)
        assertEquals(correction.leftBands, comparing.leftBands)
        assertEquals(correction.rightBands, comparing.rightBands)
        assertEquals(correction.balanceDb, comparing.balanceDb, 0.0)
        assertEquals(correction.monoBands, comparing.monoBands)
        assertTrue(comparing.compress)
        val warm = binding.copy(soundMode = SoundMode.RELAX)
        assertSettingsEqual(HeadphoneDsp.settingsFor(manual, warm), HeadphoneDsp.settingsFor(manual, warm, true))
    }

    @Test
    fun namedBassUsesTheSameCurveAsSpeakers() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.BASS))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        assertEquals(profile.bands + SoundMode.BASS.bands(),
            HeadphoneDsp.current.settings!!.bands)
    }

    @Test
    fun defaultCustomRetainsFullLegacyHeadphoneTrims() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, bassDb = 6.0, trebleDb = -6.0))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        assertEquals(profile.bands + listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 6.0, 0.707),
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, -6.0, 0.707)
        ), HeadphoneDsp.current.settings!!.bands)
    }

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
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.VOCAL))
        assertNull(HeadphoneDsp.current.settings)
        bind(endpoint, HeadphoneBinding(profile.id, enabled = true, usbEqAllowed = true, soundMode = SoundMode.VOCAL))
        assertEquals(profile.bands + SoundMode.VOCAL.bands(), HeadphoneDsp.current.settings!!.bands)
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

    @Test
    fun soundModesLayerOverImportedAndManualCalibrationWithoutChangingIt() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        val manual = HeadphoneProfile.createManual("Calibrated pair", ManualHeadphoneTuning(
            left = HeadphoneTone(4.0, -2.0), right = HeadphoneTone(2.0, 1.0), balanceDb = -3.0))
        for (calibration in listOf(profile, manual)) {
            val baseline = calibration.manual?.settings() ?: CarDspSettings(calibration.bands, requestedPreampDb = calibration.preampDb)
            for (preset in SoundMode.entries.filter { it != SoundMode.CUSTOM }) {
                val library = HeadphoneLibrary(listOf(calibration), mapOf(endpoint.key to HeadphoneBinding(
                    calibration.id, enabled = true, bassDb = -6.0, trebleDb = 6.0, soundMode = preset)))
                HeadphoneDsp.updateLibrary(library)
                HeadphoneDsp.updateDevices(listOf(bluetooth))
                val actual = HeadphoneDsp.current.settings!!
                assertEquals(baseline.bands + preset.bands(), actual.bands)
                assertEquals(baseline.leftBands, actual.leftBands)
                assertEquals(baseline.rightBands, actual.rightBands)
                assertEquals(baseline.monoBands, actual.monoBands)
                assertEquals(baseline.balanceDb, actual.balanceDb, 0.0)
                assertEquals(baseline.requestedPreampDb, actual.requestedPreampDb, 0.0)
                assertEquals(preset.compresses, actual.compress)
                assertEquals(calibration, HeadphoneDsp.state.value.profile)
                if (calibration == manual && preset == SoundMode.BASS) {
                    val chain = DspChain(actual.bands, 48_000, 2, actual.requestedPreampDb,
                        actual.leftBands, actual.rightBands, actual.balanceDb, actual.monoBands)
                    val correctionOnly = DspChain(baseline.bands, 48_000, 2, baseline.requestedPreampDb,
                        baseline.leftBands, baseline.rightBands, baseline.balanceDb, baseline.monoBands)
                    assertTrue("Headroom must cover calibration plus sound mode", chain.preampDb < correctionOnly.preampDb)
                }
            }
        }
    }

    @Test
    fun eachOutputUsesItsOwnSoundMode() {
        val btEndpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        val usbEndpoint = HeadphoneEndpoint.of(listOf(usb))!!
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(
            btEndpoint.key to HeadphoneBinding(profile.id, enabled = true, soundMode = SoundMode.BASS),
            usbEndpoint.key to HeadphoneBinding(profile.id, enabled = true, usbEqAllowed = true, soundMode = SoundMode.VOCAL))))
        for ((device, preset) in listOf(bluetooth to SoundMode.BASS, usb to SoundMode.VOCAL,
            bluetooth to SoundMode.BASS)) {
            HeadphoneDsp.updateDevices(listOf(device))
            assertEquals(profile.bands + preset.bands(), HeadphoneDsp.current.settings!!.bands)
        }
    }

    private fun bind(endpoint: HeadphoneEndpoint, binding: HeadphoneBinding) =
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to binding)))

    @Test
    fun soundModesAndNightWorkWithoutCorrectionOnEachHeadphoneRoute() {
        val wired = Triple(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "Headphones")
        for (device in listOf(bluetooth, wired, usb)) {
            val endpoint = HeadphoneEndpoint.of(listOf(device))!!
            for (mode in SoundMode.selectable) {
                val binding = HeadphoneBinding(enabled = true, usbEqAllowed = endpoint.usb,
                    soundMode = mode, nightListening = true)
                HeadphoneDsp.updateLibrary(HeadphoneLibrary(bindings = mapOf(endpoint.key to binding)))
                HeadphoneDsp.updateDevices(listOf(device))
                val settings = HeadphoneDsp.current.settings!!
                assertTrue(HeadphoneDsp.state.value.active)
                assertNull(HeadphoneDsp.state.value.profile)
                assertSame(settings, CarDsp.effectiveTarget())
                assertEquals(mode.bands(), settings.bands)
                assertTrue(settings.compress)
                assertEquals(0.0, settings.requestedPreampDb, 0.0)
                assertTrue(settings.leftBands.isEmpty())
                assertTrue(settings.rightBands.isEmpty())
            }
            if (endpoint.usb) {
                HeadphoneDsp.updateLibrary(HeadphoneLibrary(bindings = mapOf(endpoint.key to
                    HeadphoneBinding(enabled = true, soundMode = SoundMode.VOCAL, nightListening = true))))
                assertFalse(HeadphoneDsp.state.value.active)
                assertNull(CarDsp.effectiveTarget())
            }
        }
    }

    @Test
    fun customPreviewAndComparisonWorkWithoutCorrectionAndKeepNight() {
        val endpoint = HeadphoneEndpoint.of(listOf(bluetooth))!!
        val binding = HeadphoneBinding(enabled = true, bassDb = 3.0, trebleDb = -2.0, nightListening = true)
        HeadphoneDsp.updateLibrary(HeadphoneLibrary(bindings = mapOf(endpoint.key to binding)))
        HeadphoneDsp.updateDevices(listOf(bluetooth))
        val original = HeadphoneDsp.current.settings!!
        assertEquals(HeadphoneTone(bassDb = 3.0, trebleDb = -2.0).bands(), original.bands)
        HeadphoneDsp.setCustomComparison(endpoint.key, true)
        assertTrue(HeadphoneDsp.state.value.comparingCustom)
        assertTrue(HeadphoneDsp.current.settings!!.bands.isEmpty())
        assertTrue(HeadphoneDsp.current.settings!!.compress)
        HeadphoneDsp.setCustomComparison(endpoint.key, false)
        assertSettingsEqual(original, HeadphoneDsp.current.settings)
        HeadphoneDsp.previewCustomTrims(endpoint.key, 5.0, -4.0)
        assertEquals(HeadphoneTone(bassDb = 5.0, trebleDb = -4.0).bands(), HeadphoneDsp.current.settings!!.bands)
        HeadphoneDsp.clearCustomPreview(endpoint.key)
        assertSettingsEqual(original, HeadphoneDsp.current.settings)
    }
}
