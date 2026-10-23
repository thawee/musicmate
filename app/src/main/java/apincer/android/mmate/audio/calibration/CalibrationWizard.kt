package apincer.android.mmate.audio.calibration

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.audio.dsp.DspChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The calibration steps (docs/technical/CAR_AUDIO_DSP.md, section 3): five positions, fit, save,
 * verify. Measuring and analysis run on [Dispatchers.Default]; [step] is what the screen shows.
 */
class CalibrationWizard(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val recorder = SweepRecorder(app)
    private val sweep = LogSweep()
    private val analyzer by lazy { TakeAnalyzer(Deconvolver(sweep)) }
    private val takes = mutableListOf<Take.Accepted>()
    /** Set from the first tap on Save until Done, so a second tap cannot save twice. */
    private var saving = false

    sealed interface Step {
        data object Checklist : Step
        data class Position(val index: Int, val measuring: Boolean = false, val problem: String? = null) : Step
        data object Fitting : Step
        /** [carName] is the connected car's Bluetooth name, offered as the profile name. */
        class Result(val fit: FitResult, val carName: String?) : Step
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
        takes.clear()
        current.value = Step.Position(0)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun measure() {
        val position = current.value as? Step.Position ?: return
        if (position.measuring) return
        current.value = position.copy(measuring = true, problem = null)
        scope.launch {
            val take = withContext(Dispatchers.Default) {
                val capture = recorder.capture(sweep.toPcm16()) { !isActive }
                capture.problem?.let { Take.Rejected(Take.Reason.NO_RESPONSE, it) } ?: analyzer.analyze(capture.recording)
            }
            when (take) {
                is Take.Rejected -> current.value = position.copy(measuring = false, problem = advice(take))
                is Take.Accepted -> {
                    // Wireless Android Auto latency is unmeasured; the peak search allows 2.5 s
                    Log.i(TAG, "take ${takes.size + 1}: delay %.3f s".format(take.delaySeconds))
                    takes += take
                    if (takes.size < POSITIONS.size) current.value = Step.Position(takes.size) else fit()
                }
            }
        }
    }

    private suspend fun fit() {
        current.value = Step.Fitting
        val fit = withContext(Dispatchers.Default) {
            PeqFitter.fit(TakeAnalyzer.powerAverage(takes.map { it.responseDb }), TakeAnalyzer.reliableFromHz(takes))
        }
        val car = withTimeoutOrNull(2_000) { HeadsetLink.connected(app) }
        current.value = Step.Result(fit, car?.name)
    }

    /** Saves the profile, makes it the active correction, then measures once more through it. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun saveAndVerify(name: String) {
        val result = current.value as? Step.Result ?: return
        if (saving) return
        saving = true
        scope.launch {
            val car = withTimeoutOrNull(2_000) { HeadsetLink.connected(app) }
            val fit = result.fit
            val profile = CarProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { car?.name ?: "My car" },
                createdAtMillis = System.currentTimeMillis(),
                bluetoothName = car?.name,
                bluetoothAddress = car?.address,
                bands = fit.bands,
                fromHz = fit.fromHz,
                measuredDb = fit.measuredDb.toList(),
                targetDb = fit.targetDb.toList(),
                predictedDb = fit.predictedDb.toList(),
                micSource = recorder.micSource,
                approximate = !recorder.unprocessed
            )
            val store = CarDspPreferences.profiles(app)
            withContext(Dispatchers.IO) { store.save(profile) }
            CarDspPreferences.setActiveProfile(app, profile)
            current.value = Step.Verifying(profile)

            val verdict = withContext(Dispatchers.Default) {
                val chain = DspChain(fit.bands, sweep.sampleRate, 1)
                val frame = DoubleArray(1)
                val corrected = DoubleArray(sweep.length) {
                    frame[0] = sweep.samples[it]
                    chain.process(frame)
                    frame[0]
                }
                val capture = recorder.capture(sweep.toPcm16(corrected)) { !isActive }
                val take = if (capture.problem == null) analyzer.analyze(capture.recording) else null
                (take as? Take.Accepted)?.let { PeqFitter.verify(fit, it.responseDb) }
            }
            val checked = profile.copy(verified = verdict?.passed, worstDeviationDb = verdict?.worstDeviationDb)
            withContext(Dispatchers.IO) { store.save(checked) }
            current.value = Step.Done(checked)
            saving = false
        }
    }

    private fun advice(take: Take.Rejected): String = when (take.reason) {
        Take.Reason.CLIPPED -> "Too loud for the microphone. Turn the car volume down a little and measure again."
        Take.Reason.TOO_QUIET -> "${take.detail}. Turn the volume up, or switch off the engine or fan, and measure again."
        Take.Reason.NO_RESPONSE -> "${take.detail}. Check the volume and that the phone is playing through the car."
        Take.Reason.TOO_SHORT -> "${take.detail}. Measure again; if it repeats, reconnect Android Auto."
    }

    companion object {
        /** Around the driver's head at ear height, about 15 cm apart. */
        val POSITIONS = listOf("Centre", "Left", "Right", "Forward", "Back")
        private const val TAG = "CarCalibration"
    }
}
