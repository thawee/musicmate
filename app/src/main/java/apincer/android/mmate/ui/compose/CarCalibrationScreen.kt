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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import apincer.android.mmate.audio.calibration.CalibrationWizard
import apincer.android.mmate.audio.calibration.FitResult
import apincer.android.mmate.audio.calibration.FrequencyGrid
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
    onClose: () -> Unit
) {
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
                "Tune a speaker",
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
                CalibrationWizard.Step.Checklist -> Checklist(unprocessedMic)
                is CalibrationWizard.Step.Position -> Position(step)
                CalibrationWizard.Step.Fitting -> Working("Working out the correction")
                is CalibrationWizard.Step.Result -> Result(step.fit, unprocessedMic, name) { name = it }
                is CalibrationWizard.Step.Verifying -> Verifying()
                is CalibrationWizard.Step.Done -> Done(step)
            }
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (step) {
                CalibrationWizard.Step.Checklist -> PrimaryButton("Start", onClick = onStart)
                is CalibrationWizard.Step.Position ->
                    if (!step.measuring) PrimaryButton(if (step.problem == null) "Measure" else "Measure again", onClick = onMeasure)
                is CalibrationWizard.Step.Result -> PrimaryButton("Save and check") { onSave(name) }
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
}

@Composable
private fun Position(step: CalibrationWizard.Step.Position) {
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
private fun Result(fit: FitResult, unprocessedMic: Boolean, name: String, onName: (String) -> Unit) {
    Title("Your speaker, corrected")
    Body("${fit.bands.size} EQ bands correct the speaker from %.0f Hz to 8 kHz.".format(fit.fromHz))
    Spacer(Modifier.height(16.dp))
    ResponseGraph(fit)
    if (fit.fromHz > 31) {
        Spacer(Modifier.height(12.dp))
        Small("Bass below %.0f Hz was too quiet to measure and is left as it is.".format(fit.fromHz))
    }
    if (!unprocessedMic) {
        Spacer(Modifier.height(12.dp))
        Notice("Approximate: measured through the phone's voice microphone.")
    }
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Speaker name") },
        placeholder = { Text("My speaker") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MusicMateDesignTokens.Text.Primary,
            unfocusedTextColor = MusicMateDesignTokens.Text.Primary,
            focusedBorderColor = MusicMateDesignTokens.Brand.Gold,
            unfocusedBorderColor = MusicMateDesignTokens.Surfaces.BorderPrimary,
            focusedLabelColor = MusicMateDesignTokens.Brand.Gold,
            unfocusedLabelColor = MusicMateDesignTokens.Text.Muted,
            cursorColor = MusicMateDesignTokens.Brand.Gold
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Small("Saving makes this correction active, then one more sweep at the centre spot checks it.")
}

@Composable
private fun Verifying() {
    Title("Checking the correction")
    Body("Hold the phone at the centre spot again.")
    Spacer(Modifier.height(16.dp))
    PositionMap(current = 0)
    Spacer(Modifier.height(16.dp))
    Countdown()
}

@Composable
private fun Done(step: CalibrationWizard.Step.Done) {
    val profile = step.profile
    Title("${profile.name} is calibrated")
    Spacer(Modifier.height(4.dp))
    val worst = profile.worstDeviationDb ?: 0.0
    when (profile.verified) {
        true -> Verdict("Within ±3 dB of the target", "Largest difference %.1f dB.".format(worst), MusicMateDesignTokens.Brand.AcousticTeal)
        false -> Verdict("Off by up to %.1f dB".format(worst), "The correction is saved and active. Measuring again may get closer.", MusicMateDesignTokens.Brand.GoldAmber)
        null -> Verdict("Not checked", "The check sweep could not be measured. The correction is saved and active.", MusicMateDesignTokens.Brand.GoldAmber)
    }
    Spacer(Modifier.height(16.dp))
    Body("Speaker Tuning applies this correction under the sound mode you choose, and picks it again whenever this speaker connects. Switch modes from the Speaker Tuning chip in Now Playing, and manage speakers in Settings.")
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
                .semantics { contentDescription = "Frequency response: measured, target and corrected" }
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
                values.forEachIndexed { i, db -> if (i == 0) path.moveTo(x(grid[i]), y(db)) else path.lineTo(x(grid[i]), y(db)) }
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
