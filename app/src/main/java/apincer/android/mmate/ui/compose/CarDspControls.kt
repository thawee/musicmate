package apincer.android.mmate.ui.compose

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.ui.CarCalibrationActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import apincer.android.mmate.R
import apincer.android.mmate.audio.dsp.DspChain
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import apincer.android.mmate.audio.dsp.CarDspChoice
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.audio.dsp.CarSoundMode
import kotlin.math.roundToInt

/** "MU-X · DRIVE" (the active speaker, or TUNING) when Speaker Tuning is on, "TUNING · OFF" otherwise; tap to choose. */
@Composable
fun CarDspChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val choice by CarDspPreferences.state.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val accent = if (choice.enabled) MusicMateDesignTokens.Brand.GoldAmber else MusicMateDesignTokens.Text.Secondary
    val car = choice.profileName?.uppercase() ?: "TUNING"
    val label = if (choice.enabled) " · ${choice.mode.label.uppercase()}" else "TUNING · OFF"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MusicMateDesignTokens.Surfaces.GlassPill)
            .border(0.75.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { picking = true }
            .semantics {
                role = Role.Button
                contentDescription = "Speaker Tuning"
                stateDescription = if (choice.enabled) "On, ${choice.profileName ?: "no speaker correction"}, ${choice.mode.label}" else "Off"
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (choice.enabled) {
            // A long car name gives way to the mode
            ChipText(car, Modifier.weight(1f, fill = false).widthIn(max = 104.dp), overflow = TextOverflow.Ellipsis)
        }
        ChipText(label)
    }

    if (picking) {
        CarDspDialog(
            choice = choice,
            onChange = { CarDspPreferences.update(context, it) },
            onDismissRequest = { picking = false }
        )
    }
}

