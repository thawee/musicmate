package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.R

internal data class TuningAudibility(val label: String, val reason: String)

internal val NOT_APPLIED_TO_PLAYER = TuningAudibility("Unavailable", "Not applied to the current player")

internal fun tuningAudibility(status: HeadphoneStatus, remoteTarget: String? = null): TuningAudibility = when {
    remoteTarget != null -> NOT_APPLIED_TO_PLAYER
    status.endpoint == null -> TuningAudibility("Unavailable", "Connect a compatible output")
    status.profile != null && !status.endpoint.supports(status.profile.output) -> TuningAudibility("Unavailable", "Select a profile compatible with this output")
    status.active -> TuningAudibility("EQ on", if (status.profile == null) "Sound mode is applied without correction" else "Correction and sound mode are applied")
    status.endpoint.usb && !status.binding.usbEqAllowed -> TuningAudibility("EQ off", "Enable USB EQ to allow sample processing")
    !status.binding.enabled -> TuningAudibility("EQ off", "Your profile and sound mode are saved")
    else -> TuningAudibility("Unavailable", "Processing is not active on this output")
}

@Composable
internal fun TuningStatusControl(
    audibility: TuningAudibility,
    checked: Boolean,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    usb: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onEnabled)
            .semantics {
                stateDescription = "${audibility.label}. ${audibility.reason}"
            }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (usb) "USB sound tuning" else "Sound tuning", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (audibility.label == "EQ on") MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Primary)
                TuningHint(if (audibility.label == "EQ on") "On" else if (audibility.label == "EQ off" && !usb) "Bypassed · your settings are saved" else audibility.reason)
            }
            Switch(checked, onCheckedChange = null, enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MusicMateDesignTokens.Surfaces.Canvas,
                    checkedTrackColor = MusicMateDesignTokens.Brand.Gold))
        }
        if (usb) TuningHint("USB EQ changes samples. Your explicit choice is saved for this DAC.")
    }
}

@Composable
internal fun TuningSoundModes(mode: SoundMode, enabled: Boolean = true, showHeading: Boolean = true, onSelect: (SoundMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeading) TuningLabel("SOUND MODE")
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundMode.selectable.forEach { option ->
                val selected = mode == option
                Row(Modifier.clip(RoundedCornerShape(24.dp))
                    .background(if (selected) MusicMateDesignTokens.Brand.GoldContainer else MusicMateDesignTokens.Surfaces.SurfaceElevated)
                    .border(1.dp, if (selected) MusicMateDesignTokens.Brand.GoldOutline else MusicMateDesignTokens.Surfaces.BorderSubtle, RoundedCornerShape(24.dp))
                    .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selected) Icon(painterResource(R.drawable.ic_check_24), null,
                        Modifier.size(16.dp), tint = MusicMateDesignTokens.Brand.Gold)
                    Text(option.label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (enabled) MusicMateDesignTokens.Text.Primary else MusicMateDesignTokens.Text.Muted)
                }
            }
        }
        TuningHint(soundModeDescription(mode))
    }
}

internal fun soundModeDescription(mode: SoundMode): String = when (mode) {
    SoundMode.REFERENCE -> "Balanced sound, no added tone shaping"
    SoundMode.BASS -> "Deeper, punchier bass"
    SoundMode.VOCAL -> "Clearer, forward voices"
    SoundMode.LIVELY -> "Punchy bass, crisp detail"
    SoundMode.RELAX -> "Warmer, softer highs"
    SoundMode.NIGHT -> "Tames loud moments"
    SoundMode.CUSTOM -> "Your bass and treble"
}

@Composable
internal fun NightListeningControl(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = "Night listening" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Night listening", color = MusicMateDesignTokens.Text.Primary, fontSize = 14.sp)
            TuningHint("Reduces loud passages for quieter listening")
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
