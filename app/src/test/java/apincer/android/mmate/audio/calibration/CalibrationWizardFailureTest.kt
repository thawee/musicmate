package apincer.android.mmate.audio.calibration

import android.content.Context
import android.util.Log
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.audio.dsp.CarRoute
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections

/** Android audio and storage can fail mid-calibration; the wizard must offer a retry, never crash. */
class CalibrationWizardFailureTest {
    @get:Rule val temporary = TemporaryFolder()

    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> crashes += error })
    private val recorder = mockk<SweepCapture> {
        every { unprocessed } returns true
        every { micSource } returns "UNPROCESSED"
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        mockkObject(CarRoute.Companion)
        every { CarRoute.identity(any()) } returns null
        mockkObject(CarDspPreferences)
        every { CarDspPreferences.setActiveProfile(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkAll()
    }

    @Test
    fun aMicrophoneFailureBecomesARetryableProblem() {
        every { recorder.capture(any(), any()) } throws IllegalStateException("startRecording() called on an uninitialized AudioRecord")
        val wizard = wizard(temporary.root)
        wizard.begin()
        wizard.measure()
        val step = await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring } as CalibrationWizard.Step.Position
        assertNotNull(step.problem)
        assertEquals(emptyList<Throwable>(), crashes)
    }

    @Test
    fun aCaptureProblemIsShownWithItsOwnAdvice() {
        every { recorder.capture(any(), any()) } returns SweepRecorder.Capture(DoubleArray(0), "Play a song through the speaker, then measure again.")
        val wizard = wizard(temporary.root)
        wizard.begin()
        wizard.measure()
        val step = await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring } as CalibrationWizard.Step.Position
        assertEquals("Play a song through the speaker, then measure again.", step.problem)
    }

    @Test
    fun aFailedSaveReturnsToTheResultAndCanBeRetried() {
        every { recorder.capture(any(), any()) } returns cleanCapture()
        val notADirectory = temporary.newFile("files")
        val wizard = wizard(notADirectory)
        measureAllSpots(wizard)
        wizard.saveAndVerify("Car")
        val failed = await(wizard) { it is CalibrationWizard.Step.Result && it.problem != null }
        assertTrue(failed is CalibrationWizard.Step.Result)
        assertEquals(emptyList<Throwable>(), crashes)

        notADirectory.delete()
        notADirectory.mkdirs()
        wizard.saveAndVerify("Car")
        assertTrue(await(wizard) { it is CalibrationWizard.Step.Done } is CalibrationWizard.Step.Done)
    }

    @Test
    fun aFailedCheckSweepStillFinishesWithTheSavedCorrection() {
        var calls = 0
        every { recorder.capture(any(), any()) } answers {
            if (++calls > CalibrationWizard.POSITIONS.size) throw UnsupportedOperationException("Cannot create AudioRecord")
            cleanCapture()
        }
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        wizard.saveAndVerify("Car")
        val done = await(wizard) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done
        assertEquals(null, done.profile.verified)
        assertEquals(listOf(done.profile.id), CarProfileStore(File(temporary.root, "car_profiles")).list().map { it.id })
        assertEquals(emptyList<Throwable>(), crashes)
    }

    private fun measureAllSpots(wizard: CalibrationWizard) {
        wizard.begin()
        for (index in CalibrationWizard.POSITIONS.indices) {
            await(wizard) { it is CalibrationWizard.Step.Position && it.index == index && !it.measuring }
            wizard.measure()
        }
        await(wizard) { it is CalibrationWizard.Step.Result }
    }

    private fun await(wizard: CalibrationWizard, done: (CalibrationWizard.Step) -> Boolean): CalibrationWizard.Step = runBlocking {
        withTimeoutOrNull(60_000) { wizard.step.first { done(it) || crashes.isNotEmpty() } }
            ?: throw AssertionError("Stuck at ${describe(wizard.step.value)}, crashes $crashes")
    }

    private fun describe(step: CalibrationWizard.Step) = when (step) {
        is CalibrationWizard.Step.Position -> step.toString()
        is CalibrationWizard.Step.Result -> "Result(problem=${step.problem})"
        else -> step::class.simpleName
    }

    private fun wizard(filesDir: File) = CalibrationWizard(context(filesDir), scope, recorder)

    private fun context(filesDir: File): Context {
        val app = mockk<Context> { every { this@mockk.filesDir } returns filesDir }
        return mockk { every { applicationContext } returns app }
    }

    /** A flat speaker recorded the way [SweepRecorder] lays it out: the sweep 0.5 s in, 13.5 s long. */
    private fun cleanCapture(): SweepRecorder.Capture {
        val rate = sweep.sampleRate
        val recording = DoubleArray((rate * SweepRecorder.TOTAL_SECONDS).toInt())
        sweep.samples.copyInto(recording, (rate * SweepRecorder.LEAD_SECONDS).toInt())
        return SweepRecorder.Capture(recording, null)
    }

    private companion object {
        val sweep = LogSweep()
    }
}
