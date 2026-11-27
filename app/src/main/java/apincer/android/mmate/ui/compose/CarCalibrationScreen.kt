package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CalibrationWizard
import apincer.android.mmate.audio.calibration.FitResult
import apincer.android.mmate.audio.calibration.FrequencyGrid
import apincer.android.mmate.audio.calibration.PeqFitter
import apincer.android.mmate.audio.calibration.SpeakerTarget
import apincer.android.mmate.audio.calibration.SweepRecorder
import kotlin.math.ln

private val Measured = MusicMateDesignTokens.Text.Secondary
private val Target = MusicMateDesignTokens.Brand.Gold
private val Predicted = MusicMateDesignTokens.Brand.AcousticTeal

private val STAGES = listOf("Prepare", "Measure", "Review", "Check")

/** The calibration wizard: a top bar, the stage strip, the step's content, and its action pinned below. */
@Composable
fun CarCalibrationScreen(
    step: CalibrationWizard.Step,
    unprocessedMic: Boolean,
    onStart: () -> Unit,
    onMeasure: () -> Unit,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    output: CalibrationOutput = CalibrationOutput.SPEAKER,
    microphones: List<SweepRecorder.Microphone> = emptyList(),
    onMicrophone: (Int) -> Unit = {},
    targetName: String? = null,
    onImportTarget: () -> Unit = {},
    onFixtureConfirmed: (Boolean) -> Unit = {},
    onFixtureName: (String) -> Unit = {},
    onCheck: () -> Unit = {},
    onSkipCheck: () -> Unit = {},
    onTarget: (SpeakerTarget) -> Unit = {}
) {
    val headphone = output == CalibrationOutput.HEADPHONE
    var fixtureConfirmed by remember { mutableStateOf(false) }
    var fixtureName by remember { mutableStateOf("") }
    var selectedMic by remember { mutableStateOf<Int?>(null) }
    var name by remember(step is CalibrationWizard.Step.Result) {
        mutableStateOf((step as? CalibrationWizard.Step.Result)?.carName.orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicMateDesignTokens.Surfaces.Canvas)
            .safeDrawingPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_baseline_arrow_back_24), contentDescription = "Close", tint = MusicMateDesignTokens.Text.Primary)
            }
            Text(
                if (headphone) "Measure headphones" else "Tune a speaker",
                color = MusicMateDesignTokens.Text.Primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }
        StageStrip(stageOf(step), finished = step is CalibrationWizard.Step.Done)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            when (step) {
                CalibrationWizard.Step.Checklist -> {
                    if (headphone) {
                        Title("Measure with a headphone fixture")
                        Body("Use a suitable ear simulator, coupler or repeatable sealed headphone fixture. A bare phone microphone cannot measure a flat headphone response. The target must come from this same fixture and microphone rig.")
                        Spacer(Modifier.height(16.dp))
                        Body("Import CSV or TSV with frequencyHz,targetDb columns, covering 100–8000 Hz. This creates a shared fixture correction. Listening creates a separate profile with per-ear preferences. Keep playback volume and microphone gain unchanged.")
                        OutlinedTextField(value = fixtureName, onValueChange = {
                            fixtureName = it.take(100)
                            onFixtureName(fixtureName)
                        }, label = { Text("Fixture / coupler name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = onImportTarget) { Text(targetName ?: "Import fixture target") }
                        TextButton(onClick = {
                            fixtureConfirmed = !fixtureConfirmed
                            onFixtureConfirmed(fixtureConfirmed)
                        }) { Text(if (fixtureConfirmed) "✓ Fixture and matching target confirmed" else "Confirm fixture and matching target") }
                    } else Checklist(unprocessedMic)
                    if (microphones.isNotEmpty()) {
                        Heading("Measurement microphone")
                        microphones.forEach { mic ->
                            TextButton(onClick = { selectedMic = mic.id; onMicrophone(mic.id) }) {
                                Text((if (selectedMic == mic.id) "✓ " else "") + mic.name)
                            }
                        }
                        Small("Keep the same microphone connected for every take and the check sweep.")
                    }
                }
                is CalibrationWizard.Step.Position -> Position(step, headphone)
                CalibrationWizard.Step.Fitting -> Working("Working out the correction")
                is CalibrationWizard.Step.Result -> Result(step.fit, unprocessedMic, name, headphone, step.target, onTarget) { name = it }
                is CalibrationWizard.Step.Verifying -> Verifying(step, headphone)
                is CalibrationWizard.Step.Done -> Done(step, headphone)
            }
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (step) {
                CalibrationWizard.Step.Checklist -> {
                    if (!headphone || (fixtureConfirmed && fixtureName.isNotBlank() && targetName != null)) PrimaryButton("Start", onClick = onStart)
                    else Small("Confirm your fixture and import its matching target to start.")
                }
                is CalibrationWizard.Step.Position ->
                    if (!step.measuring) PrimaryButton(if (step.problem == null) "Measure" else "Measure again", onClick = onMeasure)
                // Beside the pinned button, so a failed save is explained wherever the page is scrolled
                is CalibrationWizard.Step.Result -> Column {
                    step.problem?.let {
                        Notice(it)
                        Spacer(Modifier.height(12.dp))
                    }
                    PrimaryButton("Save and check") { onSave(name) }
                }
                is CalibrationWizard.Step.Verifying -> if (!step.measuring) Column {
                    PrimaryButton("Start check", onClick = onCheck)
                    TextButton(onClick = onSkipCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip check", color = MusicMateDesignTokens.Brand.Gold)
                    }
                }
                is CalibrationWizard.Step.Done -> PrimaryButton("Done", onClick = onClose)
                else -> {}
            }
        }
    }
}

