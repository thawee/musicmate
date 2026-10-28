package apincer.android.mmate.audio.headphones

import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.calibration.*
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.*
import org.junit.Test

class CorrectionProfilesTest {
    @Test fun speakerAndHeadphoneProfilesShareChoicesWithoutDuplicatingConvertedSpeakers() {
        val speaker = CarProfile("room", "Desk speakers", 1L, bands = emptyList(), fromHz = 100.0,
            measuredDb = emptyList(), targetDb = emptyList(), predictedDb = emptyList(), micSource = "UNPROCESSED", approximate = false)
        val converted = CorrectionProfiles.fromSpeaker(speaker)
        val listening = HeadphoneProfile.createManual("Earphones", ManualHeadphoneTuning())
        val choices = CorrectionProfiles.choices(listOf(speaker), listOf(converted, listening))
        assertEquals(2, choices.size)
        assertEquals(CalibrationOutput.SPEAKER, choices.first { it.id == converted.id }.output)
        assertEquals("Microphone", choices.first { it.id == converted.id }.method)
        assertEquals("Listening", choices.first { it.id == listening.id }.method)
        assertEquals(speaker.bands, converted.bands)
    }

    @Test fun speakerListeningCorrectionAppliesOnlyToItsAssignedOutput() {
        val device = Triple(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "", "Phone speaker")
        val endpoint = HeadphoneEndpoint.of(listOf(device))!!
        assertEquals(CalibrationOutput.SPEAKER, endpoint.output)
        assertTrue(endpoint.supports(CalibrationOutput.SPEAKER))
        assertFalse(endpoint.supports(CalibrationOutput.HEADPHONE))
        val tuning = ManualHeadphoneTuning(left = HeadphoneTone(bassDb = -3.0), right = HeadphoneTone(trebleDb = 2.0))
        val profile = HeadphoneProfile.createManual("Desk", tuning, CalibrationOutput.SPEAKER)
        try {
            HeadphoneDsp.updateLibrary(HeadphoneLibrary(listOf(profile), mapOf(endpoint.key to HeadphoneBinding(profile.id, true))))
            HeadphoneDsp.updateDevices(listOf(device))
            assertEquals(tuning.settings().leftBands, HeadphoneDsp.current.settings!!.leftBands)
            assertEquals(tuning.settings().rightBands, HeadphoneDsp.current.settings!!.rightBands)
            HeadphoneDsp.updateDevices(listOf(Triple(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "Pair")))
            assertNull(HeadphoneDsp.current.settings)
        } finally {
            HeadphoneDsp.updateLibrary(HeadphoneLibrary())
            HeadphoneDsp.updateDevices(emptyList())
        }
    }

    @Test fun transportsAllowExplicitSpeakerOrHeadphoneAssignmentWhileWiredHeadsetsDoNot() {
        for (device in listOf(Triple(AudioDeviceInfo.TYPE_USB_DEVICE, "card=2", "DAC"),
            Triple(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "AA:BB", "Bluetooth"))) {
            val endpoint = HeadphoneEndpoint.of(listOf(device))!!
            assertTrue(endpoint.supports(CalibrationOutput.SPEAKER))
            assertTrue(endpoint.supports(CalibrationOutput.HEADPHONE))
        }
        val wired = HeadphoneEndpoint.of(listOf(Triple(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "Pair")))!!
        assertFalse(wired.supports(CalibrationOutput.SPEAKER))
    }
}
