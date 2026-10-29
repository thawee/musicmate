package apincer.android.mmate.ui.compose

import android.content.Intent
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CalibrationMethod
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CorrectionProfiles
import apincer.android.mmate.audio.calibration.CorrectionProfileChoice
import apincer.android.mmate.audio.dsp.CarDspPreferences
import apincer.android.mmate.ui.CarCalibrationActivity
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import apincer.android.mmate.audio.headphones.AutoEqClient
import apincer.android.mmate.audio.headphones.AutoEqEntry
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphonePreferences
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneProfileParser
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.audio.headphones.HeadphoneTestPlayer
import apincer.android.mmate.audio.dsp.SoundMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun HeadphoneDspChip(modifier: Modifier = Modifier) = AudioTuningChip(modifier)

@Composable
fun AudioTuningChip(modifier: Modifier = Modifier) {
    val status by HeadphoneDsp.state.collectAsState()
    val legacy by CarDspPreferences.state.collectAsState()
    val library by HeadphonePreferences.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val general = status.profile != null || status.endpoint?.key in library.bindings || status.endpoint?.wired == true || status.endpoint?.usb == true
    val enabled = if (general) status.active else status.endpoint != null && legacy.enabled
    val unavailable = if (general) tuningAudibility(status).label == "Unavailable" else status.endpoint == null
    val stateLabel = if (unavailable) "Unavailable" else if (enabled) "On" else "Bypassed"
    val mode = if (general) status.binding.soundMode else legacy.mode
    val accent = if (enabled) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Secondary
    Row(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(MusicMateDesignTokens.Surfaces.GlassPill)
            .clickable { open = true }.heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Audio tuning"
                stateDescription = "$stateLabel, ${mode.label}, ${status.profile?.name ?: legacy.profileName ?: "No correction"}"
            }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(androidx.compose.ui.res.painterResource(apincer.android.mmate.R.drawable.rounded_equalizer_24), null, tint = accent, modifier = Modifier.size(18.dp))
        Text(androidx.compose.ui.text.buildAnnotatedString {
            append("${mode.label} · ")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = accent))
            append(stateLabel)
            pop()
        }, color = MusicMateDesignTokens.Text.Primary, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
    if (open) QuickAudioTuningDialog { open = false }
}

@Composable
internal fun QuickAudioTuningDialog(onDismissRequest: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    AudioTuningSheet(onDismissRequest, compact = !full,
        onManage = if (full) null else ({ full = true })) {
        HeadphoneAudioSettings(quick = !full, onFullTuning = { full = true })
    }
}

@Composable
internal fun AudioTuningDialog(onDismissRequest: () -> Unit) {
    AudioTuningSheet(onDismissRequest) { HeadphoneAudioSettings() }
}

