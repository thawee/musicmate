package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneKind
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneTestSignal
import apincer.android.mmate.audio.headphones.HeadphoneTone
import apincer.android.mmate.audio.headphones.HeadphoneTonePreset
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TuningEar(val label: String) { BOTH("Both"), LEFT("Left"), RIGHT("Right") }

@Composable
internal fun ManualHeadphoneDialog(
    profile: HeadphoneProfile?,
    endpoint: HeadphoneEndpoint?,
    busy: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onSave: (HeadphoneProfile) -> Unit,
    onPlay: suspend (HeadphoneTestSignal, CarDspSettings, HeadphoneEndpoint) -> Unit = { _, _, _ -> },
    onStop: () -> Unit = {}
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var tuning by remember(profile?.id) { mutableStateOf(profile?.manual ?: ManualHeadphoneTuning()) }
    var page by remember(profile?.id) { mutableStateOf(0) }
    var ear by remember { mutableStateOf(TuningEar.BOTH) }
    var bypass by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val keyboard = LocalSoftwareKeyboardController.current
    val stop by rememberUpdatedState(onStop)
    val scrollState = remember(page) { ScrollState(0) }

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
                onPlay(signal, if (bypass) CarDspSettings(emptyList()) else tuning.settings(), output)
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
    DisposableEffect(endpoint?.key, page, lifecycleOwner) {
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

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!busy) { stopTest(); onDismiss() } },
        title = { Text(if (profile == null) "Create DIY profile" else "Edit listening setup") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Step ${page + 1} of 3", fontSize = 12.sp)
                when (page) {
                    0 -> {
                        Text("Name this pair and choose its type. Your listening adjustments stay with this pair.")
                        OutlinedTextField(value = name, onValueChange = { if (it.length <= 200) name = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Pair name") }, singleLine = true, enabled = !busy)
                        HeadphoneKind.entries.forEach { kind ->
                            ListeningChoice(when (kind) {
                                HeadphoneKind.OVER_EAR -> "Over-ear"
                                HeadphoneKind.EARBUDS -> "Earbuds"
                                HeadphoneKind.IN_EAR -> "In-ear"
                            }, tuning.kind == kind, !busy) { tuning = tuning.copy(kind = kind) }
                        }
                        Text("This is a personal listening setup. Start with a comfortable fit and a low listening volume.", fontSize = 12.sp)
                    }
                    1 -> {
                        Text("Check sides and center")
                        Text("Start with your device volume low. Each quiet test ends within 4 seconds. Adjust balance until the centered sound feels centered.", fontSize = 12.sp)
                        Text("Balance: ${formatDb(tuning.balanceDb)} dB")
                        Text("Negative favors left; positive favors right. Balance only turns down the opposite ear.", fontSize = 12.sp)
                        Slider(value = tuning.balanceDb.toFloat(), onValueChange = {
                            stopTest(); tuning = tuning.copy(balanceDb = it.toDouble())
                        }, valueRange = -12f..12f, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Left-right balance" })
                    }
                    2 -> {
                        Text("Shape the sound")
                        Text("Choose which ear to change. Bass, mids and treble each allow −6 to +6 dB.", fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth()) {
                            TuningEar.entries.forEach { option ->
                                Column(Modifier.weight(1f)) {
                                    ListeningChoice(option.label, ear == option, !busy) { ear = option }
                                }
                            }
                        }
                        if (ear == TuningEar.BOTH && tuning.left != tuning.right) {
                            Text("Ears differ. Sliders show the left ear; moving one applies that tone value to both ears.", fontSize = 12.sp)
                        }
                        val tone = if (ear == TuningEar.RIGHT) tuning.right else tuning.left
                        val selectedPreset = HeadphoneTonePreset.of(tone)?.takeIf { ear != TuningEar.BOTH || tuning.left == tuning.right }
                        Text("Start from a sound", fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeadphoneTonePreset.entries.forEach { preset ->
                                FilterChip(selected = selectedPreset == preset, onClick = { updateTone { preset.tone } },
                                    label = { Text(preset.label) }, enabled = !busy)
                            }
                        }
                        ListeningToneSlider("Bass", tone.bassDb, !busy) { value -> updateTone { it.copy(bassDb = value) } }
                        ListeningToneSlider("Mids", tone.midDb, !busy) { value -> updateTone { it.copy(midDb = value) } }
                        ListeningToneSlider("Treble", tone.trebleDb, !busy) { value -> updateTone { it.copy(trebleDb = value) } }
                        Text(if (endpoint?.usb == true) "Compare the draft with bypass before saving. Save selects this pair for the USB DAC; turn on its EQ switch to process audio."
                            else "Compare the draft with bypass before saving. Save turns this EQ on for the connected headphones.", fontSize = 12.sp)
                    }
                }
                if (page > 0) {
                    Text(endpoint?.name ?: "Connect wired, USB DAC or Bluetooth headphones to play a listening test.", fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(bypass, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { stopTest(); bypass = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bypass, onCheckedChange = null, enabled = !busy)
                        Text("Bypass draft EQ")
                    }
                    val playing = playback?.isActive == true
                    val canPlay = endpoint != null && !busy && !playing
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.LEFT) }, enabled = canPlay, modifier = Modifier.weight(1f)) { Text("Play left") }
                        OutlinedButton(onClick = { play(HeadphoneTestSignal.RIGHT) }, enabled = canPlay, modifier = Modifier.weight(1f)) { Text("Play right") }
                    }
                    OutlinedButton(onClick = { play(HeadphoneTestSignal.ALTERNATING) }, enabled = canPlay, modifier = Modifier.fillMaxWidth()) { Text("Play alternating") }
                    OutlinedButton(onClick = { play(HeadphoneTestSignal.CENTER) }, enabled = canPlay, modifier = Modifier.fillMaxWidth()) { Text("Play centered") }
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
                        onSave(profile?.copy(name = name.trim(), manual = tuning) ?: HeadphoneProfile.createManual(name.trim(), tuning))
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
