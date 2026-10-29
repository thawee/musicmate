package apincer.android.mmate.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import apincer.android.mmate.audio.headphones.ListeningSample
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneKind
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneTestSignal
import apincer.android.mmate.audio.headphones.HeadphoneTestBand
import apincer.android.mmate.audio.headphones.HeadphoneTone
import apincer.android.mmate.audio.headphones.HeadphoneTonePreset
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import apincer.android.mmate.audio.calibration.CalibrationOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TuningEar(val label: String) { BOTH("Both"), LEFT("Left"), RIGHT("Right") }

private val GUIDED_BANDS = listOf(HeadphoneTestBand.BASS, HeadphoneTestBand.WARMTH, HeadphoneTestBand.MIDS, HeadphoneTestBand.CLARITY, HeadphoneTestBand.TREBLE)

@Composable
internal fun ManualHeadphoneDialog(
    profile: HeadphoneProfile?,
    endpoint: HeadphoneEndpoint?,
    busy: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onSave: (HeadphoneProfile) -> Unit,
    onPlay: suspend (HeadphoneTestSignal, CarDspSettings, HeadphoneEndpoint, HeadphoneTestBand, ListeningSample?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onStop: () -> Unit = {},
    output: CalibrationOutput = profile?.output ?: CalibrationOutput.HEADPHONE,
    playbackMode: SoundMode? = null
) {
    val calibrationOutput = remember(profile?.id) { profile?.output ?: output }
    val speaker = calibrationOutput == CalibrationOutput.SPEAKER
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var tuning by remember(profile?.id) { mutableStateOf(profile?.manual ?: ManualHeadphoneTuning()) }
    var page by remember(profile?.id) { mutableStateOf(0) }
    var guided by remember(profile?.id) { mutableStateOf(true) }
    var guideIndex by remember(profile?.id) { mutableStateOf(0) }
    val testBand = if (page == 2 && guided) GUIDED_BANDS.getOrNull(guideIndex) ?: HeadphoneTestBand.MULTITONE else HeadphoneTestBand.MULTITONE
    val samples = when (testBand) {
        HeadphoneTestBand.BASS -> listOf(ListeningSample.BASS_GROOVE, ListeningSample.MUSIC)
        HeadphoneTestBand.WARMTH -> listOf(ListeningSample.ACOUSTIC, ListeningSample.MALE_VOICE, ListeningSample.FEMALE_VOICE)
        HeadphoneTestBand.MIDS -> listOf(ListeningSample.MALE_VOICE, ListeningSample.FEMALE_VOICE, ListeningSample.ACOUSTIC)
        HeadphoneTestBand.CLARITY -> listOf(ListeningSample.PIANO, ListeningSample.MALE_VOICE, ListeningSample.FEMALE_VOICE)
        HeadphoneTestBand.TREBLE -> listOf(ListeningSample.SHAKER, ListeningSample.MUSIC)
        HeadphoneTestBand.MULTITONE -> listOf(ListeningSample.MUSIC)
    }
    var sample by remember(profile?.id, page, guided, guideIndex) { mutableStateOf(samples.first()) }
    var ear by remember { mutableStateOf(TuningEar.BOTH) }
    var bypass by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val keyboard = LocalSoftwareKeyboardController.current
    val stop by rememberUpdatedState(onStop)
    val scrollState = remember(page, testBand) { ScrollState(0) }

    fun stopTest() {
        playback?.cancel()
        playback = null
        stop()
    }

    fun play(signal: HeadphoneTestSignal) {
        val output = endpoint ?: return
        stopTest()
        localError = null
        var request: Job? = null
        request = scope.launch {
            try {
                onPlay(signal, tuning.settings(), output, testBand, sample, bypass)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                localError = failure.message ?: "The listening test could not be played."
            } finally {
                if (playback === request) playback = null
            }
        }
        playback = request
    }

    // Page changes, route changes and pausing all end the short, explicitly requested test.
    DisposableEffect(endpoint?.key, page, testBand, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) stopTest()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            stopTest()
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    fun updateTone(change: (HeadphoneTone) -> HeadphoneTone) {
        stopTest()
        tuning = tuning.copy(
            left = if (ear != TuningEar.RIGHT) change(tuning.left) else tuning.left,
            right = if (ear != TuningEar.LEFT) change(tuning.right) else tuning.right
        )
    }

    ListeningDialogFrame(
        onDismissRequest = { if (!busy) { stopTest(); onDismiss() } },
        title = { Text(if (profile == null) "Create listening profile" else "Edit listening setup") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Step ${page + 1} of 3", fontSize = 12.sp)
                when (page) {
                    0 -> {
                        Text(if (speaker) "Name this speaker setup. Your listening adjustments stay with this profile."
                            else "Name this pair and choose its type. Your listening adjustments stay with this pair.")
                        OutlinedTextField(value = name, onValueChange = { if (it.length <= 200) name = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text(if (speaker) "Speaker name" else "Pair name") }, singleLine = true, enabled = !busy)
                        if (!speaker) HeadphoneKind.entries.forEach { kind ->
                            ListeningChoice(when (kind) {
                                HeadphoneKind.OVER_EAR -> "Over-ear"
                                HeadphoneKind.EARBUDS -> "Earbuds"
                                HeadphoneKind.IN_EAR -> "In-ear"
                            }, tuning.kind == kind, !busy) { tuning = tuning.copy(kind = kind) }
                        }
                        Text(if (speaker) "This is a personal listening setup. Sit where you usually listen and start at a low volume."
                            else "This is a personal listening setup. Start with a comfortable fit and a low listening volume.", fontSize = 12.sp)
                    }
                    1 -> {
                        Text("Check sides and center")
                        Text("Start with your device volume low. Each sample lasts 3 seconds, or 6 seconds alternating sides. Adjust balance until the centered sound feels centered.", fontSize = 12.sp)
                        Text("Balance: ${formatDb(tuning.balanceDb)} dB")
                        Text("Negative favors left; positive favors right. Balance only turns down the opposite ear.", fontSize = 12.sp)
                        Slider(value = tuning.balanceDb.toFloat(), onValueChange = {
                            stopTest(); tuning = tuning.copy(balanceDb = it.toDouble())
                        }, valueRange = -12f..12f, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Left-right balance" })
                    }
                    2 -> {
                        Text("Tune by listening")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = guided, onClick = { stopTest(); guideIndex = 0; guided = true },
                                label = { Text("Musical guide") }, enabled = !busy)
                            FilterChip(selected = !guided, onClick = { stopTest(); guided = false },
                                label = { Text("Advanced") }, enabled = !busy)
                        }
                        if (guided) {
                            TuningLabel(if (guideIndex < GUIDED_BANDS.size) "LISTENING ${guideIndex + 1} / ${GUIDED_BANDS.size}" else "READY TO COMPARE")
                            Text(testBand.label, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            TuningHint(when (testBand) {
                                HeadphoneTestBand.BASS -> "Listen for the weight of the kick and bass. Aim for impact without boom."
                                HeadphoneTestBand.WARMTH -> "Listen to the body of the plucks or a voice. Aim for fullness without muddiness."
                                HeadphoneTestBand.MIDS -> "Choose a male or female voice. Aim for a natural voice that feels present."
                                HeadphoneTestBand.CLARITY -> "Listen to piano attacks or spoken words. Aim for definition without sharpness."
                                HeadphoneTestBand.TREBLE -> "Listen to the shaker and cymbals. Aim for detail without harshness."
                                HeadphoneTestBand.MULTITONE -> "Compare the full musical mix with Original and Adjusted. Save when the overall balance feels comfortable."
                            })
                        } else {
                            TuningHint("Five independent controls, each −6 to +6 dB. Frequencies show where each adjustment is centered.")
                        }
                        TuningHint("Keep volume low. Compare the same example; this tunes your preference.")
                        if (testBand != HeadphoneTestBand.MULTITONE || !guided) {
                            Text(if (speaker) "Channel to adjust" else "Ear to adjust", fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            TuningEar.entries.forEach { option ->
                                Column(Modifier.weight(1f)) {
                                    ListeningChoice(option.label, ear == option, !busy) { ear = option }
                                }
                            }
                        }
                        if (ear == TuningEar.BOTH && tuning.left != tuning.right) {
                            TuningHint("Sides differ. Moving a slider sets both sides to that value.")
                        }
                        val tone = if (ear == TuningEar.RIGHT) tuning.right else tuning.left
                        if (!guided) {
                            val selectedPreset = HeadphoneTonePreset.of(tone)?.takeIf { ear != TuningEar.BOTH || tuning.left == tuning.right }
                            Text("Start from a sound", fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HeadphoneTonePreset.entries.forEach { preset ->
                                    FilterChip(selected = selectedPreset == preset, onClick = { updateTone { preset.tone } },
                                        label = { Text(preset.label) }, enabled = !busy)
                                }
                            }
                        }
                        if (!guided) TuningHint("100 Hz bass · 300 Hz warmth · 1 kHz vocals · 3 kHz clarity · 8 kHz detail")
                        if (!guided || testBand == HeadphoneTestBand.BASS)
                            ListeningToneSlider("Bass impact", tone.bassDb, !busy) { value -> updateTone { it.copy(bassDb = value) } }
                        if (!guided || testBand == HeadphoneTestBand.WARMTH)
                            ListeningToneSlider("Warmth", tone.warmthDb, !busy) { value -> updateTone { it.copy(warmthDb = value) } }
                        if (!guided || testBand == HeadphoneTestBand.MIDS)
                            ListeningToneSlider("Vocal naturalness", tone.midDb, !busy) { value -> updateTone { it.copy(midDb = value) } }
                        if (!guided || testBand == HeadphoneTestBand.CLARITY)
                            ListeningToneSlider("Clarity", tone.clarityDb, !busy) { value -> updateTone { it.copy(clarityDb = value) } }
                        if (!guided || testBand == HeadphoneTestBand.TREBLE)
                            ListeningToneSlider("Treble detail", tone.trebleDb, !busy) { value -> updateTone { it.copy(trebleDb = value) } }
                        if (guided && guideIndex < GUIDED_BANDS.size) {
                            fun advanceTone() {
                                stopTest()
                                guideIndex++
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { advanceTone() }, enabled = !busy) {
                                    Text("Next step")
                                }
                                TextButton(onClick = { advanceTone() }, enabled = !busy) { Text("Skip step") }
                            }
                        }
                        if (endpoint?.usb == true) TuningHint("Save selects this USB profile. Enable its EQ switch to process audio.")
                    }
                }
                if (page > 0) {
                    Text(endpoint?.name ?: "Connect one local audio output to play a listening test.", fontSize = 12.sp)
                    TuningLabel("LISTENING EXAMPLE")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        samples.forEach { choice ->
                            FilterChip(selected = sample == choice, enabled = !busy,
                                onClick = { stopTest(); sample = choice }, label = { Text(choice.label) })
                        }
                    }
                    if (sample.speechAssetPath != null) TuningHint("Voices: Open Speech Repository · speech recording, limited high treble.")
                    TuningHint("Musical examples use matched average levels. These previews include only your calibration.")
                    TuningHint(if (playbackMode == null)
                        "Choose Neutral in Audio tuning to hear your saved calibration without extra tone shaping."
                    else if (playbackMode == SoundMode.REFERENCE)
                        "Playback mode: Neutral. Your saved calibration plays without extra tone shaping."
                    else if (playbackMode == SoundMode.CUSTOM)
                        "Playback mode: Custom. Saving clears its extra bass and treble trims; your calibration remains."
                    else "Playback mode: ${playbackMode.label}. This adds processing after saving. Choose Neutral in Audio tuning to hear only your calibration.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = bypass, enabled = !busy, onClick = { stopTest(); bypass = true }, label = { Text("Original") })
                        FilterChip(selected = !bypass, enabled = !busy, onClick = { stopTest(); bypass = false }, label = { Text("Adjusted") })
                    }
                    val playing = playback?.isActive == true
                    val canPlay = endpoint != null && !busy && !playing
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.LEFT) }, enabled = canPlay, modifier = Modifier.weight(1f)) { Text("Play left") }
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.RIGHT) }, enabled = canPlay, modifier = Modifier.weight(1f)) { Text("Play right") }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.ALTERNATING) }, enabled = canPlay) { Text("Play alternating") }
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.CENTER) }, enabled = canPlay) { Text("Play centered") }
                    }
                    TextButton(onClick = { stopTest() }, enabled = playing, modifier = Modifier.fillMaxWidth()) { Text("Stop test") }
                }
                (localError ?: error)?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            if (page < 2) {
                TextButton(onClick = { stopTest(); keyboard?.hide(); page++ }, enabled = !busy && name.isNotBlank()) { Text("Next") }
            } else {
                TextButton(onClick = {
                    stopTest()
                    localError = null
                    try {
                        onSave(profile?.copy(name = name.trim(), manual = tuning) ?: HeadphoneProfile.createManual(name.trim(), tuning, calibrationOutput))
                    } catch (invalid: IllegalArgumentException) {
                        localError = invalid.message ?: "Check the pair name and listening adjustments."
                    }
                }, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Saving…" else "Save profile") }
            }
        },
        dismissButton = {
            Row {
                if (page > 0) TextButton(onClick = { stopTest(); page-- }, enabled = !busy) { Text("Back") }
                TextButton(onClick = { stopTest(); onDismiss() }, enabled = !busy) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ListeningDialogFrame(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = MusicMateDesignTokens.Brand.Gold,
            secondaryContainer = Color.White,
            onSecondaryContainer = MusicMateDesignTokens.Surfaces.Canvas,
            surface = MusicMateDesignTokens.Surfaces.Canvas,
            onSurface = MusicMateDesignTokens.Text.Primary,
            onSurfaceVariant = MusicMateDesignTokens.Text.Secondary,
            outline = MusicMateDesignTokens.Surfaces.BorderSubtle
        )) {
            Surface(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.94f).imePadding(),
                shape = RoundedCornerShape(24.dp), color = MusicMateDesignTokens.Surfaces.Canvas,
                contentColor = MusicMateDesignTokens.Text.Primary) {
                Column {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TuningLabel("MUSIC MATE / LISTENING")
                        title()
                    }
                    HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
                    Column(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp)) { text() }
                    HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
                    FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ListeningToneSlider(label: String, value: Double, enabled: Boolean, onChange: (Double) -> Unit) {
    Column {
        Text("$label: ${formatDb(value)} dB")
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toDouble()) }, valueRange = -6f..6f, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "$label adjustment" })
    }
}

private fun formatDb(value: Double): String = String.format(Locale.ROOT, "%+.1f", value)
