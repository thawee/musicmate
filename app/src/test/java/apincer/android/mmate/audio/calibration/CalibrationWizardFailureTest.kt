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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import kotlin.math.pow

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
        every { recorder.capture(any(), any(), any()) } throws IllegalStateException("startRecording() called on an uninitialized AudioRecord")
        val wizard = wizard(temporary.root)
        wizard.begin()
        wizard.measure()
        val step = await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring } as CalibrationWizard.Step.Position
        assertNotNull(step.problem)
        assertEquals(emptyList<Throwable>(), crashes)
    }

    @Test
    fun aCaptureProblemIsShownWithItsOwnAdvice() {
        every { recorder.capture(any(), any(), any()) } returns SweepRecorder.Capture(DoubleArray(0), "Play a song through the speaker, then measure again.")
        val wizard = wizard(temporary.root)
        wizard.begin()
        wizard.measure()
        val step = await(wizard) { it is CalibrationWizard.Step.Position && !it.measuring } as CalibrationWizard.Step.Position
        assertEquals("Play a song through the speaker, then measure again.", step.problem)
    }

    @Test
    fun aFailedSaveReturnsToTheResultAndCanBeRetried() {
        every { recorder.capture(any(), any(), any()) } returns cleanCapture()
        val notADirectory = temporary.newFile("files")
        val wizard = wizard(notADirectory)
        measureAllSpots(wizard)
        wizard.save("Car")
        val failed = await(wizard) { it is CalibrationWizard.Step.Result && it.problem != null }
        assertTrue(failed is CalibrationWizard.Step.Result)
        assertEquals(emptyList<Throwable>(), crashes)

        notADirectory.delete()
        notADirectory.mkdirs()
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        wizard.check()
        assertTrue(await(wizard) { it is CalibrationWizard.Step.Done } is CalibrationWizard.Step.Done)
    }

    @Test
    fun aFailedCheckSweepStillFinishesWithTheSavedCorrection() {
        var calls = 0
        every { recorder.capture(any(), any(), any()) } answers {
            if (++calls > CalibrationWizard.POSITIONS.size) throw UnsupportedOperationException("Cannot create AudioRecord")
            cleanCapture()
        }
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        wizard.check()
        val done = await(wizard) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done
        assertEquals(null, done.profile.verified)
        assertFalse(done.checkSkipped)
        assertEquals(listOf(done.profile.id), CarProfileStore(File(temporary.root, "car_profiles")).list().map { it.id })
        assertEquals(emptyList<Throwable>(), crashes)
    }

    @Test
    fun theCheckSweepWaitsForTheUserAndCanBeSkipped() {
        var calls = 0
        every { recorder.capture(any(), any(), any()) } answers { calls++; cleanCapture() }
        val checked = wizard(temporary.root)
        measureAllSpots(checked)
        checked.save("Car")
        await(checked) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        Thread.sleep(500)
        assertEquals(CalibrationWizard.POSITIONS.size, calls)
        assertTrue(checked.step.value is CalibrationWizard.Step.Verifying)
        assertFalse(checked.hasUnsavedWork)
        checked.check()
        val done = await(checked) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done
        assertEquals(CalibrationWizard.POSITIONS.size + 1, calls)
        assertEquals(true, done.profile.verified)
        assertEquals(true, CarProfileStore(File(temporary.root, "car_profiles")).list().single().verified)

        calls = 0
        val skipped = wizard(temporary.newFolder("skipped"))
        measureAllSpots(skipped)
        skipped.save("Car")
        await(skipped) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        skipped.skipCheck()
        val notChecked = await(skipped) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done
        assertEquals(CalibrationWizard.POSITIONS.size, calls)
        assertTrue(notChecked.checkSkipped)
        assertEquals(null, notChecked.profile.verified)
        assertEquals(emptyList<Throwable>(), crashes)
    }

    @Test
    fun aVoiceMicrophoneSavesACutsOnlyCorrectionTo4kHz() {
        every { recorder.unprocessed } returns false
        every { recorder.capture(any(), any(), any()) } returns cleanCapture()
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        val result = wizard.step.value as CalibrationWizard.Step.Result
        assertEquals(PeqFitter.VOICE_MIC_MAX_HZ, result.fit.toHz, 0.0)
        assertTrue(result.fit.bands.all { it.gainDb <= 0.0 })
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying }
        val saved = CarProfileStore(File(temporary.root, "car_profiles")).list().single()
        assertEquals(PeqFitter.VOICE_MIC_MAX_HZ, saved.toHz, 0.0)
        assertTrue(saved.approximate)
    }

    @Test
    fun anotherTargetRefitsTheSameSpotsAndIsSaved() {
        every { recorder.capture(any(), any(), any()) } returns cleanCapture()
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        val car = wizard.step.value as CalibrationWizard.Step.Result
        assertEquals(SpeakerTarget.CAR, car.target)
        wizard.chooseTarget(SpeakerTarget.FLAT)
        val flat = await(wizard) { (it as? CalibrationWizard.Step.Result)?.target == SpeakerTarget.FLAT } as CalibrationWizard.Step.Result
        assertTrue(flat.fit.targetDb.contentEquals(SpeakerTarget.FLAT.curve))
        assertTrue(flat.fit.bands != car.fit.bands)
        io.mockk.verify(exactly = CalibrationWizard.POSITIONS.size) { recorder.capture(any(), any(), any()) }
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying }
        assertEquals(SpeakerTarget.FLAT.id, CarProfileStore(File(temporary.root, "car_profiles")).list().single().targetId)
    }

    @Test
    fun tappingBackToTheShownTargetUndoesARefitStillRunning() {
        every { recorder.capture(any(), any(), any()) } returns cleanCapture()
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        wizard.chooseTarget(SpeakerTarget.ROOM)
        wizard.chooseTarget(SpeakerTarget.CAR)
        Thread.sleep(1_000)
        val result = wizard.step.value as CalibrationWizard.Step.Result
        assertEquals(SpeakerTarget.CAR, result.target)
        assertTrue(result.fit.targetDb.contentEquals(CarTarget.CURVE))
    }

    @Test
    fun eachSideIsFittedAndTheSideHeardFirstWaits() {
        // The right side arrives 1 ms later and 3 dB quieter; the check hears what the correction plays
        val played = mutableListOf<ShortArray>()
        every { recorder.capture(any(), any(), any()) } answers {
            played += firstArg<ShortArray>()
            room(firstArg(), secondArg(), rightDb = -3.0, rightLateSamples = 48)
        }
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        val stereo = requireNotNull((wizard.step.value as CalibrationWizard.Step.Result).stereo).correction
        assertEquals(1.0, stereo.leftDelayMs, 0.05)
        assertEquals(0.0, stereo.rightDelayMs, 0.0)
        assertEquals(3.0, stereo.balanceDb, 0.2)
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        assertEquals(stereo, CarProfileStore(File(temporary.root, "car_profiles")).list().single().stereo)
        wizard.check()
        val done = await(wizard) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done
        assertEquals(true, done.profile.verified)
        io.mockk.verify(exactly = CalibrationWizard.POSITIONS.size + 1) { recorder.capture(any(), 2, any()) }
        // The check stream is the corrected one: the left side starts 1 ms late and plays 3 dB down
        fun start(pcm: ShortArray, channel: Int) = (0 until pcm.size / 2).first { pcm[2 * it + channel] != 0.toShort() }
        fun rmsDb(pcm: ShortArray, channel: Int) =
            10 * kotlin.math.log10((0 until pcm.size / 2).sumOf { pcm[2 * it + channel].toDouble().pow(2) })
        val plain = played.first()
        val check = played.last()
        // The sweep fades in, so its quietest first samples round to zero once the left is turned down
        assertEquals(48.0, (start(check, 0) - start(plain, 0)).toDouble(), 6.0)
        assertEquals(-3.0, (rmsDb(check, 0) - rmsDb(check, 1)) - (rmsDb(plain, 0) - rmsDb(plain, 1)), 0.3)
    }

    @Test
    fun aSideWithNoSoundLeavesOneCorrectionForBoth() {
        every { recorder.capture(any(), any(), any()) } answers { room(firstArg(), secondArg(), rightDb = null) }
        val wizard = wizard(temporary.root)
        measureAllSpots(wizard)
        val result = wizard.step.value as CalibrationWizard.Step.Result
        assertEquals(null, result.stereo)
        wizard.save("Car")
        await(wizard) { it is CalibrationWizard.Step.Verifying && !it.measuring }
        assertEquals(null, CarProfileStore(File(temporary.root, "car_profiles")).list().single().stereo)
        wizard.check()
        assertEquals(true, (await(wizard) { it is CalibrationWizard.Step.Done } as CalibrationWizard.Step.Done).profile.verified)
        io.mockk.verify(exactly = 1) { recorder.capture(any(), 1, any()) }
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
    private fun cleanCapture(rightDb: Double? = 0.0, rightLateSamples: Int = 0) = room(StereoSweep(sweep).pcm(), 2, rightDb, rightLateSamples)

    /**
     * What the microphone hears when [pcm] plays: the left speaker as is, the right one
     * [rightLateSamples] later and [rightDb] louder (null for silent), over a faint noise floor.
     * Mono plays on both speakers.
     */
    private fun room(pcm: ShortArray, channels: Int, rightDb: Double? = 0.0, rightLateSamples: Int = 0): SweepRecorder.Capture {
        val rate = sweep.sampleRate
        val lead = (rate * SweepRecorder.LEAD_SECONDS).toInt()
        val frames = pcm.size / channels
        val random = java.util.Random(11)
        val recording = DoubleArray(lead + frames + (rate * SweepRecorder.TAIL_SECONDS).toInt()) { random.nextGaussian() * 1e-5 }
        val gain = rightDb?.let { 10.0.pow(it / 20) }
        for (n in 0 until frames) {
            recording[lead + n] += pcm[n * channels] / 32_767.0
            if (gain != null) recording[lead + rightLateSamples + n] += pcm[n * channels + channels - 1] / 32_767.0 * gain
        }
        return SweepRecorder.Capture(recording, null)
    }

    private companion object {
        val sweep = LogSweep()
    }
}
