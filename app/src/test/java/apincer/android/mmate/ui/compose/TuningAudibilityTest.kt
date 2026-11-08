package apincer.android.mmate.ui.compose

import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TuningAudibilityTest {
    private val profile = HeadphoneProfile.createManual("Headphones", ManualHeadphoneTuning())
    private val wired = HeadphoneEndpoint("wired", "Wired headphones", false, wired = true)
    private val usb = HeadphoneEndpoint("usb:test", "DAC", true)

    @Test fun missingRouteIsUnavailableButCorrectionIsOptional() {
        assertEquals(TuningAudibility("Unavailable", "Connect a compatible output"), tuningAudibility(HeadphoneStatus()))
        assertEquals(TuningAudibility("EQ off", "Your profile and sound mode are saved"),
            tuningAudibility(HeadphoneStatus(wired)))
    }

    @Test fun remotePlayerIsUnavailableEvenWhenLocalOutputIsActive() {
        assertEquals(TuningAudibility("Unavailable", "Not applied to the current player"),
            tuningAudibility(HeadphoneStatus(wired, HeadphoneBinding(enabled = true), active = true), "Living Room"))
    }

    @Test fun soundModeCanBeActiveWithoutCorrection() {
        assertEquals(TuningAudibility("EQ on", "Sound mode is applied without correction"),
            tuningAudibility(HeadphoneStatus(wired, HeadphoneBinding(enabled = true), active = true)))
    }

    @Test fun incompatibleProfileCannotAppearActive() {
        val speaker = HeadphoneEndpoint("speaker:built-in", "Phone speaker", false, speaker = true)
        assertEquals(CalibrationOutput.HEADPHONE, profile.output)
        assertEquals(TuningAudibility("Unavailable", "Select a profile compatible with this output"),
            tuningAudibility(HeadphoneStatus(speaker, HeadphoneBinding(profile.id, true), profile)))
    }

    @Test fun savedEnablementAloneDoesNotClaimAudibleProcessing() {
        val binding = HeadphoneBinding(profile.id, true)
        assertEquals("Unavailable", tuningAudibility(HeadphoneStatus(wired, binding, profile)).label)
        assertEquals("EQ on", tuningAudibility(HeadphoneStatus(wired, binding, profile, active = true)).label)
    }

    @Test fun usbWithoutConsentExplainsHowToEnableRatherThanClaimingActive() {
        assertEquals(TuningAudibility("EQ off", "Enable USB EQ to allow sample processing"),
            tuningAudibility(HeadphoneStatus(usb, HeadphoneBinding(profile.id), profile)))
    }

    @Test fun bypassRetainsTheSavedConfiguration() {
        assertEquals(TuningAudibility("EQ off", "Your profile and sound mode are saved"),
            tuningAudibility(HeadphoneStatus(wired, HeadphoneBinding(profile.id), profile)))
    }
}
