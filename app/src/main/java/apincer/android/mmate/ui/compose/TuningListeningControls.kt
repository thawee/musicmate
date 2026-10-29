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
import androidx.compose.ui.platform.LocalDensity
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

internal fun tuningAudibility(status: HeadphoneStatus): TuningAudibility = when {
    status.endpoint == null -> TuningAudibility("Unavailable", "Connect a compatible output")
    status.profile == null -> TuningAudibility("Unavailable", "Select a profile to enable correction")
    !status.endpoint.supports(status.profile.output) -> TuningAudibility("Unavailable", "Select a profile compatible with this output")
    status.active -> TuningAudibility("Active", "Correction and sound mode are applied")
    status.endpoint.usb && !status.binding.usbEqAllowed -> TuningAudibility("Bypassed", "Enable USB EQ to allow sample processing")
    !status.binding.enabled -> TuningAudibility("Bypassed", "Your profile and sound mode are saved")
    else -> TuningAudibility("Unavailable", "Processing is not active on this output")
}

@Composable
internal fun TuningStatusControl(
    audibility: TuningAudibility,
    checked: Boolean,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    usb: Boolean = false,
    switchLabel: String = if (usb) "Enable USB EQ" else "Enable output EQ"
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onEnabled)
            .semantics {
                contentDescription = switchLabel
                stateDescription = "${audibility.label}. ${audibility.reason}"
            }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(audibility.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (audibility.label == "Active") MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Primary)
                TuningHint(audibility.reason)
            }
            Switch(checked, onCheckedChange = null, enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MusicMateDesignTokens.Surfaces.Canvas,
                    checkedTrackColor = MusicMateDesignTokens.Brand.Gold))
        }
        if (usb) TuningHint("USB EQ changes samples. Your explicit choice is saved for this DAC.")
    }
}

@Composable
internal fun TuningSoundModes(mode: SoundMode, enabled: Boolean = true, onSelect: (SoundMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TuningLabel("SOUND MODE")
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 300.dp && fontScale <= 1.3f) 2 else 1
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundMode.entries.chunked(columns).forEach { options ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            SoundModeCard(option, mode == option, enabled, Modifier.weight(1f).fillMaxHeight()) { onSelect(option) }
                        }
                        if (options.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        TuningHint(when (mode) {
            SoundMode.REFERENCE -> "Adds no extra tone shaping; your calibration still applies when EQ is enabled."
            SoundMode.BASS -> "Lifts deep lows while easing the low mids to keep bass defined."
            SoundMode.VOCAL -> "Lifts vocal presence and reduces low-end masking."
            SoundMode.LIVELY -> "Adds low-end weight, attack and high-frequency sparkle."
            SoundMode.RELAX -> "Eases upper-mid presence and treble while adding gentle warmth."
            SoundMode.NIGHT -> "Reduces the gap between quiet and loud passages, with gentler bass and highs."
            SoundMode.CUSTOM -> "Adjust bass and treble independently on top of your correction."
        })
    }
}

internal fun soundModeDescription(mode: SoundMode): String = when (mode) {
    SoundMode.REFERENCE -> "Your calibration unchanged"
    SoundMode.BASS -> "Deeper, punchier bass"
    SoundMode.VOCAL -> "Clearer, forward voices"
    SoundMode.LIVELY -> "Punchy bass, crisp detail"
    SoundMode.RELAX -> "Warmer, softer highs"
    SoundMode.NIGHT -> "Tames loud moments"
    SoundMode.CUSTOM -> "Your bass and treble"
}

@Composable
private fun SoundModeCard(mode: SoundMode, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape)
        .background(if (selected) MusicMateDesignTokens.Brand.GoldContainer else MusicMateDesignTokens.Surfaces.SurfaceElevated)
        .border(1.dp, if (selected) MusicMateDesignTokens.Brand.GoldOutline else MusicMateDesignTokens.Surfaces.BorderSubtle, shape)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 48.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mode.label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (enabled) MusicMateDesignTokens.Text.Primary else MusicMateDesignTokens.Text.Muted)
            if (selected) Icon(painterResource(R.drawable.ic_check_24), contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MusicMateDesignTokens.Brand.Gold)
        }
        Text(soundModeDescription(mode), Modifier.fillMaxWidth(), fontSize = 12.sp, lineHeight = 17.sp,
            color = if (enabled) MusicMateDesignTokens.Text.Secondary else MusicMateDesignTokens.Text.Muted)
    }
}
