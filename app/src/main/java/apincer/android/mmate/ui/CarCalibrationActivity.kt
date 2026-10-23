package apincer.android.mmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import apincer.android.mmate.ui.compose.CarCalibrationScreen
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.StopCalibrationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
        wizard = CalibrationWizard(this, scope)

        setContent {
            MusicMateTheme {
                val step by wizard.step.collectAsState()
                var confirmingExit by remember { mutableStateOf(false) }
                fun leave() {
                    if (wizard.hasUnsavedWork) confirmingExit = true else finish()
                }
                BackHandler { leave() }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) pending?.invoke()
                    else Toast.makeText(this, "Calibration needs the microphone. Allow it in Settings, Apps, MusicMate, Permissions.", Toast.LENGTH_LONG).show()
                    pending = null
                }
                fun withMic(action: () -> Unit) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        action()
                    } else {
                        pending = action
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                CarCalibrationScreen(
                    step = step,
                    unprocessedMic = wizard.unprocessedMic,
                    onStart = { withMic { wizard.begin() } },
                    onMeasure = { withMic { @Suppress("MissingPermission") wizard.measure() } },
                    onSave = { name -> withMic { @Suppress("MissingPermission") wizard.saveAndVerify(name) } },
                    onClose = { leave() }
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
