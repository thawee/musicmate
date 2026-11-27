package apincer.android.mmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import apincer.android.mmate.audio.calibration.CalibrationWizard
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.MicCalibration
import apincer.android.mmate.audio.calibration.MicCalibrationStore
import apincer.android.mmate.audio.calibration.RigTarget
import apincer.android.mmate.ui.compose.CarCalibrationScreen
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.StopCalibrationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hosts the car calibration wizard. The screen stays on while it runs. */
class CarCalibrationActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wizard: CalibrationWizard
    /** The action waiting on the mic permission prompt; a field so recomposition keeps it. */
    private var pending: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val output = if (intent.getStringExtra("calibration_output") == "headphone") CalibrationOutput.HEADPHONE else CalibrationOutput.SPEAKER
        wizard = CalibrationWizard(this, scope, output)

        setContent {
            MusicMateTheme {
                val step by wizard.step.collectAsState()
                var confirmingExit by remember { mutableStateOf(false) }
                var asked by remember { mutableStateOf(false) }
                var fixtureConfirmed by remember { mutableStateOf(false) }
                var fixtureName by remember { mutableStateOf("") }
                var targetName by remember { mutableStateOf<String?>(null) }
                val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch {
                        try {
                            val target = withContext(Dispatchers.IO) {
                                val label = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                } ?: "Imported rig target"
                                val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { it.readNBytes(RigTarget.MAX_BYTES + 1) }
                                require(bytes.size <= RigTarget.MAX_BYTES) { "Target must be at most 64 KiB." }
                                RigTarget.parse(bytes.toString(Charsets.UTF_8), label.take(160))
                            }
                            wizard.setTarget(target)
                            targetName = target.source
                        } catch (error: Exception) {
                            Toast.makeText(this@CarCalibrationActivity, error.message ?: "Could not import target", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                val micCalibrations = remember { MicCalibrationStore(this@CarCalibrationActivity) }
                // Keyed by the device that will record: the first built-in mic until another is chosen, as capture does
                var micName by remember { mutableStateOf(wizard.microphones.firstOrNull { it.builtIn }?.name) }
                var micCalibrationName by remember { mutableStateOf(micName?.let(micCalibrations::get)?.also(wizard::setMicCalibration)?.source) }
                fun useMicCalibration(calibration: MicCalibration?) {
                    wizard.setMicCalibration(calibration)
                    micCalibrationName = calibration?.source
                }
                val micCalibrationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch {
                        try {
                            val calibration = withContext(Dispatchers.IO) {
                                val label = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                } ?: "Microphone calibration"
                                val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { it.readNBytes(MicCalibration.MAX_BYTES + 1) }
                                MicCalibration.parse(bytes.toString(Charsets.UTF_8), label)
                            }
                            micCalibrations.put(requireNotNull(micName) { "Choose a microphone first." }, calibration)
                            useMicCalibration(calibration)
                        } catch (error: Exception) {
                            Toast.makeText(this@CarCalibrationActivity, error.message ?: "Could not read the calibration file", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                fun leave() {
                    if (wizard.hasUnsavedWork) confirmingExit = true else finish()
                }
                BackHandler { leave() }
                // Nearby devices names the car in Android Auto (CarHandshake): asked once per visit, and
                // declining it never blocks calibration; only the mic is required
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                    val mic = granted[Manifest.permission.RECORD_AUDIO] ?: true
                    if (mic) pending?.invoke()
                    else Toast.makeText(this, "Calibration needs the microphone. Allow it in Settings, Apps, MusicMate, Permissions.", Toast.LENGTH_LONG).show()
                    pending = null
                }
                fun withMic(action: () -> Unit) {
                    val missing = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
                        .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty() || (Manifest.permission.RECORD_AUDIO !in missing && asked)) {
                        action()
                    } else {
                        asked = true
                        pending = action
                        permission.launch(missing.toTypedArray())
                    }
                }
                CarCalibrationScreen(
                    step = step,
                    unprocessedMic = wizard.unprocessedMic,
                    onStart = { withMic {
                        try {
                            if (output == CalibrationOutput.HEADPHONE) wizard.beginHeadphone(fixtureConfirmed, fixtureName) else wizard.begin()
                        } catch (error: IllegalArgumentException) {
                            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                        }
                    } },
                    onMeasure = { withMic { @Suppress("MissingPermission") wizard.measure() } },
                    onSave = { name -> wizard.save(name) },
                    onClose = { leave() },
                    output = output,
                    microphones = wizard.microphones,
                    onMicrophone = { id ->
                        wizard.selectMicrophone(id)
                        micName = wizard.microphones.firstOrNull { it.id == id }?.name
                        useMicCalibration(micName?.let(micCalibrations::get))
                    },
                    micCalibrationName = micCalibrationName,
                    micCalibrationAvailable = micName != null,
                    onImportMicCalibration = { micCalibrationPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                    onClearMicCalibration = {
                        micName?.let { micCalibrations.put(it, null) }
                        useMicCalibration(null)
                    },
                    targetName = targetName,
                    onImportTarget = { targetPicker.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel")) },
                    onFixtureConfirmed = { fixtureConfirmed = it },
                    onFixtureName = { fixtureName = it },
                    onCheck = { withMic { @Suppress("MissingPermission") wizard.check() } },
                    onSkipCheck = wizard::skipCheck,
                    onTarget = wizard::chooseTarget
                )
                if (confirmingExit) {
                    StopCalibrationDialog(onStop = { finish() }, onDismiss = { confirmingExit = false })
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
