package apincer.android.mmate.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.TextButton
import apincer.android.mmate.audio.calibration.CarProfile
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import apincer.android.mmate.audio.dsp.DspChain
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.dsp.CarDspChoice
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.audio.dsp.SoundMode

/** Compatibility entry point for the shared playback tuning chip. */
@Composable
fun CarDspChip(modifier: Modifier = Modifier) = AudioTuningChip(modifier)

@Composable
fun CarDspDialog(choice: CarDspChoice, onChange: (CarDspChoice) -> Unit, onDismissRequest: () -> Unit) {
    AudioTuningDialog(onDismissRequest)
}

/** Sound-mode-only speaker playback remains available before a correction is selected. */
@Composable
internal fun LegacySpeakerSoundModes(compact: Boolean = false, available: Boolean = true, busy: Boolean = false,
    showStatus: Boolean = true) {
    val context = LocalContext.current
    val choice by CarDspPreferences.state.collectAsState()
    val audibility = legacySpeakerAudibility(available, choice.enabled)
    if (showStatus) LegacySpeakerStatus(available, busy)
    TuningSoundModes(choice.mode, available && !busy) { mode ->
        CarDspPreferences.update(context, choice.copy(mode = mode))
    }
    val comparing by CarDspPreferences.comparingCustom.collectAsState()
    if (choice.mode == SoundMode.CUSTOM) {
        CustomSoundControls(choice.profileId, choice.bassDb, choice.trebleDb, choice.nightListening,
            enabled = available && !busy, canCompare = available && choice.enabled, rangeDb = SoundMode.TRIM_DB.toFloat(),
            onAdjust = { bass, treble -> CarDspPreferences.previewCustomTrims(bass, treble) },
            onAdjustFinished = { bass, treble -> CarDspPreferences.update(context,
                CarDspPreferences.state.value.copy(bassDb = bass, trebleDb = treble)) },
            onCompare = { CarDspPreferences.setCustomComparison(it) },
            onPreviewEnd = { CarDspPreferences.clearCustomPreview() })
    }
    NightListeningControl(choice.nightListening, available && !busy) {
        CarDspPreferences.update(context, CarDspPreferences.state.value.copy(nightListening = it))
    }
    if (!compact) TuningDisclosure("Response curve") {
        TuningResponseCurve(choice.settings(comparingCustom = comparing)?.bands
        ?: (choice.correction + if (comparing && choice.mode == SoundMode.CUSTOM) emptyList() else choice.mode.bands(choice.bassDb, choice.trebleDb)), choice.mode,
        active = available && choice.enabled, previewState = audibility.label)
    }
}

@Composable
internal fun LegacySpeakerStatus(available: Boolean = true, busy: Boolean = false, remoteTarget: String? = null) {
    val context = LocalContext.current
    val choice by CarDspPreferences.state.collectAsState()
    TuningStatusControl(legacySpeakerAudibility(available, choice.enabled, remoteTarget), choice.enabled, available && !busy,
        { CarDspPreferences.update(context, choice.copy(enabled = it)) })
}

private fun legacySpeakerAudibility(available: Boolean, enabled: Boolean, remoteTarget: String? = null): TuningAudibility = when {
    remoteTarget != null -> NOT_APPLIED_TO_PLAYER
    !available -> TuningAudibility("Unavailable", "Connect a compatible speaker output")
    enabled -> TuningAudibility("EQ on", "Speaker sound mode is applied")
    else -> TuningAudibility("EQ off", "Your profile and sound mode are saved")
}

/** What the chosen mode and car correction do together, 20 Hz to 20 kHz; the scale is ±6 dB, wider when the curve needs it. */
@Composable
internal fun CorrectionCurve(bands: List<apincer.android.mmate.audio.dsp.PeqBand>, modifier: Modifier = Modifier) {
    val points = remember(bands) {
        val chain = DspChain(bands, 48_000, 2)
        DoubleArray(CURVE_POINTS) { chain.responseDb(20.0 * Math.pow(1_000.0, it / (CURVE_POINTS - 1.0))) }
    }
    val gold = MusicMateDesignTokens.Brand.Gold
    val zero = MusicMateDesignTokens.Surfaces.BorderPrimary
    val gridLine = MusicMateDesignTokens.Surfaces.BorderSubtle
    val measurer = rememberTextMeasurer()
    val axis = TextStyle(color = MusicMateDesignTokens.Text.Muted, fontSize = 10.sp)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MusicMateDesignTokens.Surfaces.Canvas)
            .semantics { contentDescription = "Combined correction and sound mode EQ curve" }
    ) {
        val span = maxOf(6.0, kotlin.math.ceil(points.maxOf { kotlin.math.abs(it) } / 3.0) * 3.0)
        val dbLabel = measurer.measure("%+.0f dB".format(-span), axis)
        val plotLeft = dbLabel.size.width + 12.dp.toPx()
        val plotRight = size.width - 12.dp.toPx()
        val plotTop = dbLabel.size.height / 2f + 6.dp.toPx()
        val plotBottom = size.height - dbLabel.size.height - 12.dp.toPx()
        val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotH = plotBottom - plotTop
        fun y(db: Double) = (plotTop + plotH / 2 - db.coerceIn(-span, span) / span * plotH / 2).toFloat()
        val frequencies = listOf(20.0 to "20Hz", 100.0 to "100", 1_000.0 to "1k", 10_000.0 to "10k", 20_000.0 to "20kHz")
        val endLabel = measurer.measure("20kHz", axis)
        val endLabelLeft = plotRight - endLabel.size.width
        var previousLabelRight = Float.NEGATIVE_INFINITY
        for ((f, text) in frequencies) {
            val x = plotLeft + (Math.log(f / 20.0) / Math.log(1_000.0) * plotW).toFloat()
            drawLine(gridLine, Offset(x, plotTop), Offset(x, plotBottom))
            val label = measurer.measure(text, axis)
            val labelLeft = (x - label.size.width / 2f).coerceIn(plotLeft, maxOf(plotLeft, plotRight - label.size.width))
            val gap = 6.dp.toPx()
            if (labelLeft >= previousLabelRight + gap &&
                (f == 20_000.0 || labelLeft + label.size.width + gap <= endLabelLeft)) {
                drawText(label, topLeft = Offset(labelLeft, plotBottom + 6.dp.toPx()))
                previousLabelRight = labelLeft + label.size.width
            }
        }
        for (db in listOf(span, 0.0, -span)) {
            drawLine(if (db == 0.0) zero else gridLine, Offset(plotLeft, y(db)), Offset(plotRight, y(db)))
            val label = measurer.measure(if (db == 0.0) "0 dB" else "%+.0f dB".format(db), axis)
            drawText(label, topLeft = Offset(plotLeft - label.size.width - 6.dp.toPx(), y(db) - label.size.height / 2f))
        }
        val path = Path()
        points.forEachIndexed { i, db ->
            val x = plotLeft + i * plotW / (CURVE_POINTS - 1)
            if (i == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
        }
        drawPath(path, gold, style = Stroke(width = 2.dp.toPx()))
    }
}