/** Storage and document reads stay off the main thread; the controls only render immutable state. */
@Composable
fun HeadphoneAudioSettings(initialOutput: CalibrationOutput? = null, quick: Boolean = false, onFullTuning: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val library by HeadphonePreferences.state.collectAsState()
    val status by HeadphoneDsp.state.collectAsState()
    val scope = rememberCoroutineScope()
    var output by remember(initialOutput) { mutableStateOf(initialOutput ?: status.profile?.output ?: status.endpoint?.output ?: CalibrationOutput.HEADPHONE) }
    var speakers by remember { mutableStateOf(emptyList<CarProfile>()) }
    val speakerChoice by CarDspPreferences.state.collectAsState()
    val hasGeneralBinding = status.endpoint?.key in library.bindings
    val useLegacy = !hasGeneralBinding && status.profile == null && status.endpoint != null &&
        status.endpoint?.usb != true && status.endpoint?.wired != true
    val targetEndpoint = status.endpoint
    LaunchedEffect(status.profile?.id, status.endpoint?.key) {
        output = status.profile?.output ?: initialOutput ?: status.endpoint?.output ?: CalibrationOutput.HEADPHONE
    }
    val calibration = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { speakers = withContext(Dispatchers.IO) { CarDspPreferences.profiles(context).list() } }
    }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<HeadphoneImportDraft?>(null) }
    var manualDraft by remember { mutableStateOf<ManualHeadphoneDraft?>(null) }
    var fileOutput by remember { mutableStateOf(CalibrationOutput.HEADPHONE) }
    val testPlayer = remember(context) { HeadphoneTestPlayer(context) }
    val autoEq = remember(context) { AutoEqClient(File(context.cacheDir, "autoeq_index.md")) }
    var searching by remember { mutableStateOf(false) }
    var autoEqEntries by remember { mutableStateOf<List<AutoEqEntry>?>(null) }

    fun execute(work: () -> Unit, onSuccess: () -> Unit = {}) {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { work() }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Audio tuning could not be saved."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(context) {
        try {
            withContext(Dispatchers.IO) {
                HeadphonePreferences.init(context)
                HeadphonePreferences.refreshRoute(context)
                speakers = CarDspPreferences.profiles(context).list()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Audio tuning could not be loaded."
        } finally {
            busy = false
        }
    }

    DisposableEffect(context, lifecycleOwner) {
        fun refreshRoute() {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { HeadphonePreferences.refreshRoute(context) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "The audio output could not be refreshed."
                }
            }
        }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshRoute()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshRoute()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRoute()
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val importOutput = fileOutput
            var loaded: HeadphoneImportDraft? = null
            execute(work = {
                val source = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Imported parametric EQ file"
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = ByteArray(HeadphoneProfileParser.MAX_BYTES + 1)
                    var count = 0
                    while (count < bytes.size) {
                        val read = stream.read(bytes, count, bytes.size - count)
                        if (read < 0) break
                        count += read
                    }
                    require(count <= HeadphoneProfileParser.MAX_BYTES) { "Profile is too large. Choose a text file up to 64 KiB." }
                    String(bytes, 0, count, Charsets.UTF_8)
                } ?: error("The selected file could not be opened.")
                loaded = HeadphoneImportDraft(text, source, output = importOutput)
            }, onSuccess = { draft = loaded })
        }
    }

    val selectProfile: (String?) -> Unit = { id -> targetEndpoint?.let { endpoint -> execute({
            val legacy = speakers.firstOrNull { "speaker:${it.id}" == id }
            val firstMigration = legacy != null && library.profiles.none { it.id == id }
            if (legacy != null) {
                HeadphonePreferences.import(context, CorrectionProfiles.fromSpeaker(legacy))
                CarDspPreferences.setActiveProfile(context, legacy)
            }
            HeadphonePreferences.select(context, endpoint, id)
            if (firstMigration) {
                val previous = CarDspPreferences.state.value
                HeadphonePreferences.setSoundMode(context, endpoint, previous.mode)
                if (previous.mode == SoundMode.CUSTOM) HeadphonePreferences.adjust(context, endpoint, previous.bassDb, previous.trebleDb)
                if (!endpoint.usb) HeadphonePreferences.setEnabled(context, endpoint, previous.enabled)
            }
        }) } }
    val openMicrophone = {
        calibration.launch(Intent(context, CarCalibrationActivity::class.java).putExtra("calibration_output", if (output == CalibrationOutput.SPEAKER) "speaker" else "headphone"))
    }
    val openAutoEq = {
        searching = true
        var loaded: List<AutoEqEntry>? = null
        execute(work = { loaded = autoEq.entries() }, onSuccess = { autoEqEntries = loaded })
    }
    if (quick) QuickTuningControls(
        status = status, choices = CorrectionProfiles.choices(speakers, library.profiles), busy = busy, error = error,
        selectedName = status.profile?.name ?: if (useLegacy) speakerChoice.profileName else null,
        onSelect = selectProfile,
        onEnabled = { enabled -> targetEndpoint?.let { endpoint -> execute({ HeadphonePreferences.setEnabled(context, endpoint, enabled) }) } },
        onSoundMode = { mode -> targetEndpoint?.let { endpoint -> execute({ HeadphonePreferences.setSoundMode(context, endpoint, mode) }) } },
        onFullTuning = onFullTuning,
        speakerControls = if (useLegacy) ({ LegacySpeakerSoundModes(compact = true, busy = busy, showStatus = false, onEditCustom = onFullTuning) }) else null,
        speakerStatus = if (useLegacy) ({ LegacySpeakerStatus(busy = busy) }) else null
    ) else {
    HeadphoneEqControls(
        library = library, status = status, busy = busy, error = error,
        output = output, onOutput = { output = it },
        choices = CorrectionProfiles.choices(speakers, library.profiles),
        selectedName = status.profile?.name ?: if (useLegacy) speakerChoice.profileName else null,
        onMicrophone = openMicrophone,
        speakerControls = if (useLegacy) ({ LegacySpeakerSoundModes(busy = busy, showStatus = false) }) else null,
        speakerStatus = if (useLegacy) ({ LegacySpeakerStatus(busy = busy) }) else null,
        onFile = { fileOutput = output; picker.launch(arrayOf("text/*", "application/octet-stream")) },
        onPaste = { error = null; draft = HeadphoneImportDraft(output = output) },
        onAutoEq = openAutoEq,
        onManual = { profile -> error = null; manualDraft = ManualHeadphoneDraft(profile, profile?.output ?: output) },
        onSelect = selectProfile,
        onEnabled = { enabled -> targetEndpoint?.let { endpoint -> execute({ HeadphonePreferences.setEnabled(context, endpoint, enabled) }) } },
        onAdjust = { bass, treble -> targetEndpoint?.let { endpoint -> execute({ HeadphonePreferences.adjust(context, endpoint, bass, treble) }) } },
        onSoundMode = { preset -> targetEndpoint?.let { endpoint -> execute({ HeadphonePreferences.setSoundMode(context, endpoint, preset) }) } },
        onDelete = { id -> execute({
            speakers.firstOrNull { "speaker:${it.id}" == id }?.let { CarDspPreferences.deleteProfile(context, it.id) }
            HeadphonePreferences.delete(context, id)
            speakers = CarDspPreferences.profiles(context).list()
        }) }
    )
    LegacySpeakerProfileManagement(speakers, busy,
        onRename = { profile, name -> execute({
            CarDspPreferences.renameProfile(context, profile.id, name)
            library.profiles.firstOrNull { it.id == "speaker:${profile.id}" }?.let { HeadphonePreferences.import(context, it.copy(name = name)) }
            speakers = CarDspPreferences.profiles(context).list()
        }) },
        onDelete = { profile -> execute({
            CarDspPreferences.deleteProfile(context, profile.id)
            HeadphonePreferences.delete(context, "speaker:${profile.id}")
            speakers = CarDspPreferences.profiles(context).list()
        }) })
    }
    if (searching) {
        AutoEqSearchDialog(
            entries = autoEqEntries, busy = busy, error = error,
            onPick = { entry ->
                var text = ""
                // A download that finishes after the search was closed opens nothing
                execute(work = { text = autoEq.profile(entry) }, onSuccess = {
                    if (searching) {
                        searching = false
                        draft = HeadphoneImportDraft(text, entry.credit, entry.name)
                    }
                })
            },
            onDismiss = { searching = false; error = null }
        )
    }
    draft?.let { current ->
        HeadphoneImportDialog(
            draft = current, busy = busy, error = error, output = current.output,
            onDismiss = { if (!busy) { draft = null; error = null } },
            onImport = { text, name, source ->
                execute(work = { HeadphonePreferences.import(context, HeadphoneProfileParser.parse(text, name, source).copy(output = current.output, method = CalibrationMethod.AUTO_EQ)) },
                    onSuccess = { draft = null })
            }
        )
    }
    manualDraft?.let { current ->
        ManualHeadphoneDialog(
            profile = current.profile, endpoint = status.endpoint?.takeIf { it.supports(current.output) },
            busy = busy, error = error, output = current.output,
            onDismiss = { if (!busy) { manualDraft = null; error = null } },
            onSave = { profile ->
                execute(work = { HeadphonePreferences.saveManual(context, profile, status.endpoint?.takeIf { it.supports(profile.output) }) },
                    onSuccess = { manualDraft = null })
            },
            onPlay = { signal, settings, endpoint, band, sample -> withContext(Dispatchers.IO) { testPlayer.play(signal, settings, endpoint, band, sample) } },
            onStop = { testPlayer.stop() }
        )
    }
}

