package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CorrectionProfileChoice
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
    onCreateProfile: () -> Unit = onFullTuning,
    speakerControls: (@Composable () -> Unit)? = null,
    speakerStatus: (@Composable () -> Unit)? = null,
    onAdjust: (Double, Double) -> Unit = { _, _ -> },
    onAdjustFinished: (Double, Double) -> Unit = { _, _ -> },
    onNightListening: (Boolean) -> Unit = {},
    onCompare: (Boolean) -> Unit = {},
    onPreviewEnd: () -> Unit = {},
    remoteTarget: String? = null
) {
    val endpoint = status.endpoint
    val output = status.profile?.output ?: endpoint?.output ?: CalibrationOutput.HEADPHONE
    val canApply = endpoint != null && !busy
    val canEnable = canApply && (status.profile?.let { endpoint!!.supports(it.output) } ?: true)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TuningCurrentSound(endpoint?.name ?: "No audio output connected", remoteTarget) {
            if (speakerStatus != null) speakerStatus() else if (speakerControls == null) {
                TuningStatusControl(tuningAudibility(status, remoteTarget), status.binding.enabled, canEnable, onEnabled,
                    usb = endpoint?.usb == true)
            }
        }
        if (speakerControls != null) {
            speakerControls()
        } else {
            TuningSoundModes(status.binding.soundMode, canEnable, onSelect = onSoundMode)
            if (status.binding.soundMode == SoundMode.CUSTOM) {
                CustomSoundControls(endpoint?.key to status.profile?.id,
                    status.binding.bassDb, status.binding.trebleDb, status.binding.nightListening,
                    enabled = canEnable, canCompare = status.active && remoteTarget == null, onAdjust = onAdjust, onAdjustFinished = onAdjustFinished, onCompare = onCompare, onPreviewEnd = onPreviewEnd)
            }
            NightListeningControl(status.binding.nightListening, canEnable, onNightListening)
        }
        HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
        TuningDisclosure("Correction", selectedName ?: "None · sound mode works independently") {
            CorrectionProfileSelector(choices, status.profile?.id, selectedName, output, canApply, onSelect,
                supportsOutput = { endpoint?.supports(it) == true })
            TuningHint("Optional headphone or speaker correction. Create and manage profiles in Manage tuning.")
        }
        error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 13.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MusicMateDesignTokens.Brand.Gold)
    }
}
