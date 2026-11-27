package apincer.android.mmate.audio.calibration

import android.content.Context
import android.util.Log
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphonePreferences
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class HeadphoneMicrophoneWizardTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app = mockk<Context>()
    private val context = mockk<Context> { every { applicationContext } returns app }
    private val recorder = mockk<SweepCapture> {
        every { unprocessed } returns true
        every { micSource } returns "USB fixture mic"
    }

    @After fun cleanup() { scope.cancel(); unmockkAll() }

    @Test fun requiresFixtureAndMatchingTarget() {
        val wizard = CalibrationWizard(context, scope, recorder, CalibrationOutput.HEADPHONE)
        try { wizard.beginHeadphone(true); fail() } catch (_: IllegalArgumentException) { }
        wizard.setTarget(RigTarget.parse("100,0\n8000,0", "Rig A"))
        try { wizard.beginHeadphone(false); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(wizard.step.value is CalibrationWizard.Step.Checklist)
        verify(exactly = 0) { recorder.capture(any(), any()) }
    }

    @Test fun rejectsSpeakerForHeadphoneFixture() {
        mockkObject(HeadphoneEndpoint.Companion)
        every { HeadphoneEndpoint.current(any()) } returns HeadphoneEndpoint("speaker:built-in", "Phone", false, speaker = true)
        val wizard = CalibrationWizard(context, scope, recorder, CalibrationOutput.HEADPHONE)
        wizard.setTarget(RigTarget.parse("100,0\n8000,0", "Rig A"))
        try { wizard.beginHeadphone(true); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(wizard.step.value is CalibrationWizard.Step.Checklist)
    }

    @Test fun rejectsChangedEndpointBeforeCapture() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        mockkObject(HeadphoneEndpoint.Companion)
        every { HeadphoneEndpoint.current(any()) } returns HeadphoneEndpoint("usb:a", "A", true)
        val wizard = CalibrationWizard(context, scope, recorder, CalibrationOutput.HEADPHONE)
        wizard.setTarget(RigTarget.parse("100,0\n8000,0", "Rig A"))
        wizard.beginHeadphone(true)
        every { HeadphoneEndpoint.current(any()) } returns HeadphoneEndpoint("usb:b", "B", true)
        wizard.measure()
        val result = await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring && it.problem != null }
        assertTrue((result as CalibrationWizard.Step.Position).problem!!.contains("Output changed"))
        verify(exactly = 0) { recorder.capture(any(), any()) }
    }

    @Test fun importsMicrophoneProfileForCapturedUsbEndpointWithoutEnablingUsb() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        mockkObject(HeadphoneEndpoint.Companion, HeadphonePreferences)
        val endpoint = HeadphoneEndpoint("usb:a", "Fixture headphones", true)
        every { HeadphoneEndpoint.current(any()) } returns endpoint
        val saved = slot<HeadphoneProfile>()
        every { HeadphonePreferences.import(any(), capture(saved)) } just runs
        every { HeadphonePreferences.select(any(), any(), any()) } just runs
        val sweep = LogSweep()
        val recording = DoubleArray((sweep.sampleRate * SweepRecorder.TOTAL_SECONDS).toInt())
        sweep.samples.copyInto(recording, (sweep.sampleRate * SweepRecorder.LEAD_SECONDS).toInt())
        every { recorder.capture(any(), any()) } returns SweepRecorder.Capture(recording, null)
        val wizard = CalibrationWizard(context, scope, recorder, CalibrationOutput.HEADPHONE)
        wizard.setTarget(RigTarget.parse("100,0\n8000,0", "Rig A target.csv"))
        wizard.beginHeadphone(true)
        repeat(5) { index ->
            await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring && it.index == index }
            wizard.measure()
        }
        await(wizard) { it is CalibrationWizard.Step.Result }
        wizard.save("My rig")
        await(wizard) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        wizard.check()
        await(wizard) { it is CalibrationWizard.Step.Done }
        assertEquals(CalibrationMethod.MICROPHONE, saved.captured.method)
        assertEquals(CalibrationOutput.HEADPHONE, saved.captured.output)
        assertTrue(saved.captured.source.contains("Rig A target.csv"))
        assertTrue(saved.captured.source.contains("USB fixture mic"))
        verify { HeadphonePreferences.select(app, endpoint, saved.captured.id) }
        verify(exactly = 0) { HeadphonePreferences.setEnabled(any(), any(), any()) }
    }

    private fun await(wizard: CalibrationWizard, predicate: (CalibrationWizard.Step) -> Boolean) = runBlocking {
        withTimeout(15_000) { wizard.step.first(predicate) }
    }
}