private data class ManualHeadphoneDraft(val profile: HeadphoneProfile?, val output: CalibrationOutput)

@Composable
internal fun HeadphoneEqControls(
    library: HeadphoneLibrary,
    status: HeadphoneStatus,
    busy: Boolean = false,
    error: String? = null,
    onFile: () -> Unit = {},
    onPaste: () -> Unit = {},
    onAutoEq: () -> Unit = {},
    onSelect: (String?) -> Unit = {},
    onEnabled: (Boolean) -> Unit = {},
    onAdjust: (Double, Double) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onManual: (HeadphoneProfile?) -> Unit = {},
    onSoundMode: (SoundMode) -> Unit = {},
    output: CalibrationOutput = CalibrationOutput.HEADPHONE,
    onOutput: (CalibrationOutput) -> Unit = {},
    choices: List<CorrectionProfileChoice> = CorrectionProfiles.choices(emptyList(), library.profiles),
    selectedName: String? = status.profile?.name,
    onMicrophone: () -> Unit = {},
    speakerControls: (@Composable () -> Unit)? = null,
    speakerStatus: (@Composable () -> Unit)? = null
) {
    var deleting by remember { mutableStateOf<HeadphoneProfile?>(null) }
    var bass by remember(status.endpoint?.key, status.binding.bassDb) { mutableFloatStateOf(status.binding.bassDb.toFloat()) }
    var treble by remember(status.endpoint?.key, status.binding.trebleDb) { mutableFloatStateOf(status.binding.trebleDb.toFloat()) }
    val canEdit = status.endpoint != null && !busy
    val canEnable = canEdit && status.profile?.let { status.endpoint?.supports(it.output) } == true
    var details by remember(status.profile?.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TuningCurrentSound(status.endpoint?.name ?: "No audio output connected") {
            CorrectionProfileSelector(choices, status.profile?.id, selectedName, output, canEdit, onSelect,
                supportsOutput = { status.endpoint?.supports(it) == true })
            if (speakerStatus != null) speakerStatus() else if (speakerControls == null) {
                TuningStatusControl(tuningAudibility(status), status.binding.enabled, canEnable, onEnabled,
                    usb = status.endpoint?.usb == true)
            }
        }
        status.profile?.let { profile ->
            val settings = HeadphoneDsp.settingsFor(profile, status.binding)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TuningSoundModes(status.binding.soundMode, canEnable, onSoundMode)
                if (status.binding.soundMode == SoundMode.CUSTOM) {
                    HeadphoneToneSlider("Bass", bass, canEnable, { bass = it }, { onAdjust(bass.toDouble(), treble.toDouble()) })
                    HeadphoneToneSlider("Treble", treble, canEnable, { treble = it }, { onAdjust(bass.toDouble(), treble.toDouble()) })
                }
            }
            TuningResponseCurve(settings.bands + settings.monoBands, status.binding.soundMode, profile.manual != null, active = status.active, previewState = tuningAudibility(status).label)
        }
        if (status.profile == null && speakerControls == null) {
            TuningSoundModes(status.binding.soundMode, enabled = false, onSelect = onSoundMode)
            Surface(color = MusicMateDesignTokens.Surfaces.Surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your sound starts here", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    TuningHint("Choose a saved profile above, or create a correction below. Profiles stay saved for each output.")
                }
            }
        }
        speakerControls?.invoke()
        HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Calibrate your sound", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            TuningHint("Create a profile for your ${if (output == CalibrationOutput.SPEAKER) "speakers" else "headphones or earphones"}.")
            TuningOutputSelector(output, onOutput)
        }
        Column {
            TuningAction("Microphone", "Measure with a suitable microphone and setup", !busy, onMicrophone)
            TuningAction("Listening", "Tune with five musical listening steps", !busy) { onManual(null) }
            TuningAction("AutoEQ", if (output == CalibrationOutput.HEADPHONE) "Find measurements for your exact headphone model" else "Import your measured speaker correction", !busy,
                if (output == CalibrationOutput.HEADPHONE) onAutoEq else onFile)
        }
        TextButton(onClick = { details = !details }) {
            Text(if (details) "Hide profile management" else "Manage profiles", color = MusicMateDesignTokens.Text.Secondary)
        }
        if (details) {
            status.profile?.let { profile ->
                TuningLabel("PROFILE DETAILS")
                TuningHint(if (profile.manual != null) "Personal listening setup · independent left/right adjustments\nAutomatic headroom may reduce gain further." else "${profile.source}\nImported preamp: ${db(profile.preampDb)} dB · ${profile.bands.size} filters\nAutomatic headroom may reduce gain further.")
                if (profile.manual != null) TextButton(onClick = { onManual(profile) }, enabled = !busy) { Text("Edit listening setup") }
                TextButton(onClick = { deleting = profile }, enabled = !busy) { Text("Delete profile", color = MusicMateDesignTokens.Status.Destructive) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onFile, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Import text file", color = MusicMateDesignTokens.Text.Secondary) }
                TextButton(onClick = onPaste, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Paste profile", color = MusicMateDesignTokens.Text.Secondary) }
            }
            if (status.endpoint?.supports(output) != true && library.profiles.any { it.manual != null && it.output == output }) {
                TuningLabel("Saved listening profiles")
                library.profiles.filter { it.manual != null && it.output == output }.forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onManual(profile) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(profile.name, color = Color.White, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { deleting = profile }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Delete ${profile.name}" }) {
                            Text("Delete", color = MusicMateDesignTokens.Status.Destructive)
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 13.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MusicMateDesignTokens.Brand.Gold)
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This removes the profile and bypasses EQ on every output using it.") },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(profile.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HeadphoneToneSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onFinish: () -> Unit) {
    Column {
        Text("$label: ${db(value.toDouble())} dB", color = Color.White, fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinish,
            valueRange = -6f..6f, enabled = enabled, modifier = Modifier.semantics { contentDescription = "$label adjustment" })
    }
}

internal data class HeadphoneImportDraft(val text: String = "", val source: String = "Pasted AutoEq / Equalizer APO text",
                                       val name: String = "", val output: CalibrationOutput = CalibrationOutput.HEADPHONE)

@Composable
internal fun HeadphoneImportDialog(
    draft: HeadphoneImportDraft,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String, String, String) -> Unit,
    output: CalibrationOutput = CalibrationOutput.HEADPHONE
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var text by remember(draft) { mutableStateOf(draft.text) }
    var oversized by remember(draft) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import ${output.label.lowercase()} profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Importing adds a profile for ${output.label.lowercase()}. Choose it for your current output and enable EQ afterwards.", fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 200) name = it }, label = { Text(if (output == CalibrationOutput.HEADPHONE) "Headphone model / name" else "Speaker model / name") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = text, onValueChange = {
                    oversized = it.length > HeadphoneProfileParser.MAX_BYTES
                    if (!oversized) text = it
                }, label = { Text("Parametric EQ text") }, enabled = !busy, minLines = 4, maxLines = 7)
                Text("Source: ${draft.source}", fontSize = 12.sp)
                if (oversized) Text("Profile is too large. Paste text up to 64 KiB.", color = MusicMateDesignTokens.Status.Destructive, fontSize = 12.sp)
                error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text, name, draft.source) }, enabled = !busy && !oversized && name.isNotBlank() && text.isNotBlank()) { Text("Import profile") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

private fun db(value: Double): String = String.format(Locale.ROOT, "%+.1f", value)