private fun stageOf(step: CalibrationWizard.Step): Int = when (step) {
    CalibrationWizard.Step.Checklist -> 0
    is CalibrationWizard.Step.Position, CalibrationWizard.Step.Fitting -> 1
    is CalibrationWizard.Step.Result -> 2
    is CalibrationWizard.Step.Verifying, is CalibrationWizard.Step.Done -> 3
}

/** Four segments filled up to the current stage, and one line naming it; one line fits any text size. */
@Composable
private fun StageStrip(current: Int, finished: Boolean) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clearAndSetSemantics { contentDescription = "Step ${current + 1} of ${STAGES.size}, ${STAGES[current]}" }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STAGES.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= current) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Controls.TrackInactive)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                if (finished) "Finished" else "Step ${current + 1} of ${STAGES.size}  ·  ",
                color = MusicMateDesignTokens.Text.Muted,
                fontSize = 13.sp
            )
            if (!finished) Text(STAGES[current], color = MusicMateDesignTokens.Brand.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Checklist(unprocessedMic: Boolean) {
    Title("Measure how your speaker sounds")
    Body("A 10-second test sweep plays through the speaker while the phone listens at five spots around your head. MusicMate then builds an EQ that corrects it.")
    Spacer(Modifier.height(24.dp))
    Heading("Before you start")
    listOf(
        "In a car: parked, engine off or idling, windows closed, fan off",
        "At home: a quiet room, seated where you usually listen",
        "Android Auto or the Bluetooth speaker connected, so music plays through it",
        "The car's or speaker's own EQ and sound effects set to flat",
        "Volume at your usual level"
    ).forEachIndexed { i, text -> NumberedItem(i + 1, text) }
    if (!unprocessedMic) {
        Spacer(Modifier.height(16.dp))
        Notice("This phone only offers its voice microphone, so the result will be approximate.")
    }
    Spacer(Modifier.height(16.dp))
    Small("Recordings are analysed on this phone and discarded. Only the measured response and the EQ are saved.")
    Spacer(Modifier.height(8.dp))
    Small("Steady engine noise may limit correction at a few frequencies. For the fullest calibration, switch the engine off when possible.")
}

@Composable
private fun Position(step: CalibrationWizard.Step.Position, headphone: Boolean = false) {
    if (headphone) {
        Small("Reseat ${step.index + 1} of 5")
        Title(if (step.index == 0) "Initial placement" else "Reseat the headphone")
        Body("Place the same ear cup on the fixture with the same seal. Between takes, remove and reseat it to average placement differences. Keep the microphone, target, volume and output unchanged.")
        if (step.measuring) Countdown() else step.problem?.let { Notice(it) }
        NoiseNotice(step.noisyBandsHz)
        return
    }
    val name = CalibrationWizard.POSITIONS[step.index]
    Small("Spot ${step.index + 1} of ${CalibrationWizard.POSITIONS.size}")
    Title(name)
    Body(
        if (step.index == 0) "Hold the phone at ear height where your head is when you listen, microphone facing the front."
        else "Move the phone about 15 cm ${name.lowercase()} of the centre spot. Same height, same grip."
    )
    Spacer(Modifier.height(16.dp))
    PositionMap(current = step.index)
    Spacer(Modifier.height(16.dp))
    if (step.measuring) {
        Countdown()
    } else {
        step.problem?.let { Notice(it) } ?: Small("Keep still and quiet for about 14 seconds once you press Measure.")
    }
    NoiseNotice(step.noisyBandsHz)
}

/** Top view of the listener's head with the five spots: done ones teal, the current one gold. */
@Composable
private fun PositionMap(current: Int) {
    val ring = MusicMateDesignTokens.Surfaces.BorderPrimary
    val head = MusicMateDesignTokens.Text.Muted
    val pending = MusicMateDesignTokens.Controls.TrackInactive
    val gold = MusicMateDesignTokens.Brand.Gold
    val done = MusicMateDesignTokens.Brand.AcousticTeal
    val surface = MusicMateDesignTokens.Surfaces.Surface
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = MusicMateDesignTokens.Text.Muted, fontSize = 11.sp)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MusicMateDesignTokens.Surfaces.Surface)
            .border(1.dp, MusicMateDesignTokens.Surfaces.BorderSubtle, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = "Spot ${current + 1} of 5, ${CalibrationWizard.POSITIONS[current]}" }
    ) {
        val c = Offset(size.width / 2, size.height / 2 + 8.dp.toPx())
        val step = 62.dp.toPx()
        val forward = measurer.measure("Front", label)
        drawText(forward, topLeft = Offset(c.x - forward.size.width / 2, 8.dp.toPx()))
        drawCircle(ring, radius = step, center = c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        // Head outline with the nose pointing forward; the centre spot sits inside it
        drawCircle(head, radius = 24.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
        drawLine(head, Offset(c.x, c.y - 24.dp.toPx()), Offset(c.x, c.y - 31.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        val spots = listOf(c, Offset(c.x - step, c.y), Offset(c.x + step, c.y), Offset(c.x, c.y - step), Offset(c.x, c.y + step))
        spots.forEachIndexed { i, p ->
            val r = if (i == current) 9.dp.toPx() else 7.dp.toPx()
            // A gap in the ring around each spot keeps the dashes off the dots
            drawCircle(surface, radius = r + 4.dp.toPx(), center = p)
            when {
                i == current -> {
                    drawCircle(gold.copy(alpha = 0.25f), radius = r + 6.dp.toPx(), center = p)
                    drawCircle(gold, radius = r, center = p)
                }
                i < current -> drawCircle(done, radius = r, center = p)
                else -> drawCircle(pending, radius = r, center = p, style = Stroke(1.5.dp.toPx()))
            }
        }
        val gap = 14.dp.toPx()
        val left = measurer.measure("Left", label)
        val right = measurer.measure("Right", label)
        val back = measurer.measure("Back", label)
        drawText(left, topLeft = Offset(spots[1].x - gap - left.size.width, c.y - left.size.height / 2))
        drawText(right, topLeft = Offset(spots[2].x + gap, c.y - right.size.height / 2))
        drawText(back, topLeft = Offset(c.x + gap, spots[4].y - back.size.height / 2))
    }
}

/** Fills over the length of one capture; analysis follows, so it ends on "Analysing". */
@Composable
private fun Countdown() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween((SweepRecorder.TOTAL_SECONDS * 1000).toInt(), easing = LinearEasing))
    }
    val left = ((1f - progress.value) * SweepRecorder.TOTAL_SECONDS).toInt() + 1
    Text(
        if (progress.value < 1f) "Measuring. Keep still and quiet, $left s" else "Analysing",
        color = MusicMateDesignTokens.Text.Primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { progress.value },
        color = MusicMateDesignTokens.Brand.Gold,
        trackColor = MusicMateDesignTokens.Controls.TrackInactive,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    )
}

