package apincer.android.mmate.audio.calibration

import android.Manifest
import android.content.Context
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphonePreferences
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import android.util.Log
import androidx.annotation.RequiresPermission
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.audio.dsp.CarRoute
import apincer.android.mmate.audio.dsp.DspChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The calibration steps (docs/technical/CAR_AUDIO_DSP.md, section 3): five positions, fit, save,
 * verify. Measuring and analysis run on [Dispatchers.Default]; [step] is what the screen shows.
 */
class CalibrationWizard internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val recorder: SweepCapture,
    val output: CalibrationOutput = CalibrationOutput.SPEAKER
) {
    constructor(context: Context, scope: CoroutineScope, output: CalibrationOutput = CalibrationOutput.SPEAKER) : this(context, scope, SweepRecorder(context.applicationContext), output)

    private val app = context.applicationContext
    private val sweep = LogSweep()
    private val analyzer by lazy { TakeAnalyzer(Deconvolver(sweep)) }
    private val measurements = MeasurementSession()
    private val takeGuidance = CalibrationTakeGuidance()
    /** Set from the first tap on Save until Done, so a second tap cannot save twice. */
    private var saving = false
    /** Taken while the sweep plays, so the saved profile names the car or speaker it measured. */
    private var identity: CarRoute.Identity? = null
    private var endpoint: HeadphoneEndpoint? = null
    private var rigTarget: RigTarget? = null
    private var fixtureName = "Headphone fixture/coupler"
    val microphones: List<SweepRecorder.Microphone> get() = (recorder as? SweepRecorder)?.microphones.orEmpty()
    fun selectMicrophone(id: Int) { if (current.value == Step.Checklist) (recorder as? SweepRecorder)?.selectMicrophone(id) }
    fun setTarget(target: RigTarget) { if (current.value == Step.Checklist) rigTarget = target }
    fun beginHeadphone(fixtureConfirmed: Boolean, fixtureDescription: String = "Headphone fixture/coupler") {
        require(fixtureDescription.isNotBlank() && fixtureDescription.length <= 100 && fixtureDescription.none { it.isISOControl() }) { "Name the fixture or coupler (up to 100 characters)." }
        fixtureName = fixtureDescription.trim()
        require(fixtureConfirmed && rigTarget != null) { "Confirm the fixture and import a target from the same rig first." }
        endpoint = HeadphoneEndpoint.current(app)
        require(endpoint?.supports(CalibrationOutput.HEADPHONE) == true) { "Connect the headphones before starting calibration." }
        begin()
    }

    sealed interface Step {
        data object Checklist : Step
        data class Position(val index: Int, val measuring: Boolean = false, val problem: String? = null, val noisyBandsHz: List<Double> = emptyList()) : Step
        data object Fitting : Step
        /** [carName] is the tuned car's or speaker's Bluetooth name, offered as the profile name; [problem] says why saving failed. */
        class Result(val fit: FitResult, val carName: String?, val problem: String? = null) : Step
        class Verifying(val profile: CarProfile) : Step
        class Done(val profile: CarProfile) : Step
    }

    private val current = MutableStateFlow<Step>(Step.Checklist)
    val step: StateFlow<Step> = current

    /** False when the phone records through its voice path, which makes the result approximate. */
    val unprocessedMic: Boolean get() = recorder.unprocessed

    /** True while leaving would throw away measured spots or a result not yet saved. */
    val hasUnsavedWork: Boolean
        get() = when (val s = current.value) {
            is Step.Position -> s.index > 0 || s.measuring
            Step.Fitting, is Step.Result, is Step.Verifying -> true
            else -> false
        }

    fun begin() {
        if (output == CalibrationOutput.SPEAKER) {
            endpoint = attempt("Output route") { HeadphoneEndpoint.current(app) }
            require(endpoint == null || endpoint?.supports(CalibrationOutput.SPEAKER) == true) { "Connect a speaker before starting calibration." }
        }
        if (output == CalibrationOutput.HEADPHONE && (rigTarget == null || endpoint == null)) return
        measurements.clear()
        takeGuidance.reset()
        (recorder as? SweepRecorder)?.beginSession()
        current.value = Step.Position(0)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun measure() {
        val position = current.value as? Step.Position ?: return
        if (position.measuring) return
        current.value = position.copy(measuring = true, problem = null)
        scope.launch {
            val take = withContext(Dispatchers.Default) {
                if (endpoint != null && HeadphoneEndpoint.current(app)?.key != endpoint?.key)
                    return@withContext Take.Rejected(Take.Reason.CAPTURE, "Output changed. Restart calibration with the same playback output.")
                val capture = attempt("Take") { recorder.capture(sweep.toPcm16()) { !isActive } }
                    ?: return@withContext Take.Rejected(Take.Reason.CAPTURE, MIC_FAILED)
                capture.problem?.let { Take.Rejected(Take.Reason.CAPTURE, it) } ?: analyzer.analyze(capture.recording)
            }
            when (take) {
                is Take.Rejected -> {
                    Log.i(TAG, "take rejected: ${take.reason}: ${take.detail}")
                    current.value = position.copy(measuring = false, problem = takeGuidance.advice(take))
                }
                is Take.Accepted -> {
                    if (!measurements.add(take)) {
                        current.value = position.copy(measuring = false, problem = "Background noise affected too many frequencies across the measured spots. Quieten the engine or fan and retry this spot.")
                        return@launch
                    }
                    takeGuidance.reset()
                    // Wireless Android Auto latency is unmeasured; the peak search allows 2.5 s
                    val takes = measurements.takes
                    val noisyBands = TakeAnalyzer.unreliableBands(takes)
                    Log.i(TAG, "take ${takes.size}: delay %.3f s".format(take.delaySeconds))
                    if (takes.size < POSITIONS.size) current.value = Step.Position(takes.size, noisyBandsHz = noisyBands) else fit()
                }
            }
        }
    }

    private suspend fun fit() {
        current.value = Step.Fitting
        val fit = attempt("Fit") {
            withContext(Dispatchers.Default) {
                val takes = measurements.takes
                PeqFitter.fit(TakeAnalyzer.powerAverage(takes.map { it.responseDb }),
                    maxOf(TakeAnalyzer.reliableFromHz(takes), if (output == CalibrationOutput.HEADPHONE) 100.0 else PeqFitter.MIN_HZ),
                    TakeAnalyzer.unreliableBands(takes), if (output == CalibrationOutput.HEADPHONE) requireNotNull(rigTarget).curve else CarTarget.CURVE)
            }
        }
        if (fit == null) {
            // The five takes stay; measuring the last spot again fits them once more
            current.value = Step.Position(POSITIONS.lastIndex, problem = FIT_FAILED)
            return
        }
        identity = attempt("Speaker name") { CarRoute.identity(app) }
        current.value = Step.Result(fit, endpoint?.name ?: identity?.name)
    }

    /** Saves the profile, makes it the active correction, then measures once more through it. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun saveAndVerify(name: String) {
        val result = current.value as? Step.Result ?: return
        if (saving) return
        saving = true
        scope.launch {
            val car = identity
            val fit = result.fit
            val profile = CarProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { endpoint?.name ?: car?.name ?: "My speaker" },
                createdAtMillis = System.currentTimeMillis(),
                bluetoothName = car?.name,
                bluetoothAddress = car?.address,
                kind = car?.kind,
                bands = fit.bands,
                fromHz = fit.fromHz,
                measuredDb = fit.measuredDb.toList(),
                targetDb = fit.targetDb.toList(),
                predictedDb = fit.predictedDb.toList(),
                micSource = recorder.micSource,
                approximate = !recorder.unprocessed,
                excludedBandsHz = fit.excludedBandsHz
            )
            val store by lazy { CarDspPreferences.profiles(app) }
            val saved = attempt("Save") {
                if (output == CalibrationOutput.HEADPHONE) {
                    val captured = requireNotNull(endpoint)
                    require(HeadphoneEndpoint.current(app)?.key == captured.key) { "Output changed; restart calibration." }
                    val headphone = HeadphoneProfile(profile.id, profile.name,
                        "Microphone; fixture: $fixtureName; target: ${requireNotNull(rigTarget).source}; mic: ${recorder.micSource}",
                        0.0, fit.bands, output = CalibrationOutput.HEADPHONE, method = CalibrationMethod.MICROPHONE)
                    withContext(Dispatchers.IO) { HeadphonePreferences.import(app, headphone) }
                    HeadphonePreferences.select(app, captured, headphone.id)
                    if (!captured.usb) HeadphonePreferences.setEnabled(app, captured, true)
                } else {
                    val captured = endpoint
                    if (captured != null) require(HeadphoneEndpoint.current(app)?.key == captured.key) { "Output changed; restart calibration." }
                    withContext(Dispatchers.IO) { store.save(profile) }
                    CarDspPreferences.setActiveProfile(app, profile)
                    if (captured != null) {
                        val converted = CorrectionProfiles.fromSpeaker(profile)
                        val legacy = CarDspPreferences.state.value
                        withContext(Dispatchers.IO) { HeadphonePreferences.import(app, converted) }
                        HeadphonePreferences.select(app, captured, converted.id)
                        HeadphonePreferences.adjust(app, captured, legacy.bassDb, legacy.trebleDb)
                        HeadphonePreferences.setSoundMode(app, captured, legacy.mode)
                        if (!captured.usb) HeadphonePreferences.setEnabled(app, captured, true)
                    }
                }
            }
            if (saved == null) {
                current.value = Step.Result(fit, result.carName, SAVE_FAILED)
                saving = false
                return@launch
            }
            current.value = Step.Verifying(profile)

            // A check sweep that cannot run leaves the saved correction unverified
            val verdict = attempt("Check sweep") {
                withContext(Dispatchers.Default) {
                    if (endpoint != null && HeadphoneEndpoint.current(app)?.key != endpoint?.key) return@withContext null
                    val chain = DspChain(fit.bands, sweep.sampleRate, 1)
                    val frame = DoubleArray(1)
                    val corrected = DoubleArray(sweep.length) {
                        frame[0] = sweep.samples[it]
                        chain.process(frame)
                        frame[0]
                    }
                    val capture = recorder.capture(sweep.toPcm16(corrected)) { !isActive }
                    val take = if (capture.problem == null) analyzer.analyze(capture.recording) else null
                    (take as? Take.Accepted)?.let { PeqFitter.verify(fit, it) }
                }
            }
            val checked = profile.copy(verified = verdict?.passed, worstDeviationDb = verdict?.worstDeviationDb)
            if (output == CalibrationOutput.SPEAKER) attempt("Saving the check") { withContext(Dispatchers.IO) { store.save(checked) } }
            current.value = Step.Done(checked)
            saving = false
        }
    }

    /** Android audio and storage can fail at any time; [block]'s failure is logged and returns null. */
    private inline fun <T> attempt(what: String, block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, "$what failed", failure)
        null
    }

    companion object {
        /** Around the listener's head at ear height, about 15 cm apart. */
        val POSITIONS = listOf("Centre", "Left", "Right", "Forward", "Back")
        private const val TAG = "CarCalibration"
        private const val MIC_FAILED = "The microphone could not record. Close apps that may be using it, such as a call or voice assistant, and measure again."
        private const val FIT_FAILED = "The correction could not be worked out from these measurements. Measure this spot again."
        private const val SAVE_FAILED = "The correction could not be saved. Check that the phone has free storage, then save again."
    }
}
