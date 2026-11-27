package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import apincer.android.mmate.R
import apincer.android.mmate.audio.calibration.CalibrationOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioTuningSheet(onDismissRequest: () -> Unit, compact: Boolean = false,
    onManage: (() -> Unit)? = null, title: String? = null, closeLabel: String? = null, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The body owns vertical scrolling; dragging the sheet competes at scroll boundaries.
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MusicMateDesignTokens.Surfaces.Canvas,
        contentColor = MusicMateDesignTokens.Text.Primary,
        sheetMaxWidth = 640.dp
    ) {
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f) else Modifier.fillMaxHeight(0.94f))) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title ?: stringResource(if (compact) R.string.matetune_quick_title else R.string.nav_audio_tuning), Modifier.weight(1f),
                    fontSize = 22.sp, fontWeight = FontWeight.Medium)
                onManage?.let { manage ->
                    IconButton(onClick = manage) {
                        Icon(painterResource(R.drawable.ic_round_settings_24), stringResource(R.string.matetune_manage),
                            tint = MusicMateDesignTokens.Text.Secondary)
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(painterResource(R.drawable.round_close_24), closeLabel ?: stringResource(if (compact) R.string.matetune_close_quick else R.string.matetune_close),
                        tint = MusicMateDesignTokens.Text.Secondary)
                }
            }
            HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
            Column(Modifier.weight(1f, fill = !compact).verticalScroll(remember(compact) { ScrollState(0) }).padding(24.dp)) { content() }

        }
    }
}

@Composable
internal fun TuningCurrentSound(outputName: String, remoteTarget: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(outputName, color = MusicMateDesignTokens.Text.Secondary, fontSize = 13.sp)
        remoteTarget?.let { TuningHint("Playing on $it. Tuning applies only when Music Mate plays on this phone.") }
        content()
    }
}

@Composable
internal fun TuningDisclosure(title: String, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                summary?.let { TuningHint(it) }
            }
            Icon(painterResource(R.drawable.ic_expand_more), null,
                Modifier.size(24.dp).then(if (expanded) Modifier.graphicsLayer(rotationZ = 180f) else Modifier),
                tint = MusicMateDesignTokens.Text.Secondary)
        }
        if (expanded) content()
    }
}

@Composable
internal fun TuningLabel(text: String) {
    Text(text, color = MusicMateDesignTokens.Text.Secondary, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
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
internal fun TuningResponseCurve(bands: List<apincer.android.mmate.audio.dsp.PeqBand>, mode: apincer.android.mmate.audio.dsp.SoundMode, averageEars: Boolean = false, active: Boolean = true, previewState: String = "Bypassed", settings: apincer.android.mmate.audio.dsp.CarDspSettings? = null) {
    Surface(color = MusicMateDesignTokens.Surfaces.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TuningLabel("EQ CURVE")
                Text(if (active) "${mode.label} mode" else "Preview · ${mode.label} · $previewState", color = MusicMateDesignTokens.Text.Secondary, fontSize = 12.sp)
            }
            CorrectionCurve(bands, settings = settings)
            if (settings != null) {
                val preamp = androidx.compose.runtime.remember(settings) {
                    apincer.android.mmate.audio.dsp.DspChain.of(settings, 48_000, 2).preampDb
                }
                TuningHint("Headroom attenuation: %.1f dB · 48 kHz preview".format(-preamp))
                TuningHint("Correction + sound mode · left: gold, right: grey. Curve excludes headroom, balance and crossfeed.")
            } else TuningHint(if (averageEars) "Correction + sound mode · average of left and right" else "Correction + sound mode · 20 Hz–20 kHz")
        }
    }
}