@Composable
private fun Result(fit: FitResult, unprocessedMic: Boolean, name: String, headphone: Boolean = false,
                   target: SpeakerTarget? = null, onTarget: (SpeakerTarget) -> Unit = {}, onName: (String) -> Unit) {
    Title(if (headphone) "Your headphones, corrected" else "Your speaker, corrected")
    Body("${fit.bands.size} EQ bands correct ${if (headphone) "the fixture response" else "the speaker"} from %.0f Hz to %.0f kHz.".format(fit.fromHz, fit.toHz / 1_000))
    if (target != null) {
        Spacer(Modifier.height(16.dp))
        Heading("Aim for")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeakerTarget.entries.forEach { choice ->
                FilterChip(selected = target == choice, onClick = { onTarget(choice) }, label = { Text(choice.label) })
            }
        }
        Small(when (target) {
            SpeakerTarget.CAR -> "More bass and softer treble, to carry over road noise."
            SpeakerTarget.ROOM -> "The extra bass and softer treble most listeners preferred in a 2013 listening-room study."
            SpeakerTarget.FLAT -> "An even response, with no added bass or treble."
        })
    }
    Spacer(Modifier.height(16.dp))
    ResponseGraph(fit)
    NoiseNotice(fit.excludedBandsHz)
    if (fit.fromHz > 31) {
        Spacer(Modifier.height(12.dp))
        Small(if (headphone) "Correction below %.0f Hz is left unchanged.".format(fit.fromHz) else "Bass below %.0f Hz was too quiet to measure and is left as it is.".format(fit.fromHz))
    }
    if (!unprocessedMic) {
        Spacer(Modifier.height(12.dp))
        Notice("Approximate: measured through the phone's voice microphone, so the correction only cuts and stops at %.0f kHz.".format(fit.toHz / 1_000))
    }
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(if (headphone) "Headphone name" else "Speaker name") },
        placeholder = { Text(if (headphone) "My headphones" else "My speaker") },
        singleLine = true,
        colors = speakerNameFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Small(if (headphone) "Saving selects this correction for this output. Then one more sweep can check the fixture response. USB EQ still needs its explicit enable switch." else "Saving makes this correction active. Then one more sweep at the centre spot can check it.")
}

