package apincer.android.mmate.audio.calibration

import android.Manifest
import android.content.Context
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
    private val recorder: SweepCapture
) {
    constructor(context: Context, scope: CoroutineScope) : this(context, scope, SweepRecorder(context.applicationContext))

    private val app = context.applicationContext
    private val sweep = LogSweep()
    private val analyzer by lazy { TakeAnalyzer(Deconvolver(sweep)) }
    private val measurements = MeasurementSession()
    /** Set from the first tap on Save until Done, so a second tap cannot save twice. */
    private var saving = false
    /** Taken while the sweep plays, so the saved profile names the car or speaker it measured. */
    private var identity: CarRoute.Identity? = null

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
        measurements.clear()
        current.value = Step.Position(0)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun measure() {
        val position = current.value as? Step.Position ?: return
        if (position.measuring) return
        current.value = position.copy(measuring = true, problem = null)
        scope.launch {
            val take = withContext(Dispatchers.Default) {
                val capture = attempt("Take") { recorder.capture(sweep.toPcm16()) { !isActive } }
                    ?: return@withContext Take.Rejected(Take.Reason.CAPTURE, MIC_FAILED)
                capture.problem?.let { Take.Rejected(Take.Reason.CAPTURE, it) } ?: analyzer.analyze(capture.recording)
            }
            when (take) {
                is Take.Rejected -> current.value = position.copy(measuring = false, problem = advice(take))
                is Take.Accepted -> {
                    if (!measurements.add(take)) {
                        current.value = position.copy(measuring = false, problem = "Background noise affected too many frequencies across the measured spots. Quieten the engine or fan and retry this spot.")
                        return@launch
                    }
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
                PeqFitter.fit(TakeAnalyzer.powerAverage(takes.map { it.responseDb }), TakeAnalyzer.reliableFromHz(takes), TakeAnalyzer.unreliableBands(takes))
            }
        }
        if (fit == null) {
            // The five takes stay; measuring the last spot again fits them once more
            current.value = Step.Position(POSITIONS.lastIndex, problem = FIT_FAILED)
            return
        }
        identity = attempt("Speaker name") { CarRoute.identity(app) }
        current.value = Step.Result(fit, identity?.name)
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
                name = name.ifBlank { car?.name ?: "My speaker" },
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
            val store = CarDspPreferences.profiles(app)
            val saved = attempt("Save") {
                withContext(Dispatchers.IO) { store.save(profile) }
                CarDspPreferences.setActiveProfile(app, profile)
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
            attempt("Saving the check") { withContext(Dispatchers.IO) { store.save(checked) } }
            current.value = Step.Done(checked)
            saving = false
        }
    }

    private fun advice(take: Take.Rejected): String = when (take.reason) {
        Take.Reason.CLIPPED -> "Too loud for the microphone. Turn the volume down a little and measure again."
        Take.Reason.TOO_QUIET -> "${take.detail}. Turn the volume up, or quieten the room (in a car, the engine and fan), and measure again."
        Take.Reason.NO_RESPONSE -> "${take.detail}. Check the volume and that the phone is playing through the speaker."
        Take.Reason.TOO_SHORT -> "${take.detail}. Measure again; if it repeats, reconnect Android Auto."
        Take.Reason.CAPTURE -> take.detail
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
