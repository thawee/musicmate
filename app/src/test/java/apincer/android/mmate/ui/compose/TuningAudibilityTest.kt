package apincer.android.mmate.ui.compose

import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TuningAudibilityTest {
    private val profile = HeadphoneProfile.createManual("Headphones", ManualHeadphoneTuning())
    private val wired = HeadphoneEndpoint("wired", "Wired headphones", false, wired = true)
    private val usb = HeadphoneEndpoint("usb:test", "DAC", true)

    @Test fun missingRouteAndMissingProfileExplainUnavailableProcessing() {
        assertEquals(TuningAudibility("Unavailable", "Connect a compatible output"), tuningAudibility(HeadphoneStatus()))
        assertEquals(TuningAudibility("Unavailable", "Select a profile to enable correction"),
            tuningAudibility(HeadphoneStatus(wired)))
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
        assertEquals("Active", tuningAudibility(HeadphoneStatus(wired, binding, profile, active = true)).label)
    }

    @Test fun usbWithoutConsentExplainsHowToEnableRatherThanClaimingActive() {
        assertEquals(TuningAudibility("Bypassed", "Enable USB EQ to allow sample processing"),
            tuningAudibility(HeadphoneStatus(usb, HeadphoneBinding(profile.id), profile)))
    }

    @Test fun bypassRetainsTheSavedConfiguration() {
        assertEquals(TuningAudibility("Bypassed", "Your profile and sound mode are saved"),
            tuningAudibility(HeadphoneStatus(wired, HeadphoneBinding(profile.id), profile)))
    }
}