@Composable
private fun Verifying(step: CalibrationWizard.Step.Verifying, headphone: Boolean = false) {
    Title(if (step.measuring) "Checking the correction" else "Check the correction")
    Body(if (headphone) "Keep the headphone seated on the fixture as for the last measurement." else "Hold the phone at the centre spot again.")
    if (!step.measuring) {
        Spacer(Modifier.height(8.dp))
        Small("The correction is saved. The check plays one more sweep through it; skipping leaves it \"Not checked\".")
    }
    Spacer(Modifier.height(16.dp))
    if (!headphone) PositionMap(current = 0)
    if (step.measuring) {
        Spacer(Modifier.height(16.dp))
        Countdown()
    }
    NoiseNotice(step.profile.excludedBandsHz)
}

@Composable
private fun Done(step: CalibrationWizard.Step.Done, headphone: Boolean = false) {
    val profile = step.profile
    Title("${profile.name} is calibrated")
    Spacer(Modifier.height(4.dp))
    val worst = profile.worstDeviationDb ?: 0.0
    when (profile.verified) {
        true -> Verdict(if (profile.excludedBandsHz.isEmpty() && !step.partialCheck) "Check passed, within ±3 dB" else "Reliable frequencies passed, within ±3 dB", "Largest difference %.1f dB.".format(worst), MusicMateDesignTokens.Brand.AcousticTeal)
        false -> Verdict("Off by up to %.1f dB".format(worst), if (headphone) "The correction is saved for this output. Reseating and measuring again may get closer." else "The correction is saved and active. Measuring again may get closer.", MusicMateDesignTokens.Brand.GoldAmber)
        null -> Verdict("Not checked", (if (step.checkSkipped) "The check sweep was skipped." else "The check sweep could not be measured.") +
            if (headphone) " The correction is saved for this output." else " The correction is saved and active.", MusicMateDesignTokens.Brand.GoldAmber)
    }
    NoiseNotice(profile.excludedBandsHz)
    Spacer(Modifier.height(16.dp))
    Body(if (headphone) "The check describes this fixture and target, not an absolute headphone response. Manage this profile in MateTune. USB output requires explicit EQ consent." else "MateTune applies this correction under the sound mode you choose, and picks it again whenever this speaker connects. Switch modes from the MateTune chip in Now Playing, and manage speakers in MateTune.")
}