private const val CURVE_POINTS = 120

@Composable
private fun ChipText(text: String, modifier: Modifier = Modifier, overflow: TextOverflow = TextOverflow.Clip, dimmed: Boolean = false) {
    Text(
        text = text,
        color = if (dimmed) MusicMateDesignTokens.Text.Secondary else MusicMateDesignTokens.Text.Primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        overflow = overflow,
        modifier = modifier
    )
}

/** The Settings card body: saved car profiles, which one corrects the car, and the calibrate button. */
@Composable
fun CarAudioSettings() = HeadphoneAudioSettings(apincer.android.mmate.audio.calibration.CalibrationOutput.SPEAKER)

@Composable
internal fun LegacySpeakerProfileManagement(
    profiles: List<CarProfile>,
    busy: Boolean,
    onRename: (CarProfile, String) -> Unit,
    onDelete: (CarProfile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CarProfile?>(null) }
    var deleting by remember { mutableStateOf<CarProfile?>(null) }
    if (profiles.isNotEmpty()) {
        TextButton(onClick = { expanded = !expanded }) { Text("Manage measured speakers") }
        if (expanded) profiles.forEach { profile ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, color = MusicMateDesignTokens.Text.Primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { renaming = profile }, enabled = !busy) { Text("Rename") }
                TextButton(onClick = { deleting = profile }, enabled = !busy) { Text("Delete") }
            }
        }
    }
    renaming?.let { profile ->
        RenameSpeakerDialog(profile, onRename = { name -> onRename(profile, name); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { profile ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${profile.name}?") },
            text = { Text("Its correction is removed from saved outputs. You can tune it again later.") },
            confirmButton = { TextButton(onClick = { onDelete(profile); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

/** The longest friendly name a speaker can have; the chip shortens it further. */
private const val MAX_NAME = 40

/** A friendly name for a saved speaker, with its Bluetooth name underneath so the device stays recognisable. */
@Composable
private fun RenameSpeakerDialog(profile: CarProfile, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    // Opens focused with the old name selected, so typing replaces it
    var field by remember(profile.id) { mutableStateOf(TextFieldValue(profile.name, TextRange(0, profile.name.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val trimmed = field.text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != profile.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename speaker") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= MAX_NAME) field = it },
                label = { Text("Speaker name") },
                supportingText = profile.bluetoothName?.let { { Text("Bluetooth: $it") } },
                singleLine = true,
                colors = speakerNameFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(trimmed) }, enabled = canSave) {
                Text("Save", color = if (canSave) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MusicMateDesignTokens.Text.Secondary) }
        },
        containerColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
        titleContentColor = MusicMateDesignTokens.Text.Primary,
        textContentColor = MusicMateDesignTokens.Text.Telemetry
    )
}

/** Shared by the rename dialog and the wizard's name field. */
@Composable
internal fun speakerNameFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MusicMateDesignTokens.Text.Primary,
    unfocusedTextColor = MusicMateDesignTokens.Text.Primary,
    focusedBorderColor = MusicMateDesignTokens.Brand.Gold,
    unfocusedBorderColor = MusicMateDesignTokens.Surfaces.BorderPrimary,
    focusedLabelColor = MusicMateDesignTokens.Brand.Gold,
    unfocusedLabelColor = MusicMateDesignTokens.Text.Muted,
    cursorColor = MusicMateDesignTokens.Brand.Gold
)

/** "AL5125SH_BT · " when the friendly name hides which Bluetooth device this is. */
private fun deviceNote(profile: CarProfile): String =
    profile.bluetoothName?.takeIf { it != profile.name }?.let { "$it · " } ?: ""
