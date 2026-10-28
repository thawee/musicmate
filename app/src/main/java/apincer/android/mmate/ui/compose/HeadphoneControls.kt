package apincer.android.mmate.ui.compose

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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.window.Dialog
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun HeadphoneDspChip(modifier: Modifier = Modifier) {
    val status by HeadphoneDsp.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val accent = if (status.active) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Secondary
    Text(
        if (status.active) "HEADPHONE EQ" else "HEADPHONE FLAT",
        color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(MusicMateDesignTokens.Surfaces.GlassPill)
            .clickable { open = true }
            .semantics {
                role = Role.Button
                contentDescription = "Headphone EQ"
                stateDescription = if (status.active) "Active, ${status.profile?.name}" else "Flat, EQ bypassed"
            }.padding(horizontal = 8.dp, vertical = 6.dp)
    )
    if (open) Dialog(onDismissRequest = { open = false }) {
        Surface(shape = RoundedCornerShape(20.dp), color = MusicMateDesignTokens.Surfaces.Canvas) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("HEADPHONE EQ", color = MusicMateDesignTokens.Brand.Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                HeadphoneAudioSettings()
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

/** Storage and document reads stay off the main thread; the controls only render immutable state. */
@Composable
fun HeadphoneAudioSettings() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val library by HeadphonePreferences.state.collectAsState()
    val status by HeadphoneDsp.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<HeadphoneImportDraft?>(null) }
    var manualDraft by remember { mutableStateOf<ManualHeadphoneDraft?>(null) }
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
                error = failure.message ?: "Headphone settings could not be saved."
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
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Headphone settings could not be loaded."
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
                    error = failure.message ?: "The headphone output could not be refreshed."
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
                loaded = HeadphoneImportDraft(text, source)
            }, onSuccess = { draft = loaded })
        }
    }

    HeadphoneEqControls(
        library = library, status = status, busy = busy, error = error,
        onFile = { picker.launch(arrayOf("text/*", "application/octet-stream")) },
        onPaste = { error = null; draft = HeadphoneImportDraft() },
        onAutoEq = {
            searching = true
            var loaded: List<AutoEqEntry>? = null
            execute(work = { loaded = autoEq.entries() }, onSuccess = { autoEqEntries = loaded })
        },
        onManual = { profile -> error = null; manualDraft = ManualHeadphoneDraft(profile) },
        onSelect = { id -> status.endpoint?.let { endpoint -> execute({ HeadphonePreferences.select(context, endpoint, id) }) } },
        onEnabled = { enabled -> status.endpoint?.let { endpoint -> execute({ HeadphonePreferences.setEnabled(context, endpoint, enabled) }) } },
        onAdjust = { bass, treble -> status.endpoint?.let { endpoint -> execute({ HeadphonePreferences.adjust(context, endpoint, bass, treble) }) } },
        onDelete = { id -> execute({ HeadphonePreferences.delete(context, id) }) }
    )
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
            draft = current, busy = busy, error = error,
            onDismiss = { if (!busy) { draft = null; error = null } },
            onImport = { text, name, source ->
                execute(work = { HeadphonePreferences.import(context, HeadphoneProfileParser.parse(text, name, source)) },
                    onSuccess = { draft = null })
            }
        )
    }
    manualDraft?.let { current ->
        ManualHeadphoneDialog(
            profile = current.profile, endpoint = status.endpoint, busy = busy, error = error,
            onDismiss = { if (!busy) { manualDraft = null; error = null } },
            onSave = { profile ->
                execute(work = { HeadphonePreferences.saveManual(context, profile, status.endpoint) },
                    onSuccess = { manualDraft = null })
            },
            onPlay = { signal, settings, endpoint -> withContext(Dispatchers.IO) { testPlayer.play(signal, settings, endpoint) } },
            onStop = { testPlayer.stop() }
        )
    }
}