@Composable
private fun NoiseNotice(bandsHz: List<Double>) {
    if (bandsHz.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Notice("Partial calibration: background noise near ${bandsHz.joinToString { "%.0f Hz".format(it) }}. These regions receive at most 1 dB of EQ and are excluded from the check.")
}

@Composable
private fun Verdict(title: String, detail: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MusicMateDesignTokens.Surfaces.Surface)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = MusicMateDesignTokens.Text.Telemetry, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Measured, target and corrected response, 20 Hz to 20 kHz on a log axis, ±15 dB. */
@Composable
private fun ResponseGraph(fit: FitResult) {
    val grid = FrequencyGrid.HZ
    val measurer = rememberTextMeasurer()
    val axis = TextStyle(color = MusicMateDesignTokens.Text.Muted, fontSize = 10.sp)
    val gridLine = MusicMateDesignTokens.Surfaces.BorderSubtle
    val zeroLine = MusicMateDesignTokens.Surfaces.BorderPrimary
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MusicMateDesignTokens.Surfaces.Surface)
                .border(1.dp, MusicMateDesignTokens.Surfaces.BorderSubtle, RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Frequency response: measured, target and corrected" + if (fit.excludedBandsHz.isEmpty()) "" else "; gaps mark frequencies obscured by noise" }
        ) {
            val plotH = size.height - 18.dp.toPx()
            fun x(hz: Double) = (ln(hz / 20.0) / ln(1_000.0) * size.width).toFloat()
            fun y(db: Double) = (plotH / 2 - db.coerceIn(-15.0, 15.0) / 15.0 * plotH / 2).toFloat()
            for ((hz, text) in listOf(100.0 to "100", 1_000.0 to "1k", 10_000.0 to "10k")) {
                drawLine(gridLine, Offset(x(hz), 0f), Offset(x(hz), plotH))
                drawText(measurer, text, Offset(x(hz) + 3.dp.toPx(), plotH + 3.dp.toPx()), axis)
            }
            for (db in listOf(10.0, -10.0)) {
                drawLine(gridLine, Offset(0f, y(db)), Offset(size.width, y(db)))
                drawText(measurer, "%+.0f".format(db), Offset(4.dp.toPx(), y(db) + 2.dp.toPx()), axis)
            }
            drawLine(zeroLine, Offset(0f, y(0.0)), Offset(size.width, y(0.0)))
            drawText(measurer, "0 dB", Offset(4.dp.toPx(), y(0.0) + 2.dp.toPx()), axis)
            drawText(measurer, "Hz", Offset(size.width - 18.dp.toPx(), plotH + 3.dp.toPx()), axis)
            fun curve(values: DoubleArray, color: Color, dashed: Boolean = false) {
                val path = Path()
                var connected = false
                values.forEachIndexed { i, db ->
                    if (PeqFitter.isExcluded(grid[i], fit.excludedBandsHz)) {
                        connected = false
                    } else {
                        if (connected) path.lineTo(x(grid[i]), y(db)) else path.moveTo(x(grid[i]), y(db))
                        connected = true
                    }
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null))
            }
            curve(fit.measuredDb, Measured)
            curve(fit.targetDb, Target, dashed = true)
            curve(fit.predictedDb, Predicted)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Measured", Measured)
            Legend("Target", Target)
            Legend("Corrected", Predicted)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MusicMateDesignTokens.Text.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun Working(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = MusicMateDesignTokens.Brand.Gold, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Body(text)
    }
}

@Composable
private fun Title(text: String) = Text(
    text,
    color = MusicMateDesignTokens.Text.Primary,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier
        .padding(bottom = 8.dp)
        .semantics { heading() }
)

@Composable
private fun Heading(text: String) = Text(
    text,
    color = MusicMateDesignTokens.Text.Primary,
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier
        .padding(bottom = 8.dp)
        .semantics { heading() }
)

@Composable
private fun Body(text: String) = Text(text, color = MusicMateDesignTokens.Text.Telemetry, fontSize = 15.sp, lineHeight = 22.sp)

@Composable
private fun Small(text: String) = Text(text, color = MusicMateDesignTokens.Text.Muted, fontSize = 13.sp, lineHeight = 18.sp)

/** A caution with an icon, so it does not rely on colour alone. */
@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MusicMateDesignTokens.Brand.GoldOutline, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painterResource(R.drawable.rounded_info_24),
            contentDescription = null,
            tint = MusicMateDesignTokens.Brand.GoldAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = MusicMateDesignTokens.Text.Telemetry, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun NumberedItem(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .sizeIn(minWidth = 26.dp, minHeight = 26.dp)
                .border(1.dp, MusicMateDesignTokens.Brand.GoldOutline, CircleShape)
                .padding(horizontal = 6.dp)
        ) {
            Text("$number", color = MusicMateDesignTokens.Brand.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Body(text)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MusicMateDesignTokens.Brand.Gold,
            contentColor = MusicMateDesignTokens.Surfaces.Canvas
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** Asked before leaving with measured spots or an unsaved result. */
@Composable
fun StopCalibrationDialog(onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop calibration?") },
        text = { Text("The spots measured so far are lost.") },
        confirmButton = {
            TextButton(onClick = onStop) { Text("Stop", color = MusicMateDesignTokens.Status.Destructive, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep going", color = MusicMateDesignTokens.Brand.Gold) }
        },
        containerColor = MusicMateDesignTokens.Surfaces.SurfaceElevated,
        titleContentColor = MusicMateDesignTokens.Text.Primary,
        textContentColor = MusicMateDesignTokens.Text.Telemetry
    )
}