@Composable
fun CarDspDialog(choice: CarDspChoice, onChange: (CarDspChoice) -> Unit, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val cars by produceState(emptyList<CarProfile>()) {
        value = withContext(Dispatchers.IO) { CarDspPreferences.profiles(context).list() }
    }
    val calibrate = {
        onDismissRequest()
        context.startActivity(Intent(context, CarCalibrationActivity::class.java))
    }
    // Picking a car, like picking a mode, turns Car DSP on
    fun pickCar(car: CarProfile?) {
        CarDspPreferences.setActiveProfile(context, car)
        if (!choice.enabled) CarDspPreferences.update(context, CarDspPreferences.state.value.copy(enabled = true))
    }
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MusicMateDesignTokens.Surfaces.Surface)
                .border(1.dp, MusicMateDesignTokens.Surfaces.BorderPrimary, RoundedCornerShape(20.dp))
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Speaker Tuning", color = MusicMateDesignTokens.Text.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (choice.enabled) "On for local playback. Never applied to a USB DAC." else "Off. Playback is untouched.",
                        color = MusicMateDesignTokens.Text.Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = choice.enabled,
                    onCheckedChange = { onChange(choice.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MusicMateDesignTokens.Surfaces.Canvas,
                        checkedTrackColor = MusicMateDesignTokens.Brand.Gold,
                        uncheckedThumbColor = MusicMateDesignTokens.Text.Secondary,
                        uncheckedTrackColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
                        uncheckedBorderColor = MusicMateDesignTokens.Text.Muted
                    ),
                    modifier = Modifier.semantics { contentDescription = "Speaker Tuning" }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                if (choice.enabled) {
                    Spacer(Modifier.height(16.dp))
                    EqCurve(choice, modifier = Modifier.padding(horizontal = 20.dp))
                }
                if (cars.isNotEmpty()) {
                    SectionLabel("SPEAKER")
                    CarDspOption("No correction", "Sound modes only", selected = choice.profileId == null, dimmed = !choice.enabled) {
                        pickCar(null)
                    }
                    cars.forEach { car ->
                        CarDspOption(car.name, calibratedOn(car), selected = car.id == choice.profileId, dimmed = !choice.enabled) {
                            pickCar(car)
                        }
                    }
                }
                SectionLabel("SOUND MODE")
                CarSoundMode.entries.forEach { mode ->
                    CarDspOption(mode.label, mode.summary, selected = choice.mode == mode, dimmed = !choice.enabled) {
                        onChange(choice.copy(enabled = true, mode = mode))
                    }
                }
                if (choice.enabled && choice.mode == CarSoundMode.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TrimSlider("Bass", choice.bassDb) { onChange(choice.copy(bassDb = it)) }
                    TrimSlider("Treble", choice.trebleDb) { onChange(choice.copy(trebleDb = it)) }
                }
                if (cars.isEmpty()) {
                    SectionLabel("SPEAKER CORRECTION")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("None", color = MusicMateDesignTokens.Text.Telemetry, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Measure a car or speaker to correct its sound", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = calibrate) {
                            Text("Tune", color = MusicMateDesignTokens.Brand.Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (cars.isNotEmpty()) {
                    TextButton(onClick = calibrate) {
                        Text("Tune a speaker", color = MusicMateDesignTokens.Text.Secondary)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Done", color = MusicMateDesignTokens.Brand.Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MusicMateDesignTokens.Text.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
            .semantics { heading() }
    )
}

/** What the chosen mode and car correction do together, 20 Hz to 20 kHz; the scale is ±6 dB, wider when the curve needs it. */
@Composable
private fun EqCurve(choice: CarDspChoice, modifier: Modifier = Modifier) {
    val bands = choice.settings()?.bands.orEmpty()
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
            .height(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MusicMateDesignTokens.Surfaces.Canvas)
            .semantics { contentDescription = "EQ curve of ${choice.mode.label}" + (choice.profileName?.let { " with $it correction" } ?: "") }
    ) {
        val plotH = size.height - 14.dp.toPx()
        val span = maxOf(6.0, kotlin.math.ceil(points.maxOf { kotlin.math.abs(it) } / 3.0) * 3.0)
        fun y(db: Double) = (plotH / 2 - db.coerceIn(-span, span) / span * plotH / 2).toFloat()
        for ((f, text) in listOf(100.0 to "100", 1_000.0 to "1k", 10_000.0 to "10k")) {
            val x = (Math.log(f / 20.0) / Math.log(1_000.0) * size.width).toFloat()
            drawLine(gridLine, Offset(x, 0f), Offset(x, plotH))
            drawText(measurer, text, Offset(x + 3.dp.toPx(), plotH + 1.dp.toPx()), axis)
        }
        drawLine(zero, Offset(0f, y(0.0)), Offset(size.width, y(0.0)))
        drawText(measurer, "%+.0f dB".format(span), Offset(4.dp.toPx(), 2.dp.toPx()), axis)
        drawText(measurer, "0", Offset(4.dp.toPx(), y(0.0) + 1.dp.toPx()), axis)
        val path = Path()
        points.forEachIndexed { i, db ->
            val x = i * size.width / (CURVE_POINTS - 1)
            if (i == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
        }
        drawPath(path, gold, style = Stroke(width = 2.dp.toPx()))
    }
}

private const val CURVE_POINTS = 120

@Composable
private fun CarDspOption(title: String, summary: String, selected: Boolean, dimmed: Boolean = false, onSelect: () -> Unit) {
    val highlight = selected && !dimmed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) MusicMateDesignTokens.Brand.GoldContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { if (dimmed && selected) stateDescription = "Saved, Speaker Tuning off" }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = if (dimmed) MusicMateDesignTokens.Text.Muted else MusicMateDesignTokens.Brand.Gold,
                unselectedColor = MusicMateDesignTokens.Text.Muted
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = when {
                    highlight -> MusicMateDesignTokens.Brand.Gold
                    dimmed -> MusicMateDesignTokens.Text.Secondary
                    else -> MusicMateDesignTokens.Text.Telemetry
                },
                fontSize = 15.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
            Text(text = summary, color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Bass or treble trim in 0.5 dB steps across +/-3 dB. */
@Composable
private fun TrimSlider(label: String, valueDb: Double, onChange: (Double) -> Unit) {
    val range = CarSoundMode.TRIM_DB.toFloat()
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "$label %+.1f dB".format(valueDb),
            color = MusicMateDesignTokens.Text.Telemetry,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Slider(
            value = valueDb.toFloat(),
            onValueChange = { onChange((it * 2).roundToInt() / 2.0) },
            valueRange = -range..range,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MusicMateDesignTokens.Brand.Gold,
                activeTrackColor = MusicMateDesignTokens.Brand.Gold,
                inactiveTrackColor = MusicMateDesignTokens.Controls.TrackInactive,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.semantics { contentDescription = "$label trim" }
        )
    }
}

@Composable
private fun ChipText(text: String, modifier: Modifier = Modifier, overflow: TextOverflow = TextOverflow.Clip) {
    Text(
        text = text,
        color = MusicMateDesignTokens.Text.Primary,
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
fun CarAudioSettings() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<CarProfile?>(null) }
    var renaming by remember { mutableStateOf<CarProfile?>(null) }
    val profiles by produceState(emptyList<CarProfile>(), refresh) {
        value = withContext(Dispatchers.IO) { CarDspPreferences.profiles(context).list() }
    }
    // Follows the live choice, so a car switched from the dialog or by Bluetooth shows here too
    remember { CarDspPreferences.init(context) }
    val activeId = CarDspPreferences.state.collectAsState().value.profileId
    val calibrate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val hasMic = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }

    Text(
        text = "Measure your car or Bluetooth speaker once with the phone's microphone; Speaker Tuning then corrects it under the sound mode you pick, and picks it again when it connects.",
        color = MusicMateDesignTokens.Text.Muted,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    Spacer(Modifier.height(10.dp))
    if (profiles.isNotEmpty()) {
        CarDspOption("No correction", "Sound modes only", activeId == null) {
            CarDspPreferences.setActiveProfile(context, null)
            refresh++
        }
    }
    profiles.forEach { profile ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CarDspOption(profile.name, profileSummary(profile), profile.id == activeId) {
                    CarDspPreferences.setActiveProfile(context, profile)
                    refresh++
                }
            }
            IconButton(onClick = { renaming = profile }) {
                Icon(
                    painterResource(R.drawable.ic_round_edit_24),
                    contentDescription = "Rename ${profile.name}",
                    tint = MusicMateDesignTokens.Text.Secondary
                )
            }
            IconButton(onClick = { deleting = profile }) {
                Icon(
                    painterResource(R.drawable.rounded_delete_24),
                    contentDescription = "Delete ${profile.name}",
                    tint = MusicMateDesignTokens.Status.Destructive
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (!hasMic) {
        Text("This device has no microphone, so it cannot calibrate.", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
    } else OutlinedButton(
        onClick = { calibrate.launch(Intent(context, CarCalibrationActivity::class.java)) },
        border = BorderStroke(1.dp, MusicMateDesignTokens.Brand.GoldOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (profiles.isEmpty()) "Tune a speaker" else "Tune another speaker", color = MusicMateDesignTokens.Brand.Gold, fontWeight = FontWeight.Bold)
    }

    renaming?.let { profile ->
        RenameSpeakerDialog(
            profile = profile,
            onRename = { name ->
                CarDspPreferences.renameProfile(context, profile.id, name)
                renaming = null
                refresh++
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Its correction is removed. You can tune it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    CarDspPreferences.deleteProfile(context, profile.id)
                    deleting = null
                    refresh++
                }) { Text("Delete", color = MusicMateDesignTokens.Status.Destructive, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel", color = MusicMateDesignTokens.Text.Secondary) }
            },
            containerColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
            titleContentColor = MusicMateDesignTokens.Text.Primary,
            textContentColor = MusicMateDesignTokens.Text.Telemetry
        )
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

private fun calibratedOn(profile: CarProfile): String =
    deviceNote(profile) + "Calibrated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.createdAtMillis))

private fun profileSummary(profile: CarProfile): String {
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.createdAtMillis))
    val check = when (profile.verified) {
        true -> "checked"
        false -> "check off by %.1f dB".format(profile.worstDeviationDb ?: 0.0)
        null -> "not checked"
    }
    return deviceNote(profile) + "$date · ${profile.bands.size} bands · $check" + if (profile.approximate) " · approximate" else ""
}