private data class ManualHeadphoneDraft(val profile: HeadphoneProfile?)

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
    onManual: (HeadphoneProfile?) -> Unit = {}
) {
    var choosing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HeadphoneProfile?>(null) }
    var bass by remember(status.endpoint?.key, status.binding.bassDb) { mutableFloatStateOf(status.binding.bassDb.toFloat()) }
    var treble by remember(status.endpoint?.key, status.binding.trebleDb) { mutableFloatStateOf(status.binding.trebleDb.toFloat()) }
    val canEdit = status.endpoint != null && !busy
    val canEnable = canEdit && status.profile != null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Create a personal listening setup for your DIY pair, or import a correction for an exact headphone model from AutoEq or Equalizer APO (up to 64 KiB).", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
        Text(status.endpoint?.name ?: "Connect wired, USB DAC or Bluetooth headphones to choose a profile for that output.", color = Color.White, fontSize = 14.sp)
        Text(if (status.active) "EQ active" else "EQ bypassed", color = if (status.active) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { choosing = true }, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
                Text(status.profile?.name ?: "Flat / bypass")
            }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                DropdownMenuItem(text = { Text("Flat / bypass") }, onClick = { choosing = false; onSelect(null) })
                library.profiles.forEach { profile ->
                    DropdownMenuItem(text = { Text(profile.name) }, onClick = { choosing = false; onSelect(profile.id) })
                }
            }
        }
        if (status.endpoint != null) {
            Row(
                Modifier.fillMaxWidth().toggleable(value = status.binding.enabled, enabled = canEnable, role = Role.Switch, onValueChange = onEnabled),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (status.endpoint.usb) "Enable USB EQ (changes samples)" else "Enable headphone EQ", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = status.binding.enabled, onCheckedChange = null, enabled = canEnable)
            }
            Text(if (status.endpoint.usb) "USB EQ processes audio samples. Turn it off for headphone EQ bypass. Your choice is saved for this DAC." else "The selected profile and EQ switch are saved separately for each output.", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
        }
        status.profile?.let { profile ->
            if (profile.manual != null) {
                Text("Personal listening setup · saved for this pair\nAutomatic headroom may reduce gain further.", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
                OutlinedButton(onClick = { onManual(profile) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Edit listening setup") }
            } else {
                Text("Source: ${profile.source}\nImported preamp: ${db(profile.preampDb)} dB · ${profile.bands.size} filters\nAutomatic headroom may reduce gain further.", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
                HeadphoneToneSlider("Bass", bass, canEnable, { bass = it }, { onAdjust(bass.toDouble(), treble.toDouble()) })
                HeadphoneToneSlider("Treble", treble, canEnable, { treble = it }, { onAdjust(bass.toDouble(), treble.toDouble()) })
            }
            TextButton(onClick = { deleting = profile }, enabled = !busy) { Text("Delete profile", color = MusicMateDesignTokens.Status.Destructive) }
        }
        OutlinedButton(onClick = { onManual(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create DIY profile") }
        if (status.endpoint == null && library.profiles.any { it.manual != null }) {
            Text("Saved DIY profiles", color = Color.White, fontSize = 13.sp)
            library.profiles.filter { it.manual != null }.forEach { profile ->
                // Deleting here lets a pair that is no longer owned be removed without connecting headphones
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onManual(profile) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(profile.name, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { deleting = profile }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Delete ${profile.name}" }) {
                        Text("Delete", color = MusicMateDesignTokens.Status.Destructive)
                    }
                }
            }
        }
        OutlinedButton(onClick = onAutoEq, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Search AutoEq") }
        OutlinedButton(onClick = onFile, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import text file") }
        OutlinedButton(onClick = onPaste, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Paste profile") }
        Text("DIY profiles are listening adjustments. Speaker microphone measurements do not measure headphones.", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
        error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 12.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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

internal data class HeadphoneImportDraft(val text: String = "", val source: String = "Pasted AutoEq / Equalizer APO text", val name: String = "")

@Composable
internal fun HeadphoneImportDialog(
    draft: HeadphoneImportDraft,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String, String, String) -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var text by remember(draft) { mutableStateOf(draft.text) }
    var oversized by remember(draft) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import headphone profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Use the exact headphone model name. Importing adds a profile; choose it for your current output and enable EQ afterwards.", fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 200) name = it }, label = { Text("Headphone model / name") }, singleLine = true, enabled = !busy)
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
