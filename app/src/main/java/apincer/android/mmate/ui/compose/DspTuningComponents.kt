package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.audio.calibration.CalibrationOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioTuningSheet(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MusicMateDesignTokens.Surfaces.Canvas,
        contentColor = MusicMateDesignTokens.Text.Primary,
        sheetMaxWidth = 640.dp
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TuningLabel("MUSIC MATE / DSP")
                    Text("Audio tuning", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(painterResource(R.drawable.round_close_24), "Close audio tuning", tint = MusicMateDesignTokens.Text.Secondary)
                }
            }
            HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) { content() }
        }
    }
}

@Composable
internal fun TuningLabel(text: String) {
    Text(text, color = MusicMateDesignTokens.Text.Secondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun TuningHint(text: String) {
    Text(text, color = MusicMateDesignTokens.Text.Secondary, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
internal fun TuningOutputSelector(output: CalibrationOutput, onOutput: (CalibrationOutput) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MusicMateDesignTokens.Surfaces.Surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CalibrationOutput.entries.forEach { kind ->
            val selected = output == kind
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .background(if (selected) MusicMateDesignTokens.Surfaces.SurfaceElevated else Color.Transparent)
                .selectable(selected, role = Role.Tab, onClick = { onOutput(kind) })
                .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (kind == CalibrationOutput.SPEAKER) "Speaker" else "Headphone", color = if (selected) Color.White else MusicMateDesignTokens.Text.Secondary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun TuningAction(title: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = if (enabled) Color.White else MusicMateDesignTokens.Text.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                TuningHint(description)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MusicMateDesignTokens.Text.Secondary, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
}

@Composable
internal fun TuningResponseCurve(bands: List<apincer.android.mmate.audio.dsp.PeqBand>, mode: apincer.android.mmate.audio.dsp.SoundMode, averageEars: Boolean = false) {
    Surface(color = MusicMateDesignTokens.Surfaces.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TuningLabel("FREQUENCY RESPONSE")
                Text("${mode.label} mode", color = MusicMateDesignTokens.Text.Secondary, fontSize = 12.sp)
            }
            CorrectionCurve(bands)
            TuningHint(if (averageEars) "Correction + sound mode · average of left and right" else "Correction + sound mode · 20 Hz–20 kHz")
        }
    }
}
