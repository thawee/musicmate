package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CorrectionProfileChoice
import apincer.android.mmate.audio.dsp.AutoSoundPreferences
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.HeadphoneStatus

@Composable
internal fun QuickTuningControls(
    status: HeadphoneStatus,
    choices: List<CorrectionProfileChoice>,
    onSelect: (String?) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSoundMode: (SoundMode) -> Unit,
    onFullTuning: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
    selectedName: String? = status.profile?.name,
    speakerControls: (@Composable () -> Unit)? = null,
    speakerStatus: (@Composable () -> Unit)? = null
) {
    val endpoint = status.endpoint
    val output = status.profile?.output ?: endpoint?.output ?: CalibrationOutput.HEADPHONE
    val canApply = endpoint != null && !busy
    val canEnable = canApply && status.profile?.let { endpoint!!.supports(it.output) } == true
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TuningCurrentSound(endpoint?.name ?: "No local output connected") {
            CorrectionProfileSelector(choices, status.profile?.id, selectedName, output, canApply, onSelect,
                supportsOutput = { endpoint?.supports(it) == true })
            if (speakerStatus != null) speakerStatus() else if (speakerControls == null) {
                TuningStatusControl(tuningAudibility(status), status.binding.enabled, canEnable, onEnabled,
                    usb = endpoint?.usb == true)
            }
        }
        if (speakerControls != null) {
            speakerControls()
        } else {
            AutoTuningSoundModes(status.binding.soundMode, canEnable, onSoundMode)
            if (!AutoSoundPreferences.state.collectAsState().value.config.enabled && status.binding.soundMode == SoundMode.CUSTOM) {
                TextButton(onClick = onFullTuning) { Text("Edit Custom", color = MusicMateDesignTokens.Brand.Gold) }
            }
            if (status.profile == null) TuningHint("Choose a saved profile, or open Manage tuning to create one.")
        }
        error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 13.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MusicMateDesignTokens.Brand.Gold)
    }
}
