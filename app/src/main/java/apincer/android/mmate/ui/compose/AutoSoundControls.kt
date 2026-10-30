package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.dsp.*
import apincer.music.core.repository.TagRepository

/** Auto chooses a listening mode; it never changes the output's correction or enable switch. */
@Composable
internal fun AutoTuningSoundModes(mode: SoundMode, enabled: Boolean = true, onSelect: (SoundMode) -> Unit) {
    val context = LocalContext.current
    val state by AutoSoundPreferences.state.collectAsState()
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(context) { AutoSoundPreferences.init(context) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = MusicMateDesignTokens.Brand.Gold)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sound mode", color = MusicMateDesignTokens.Text.Primary, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundSelectionChip(selected = state.config.enabled, enabled = enabled,
                    onClick = { AutoSoundPreferences.setEnabled(context, true) }, label = { Text("Auto") })
                SoundSelectionChip(selected = !state.config.enabled, enabled = enabled,
                    onClick = { AutoSoundPreferences.setEnabled(context, false) }, label = { Text("Manual") })
            }
            if (state.config.enabled) {
                Surface(color = MusicMateDesignTokens.Surfaces.SurfaceElevated, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.resolvedMode.label, color = MusicMateDesignTokens.Text.Primary, fontSize = 20.sp)
                        Text(soundModeDescription(state.resolvedMode), color = MusicMateDesignTokens.Text.Primary)
                        if (state.held) TuningHint("Kept for this session")
                        TuningHint(state.reason)
                        TextButton(enabled = enabled, onClick = {
                            if (state.held) AutoSoundPreferences.resume() else AutoSoundPreferences.hold()
                        }) { Text(if (state.held) "Resume Auto" else "Keep for this session") }
                    }
                }
                TextButton(onClick = { settings = true }, enabled = enabled) { Text("Auto settings") }
            } else {
                TuningSoundModes(mode, enabled, showHeading = false, onSelect = onSelect)
            }
        }
        if (settings) AutoSoundSettings { settings = false }
    }
}

@Composable
private fun AutoSoundSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by AutoSoundPreferences.state.collectAsState()
    var editing by remember { mutableStateOf<AutoSoundRule?>(null) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(config: AutoSoundConfig) {
        try { AutoSoundPreferences.saveSettings(context, config); error = null }
        catch (e: IllegalArgumentException) { error = e.message }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Auto settings") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Matches use track genre and style tags. The first matching rule wins. Your correction stays unchanged.")
                Text("Fallback", style = MaterialTheme.typography.titleSmall)
                SoundModeMenu(state.config.fallback) { save(state.config.copy(fallback = it)) }
                TuningHint("Used when tags are missing or no rule matches.")
                Text("Your matches", style = MaterialTheme.typography.titleSmall)
                if (state.config.rules.isEmpty()) Text("No matches yet. Auto uses ${state.config.fallback.label}.")
                state.config.rules.forEachIndexed { index, rule ->
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { editing = rule; editingIndex = index }) {
                            Text("${rule.value} → ${rule.mode.label}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (rule.field == AutoSoundField.GENRE) "Genre" else "Style", Modifier.weight(1f))
                            if (index > 0) TextButton(onClick = {
                                val rules = state.config.rules.toMutableList()
                                rules[index] = rules[index - 1]; rules[index - 1] = rule
                                save(state.config.copy(rules = rules))
                            }) { Text("Move up") }
                            TextButton(onClick = { save(state.config.copy(rules = state.config.rules.filterIndexed { i, _ -> i != index })) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
                TextButton(onClick = { adding = true; editingIndex = -1 }) { Text("Add match") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    if (adding || editing != null) AutoSoundRuleEditor(editing, onDismiss = { adding = false; editing = null }) { rule ->
        val rules = state.config.rules.toMutableList()
        if (editingIndex < 0) rules.add(rule) else rules[editingIndex] = rule
        save(state.config.copy(rules = rules))
        adding = false; editing = null
    }
}

@Composable
private fun AutoSoundRuleEditor(initial: AutoSoundRule?, onDismiss: () -> Unit, onSave: (AutoSoundRule) -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(initial?.value.orEmpty()) }
    var field by remember { mutableStateOf(initial?.field ?: AutoSoundField.GENRE) }
    var mode by remember { mutableStateOf(initial?.mode ?: SoundMode.REFERENCE) }
    val options = remember(context, field) {
        val defaults = if (field == AutoSoundField.GENRE) TagRepository.getDefaultGenreList(context)
            else TagRepository.getDefaultStyleList(context)
        defaults.map { it.trim() }.filter { it.isNotEmpty() && it != "-" }.distinct()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add match" else "Edit match") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundSelectionChip(field == AutoSoundField.GENRE, { field = AutoSoundField.GENRE }, label = { Text("Genre") })
                    SoundSelectionChip(field == AutoSoundField.STYLE, { field = AutoSoundField.STYLE }, label = { Text("Style") })
                }
                key(field) {
                    EditorDropdownField(value = value, onValueChange = { value = it.take(80) },
                        label = "Tag value", options = options,
                        hint = "Type to filter ${field.label.lowercase()} choices, or enter your own tag.")
                }
                SoundModeMenu(mode) { mode = it }
                if (mode == SoundMode.CUSTOM) TuningHint("Uses the Custom bass and treble saved for the current output.")
            }
        }, confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(AutoSoundRule(field, value.trim(), mode)) }) { Text("Save match") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SoundSelectionChip(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, label: @Composable () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, enabled = enabled, label = label,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MusicMateDesignTokens.Text.Primary,
            selectedContainerColor = MusicMateDesignTokens.Brand.GoldContainer,
            selectedLabelColor = MusicMateDesignTokens.Brand.Gold),
        border = FilterChipDefaults.filterChipBorder(enabled, selected,
            borderColor = MusicMateDesignTokens.Surfaces.BorderSubtle,
            selectedBorderColor = MusicMateDesignTokens.Brand.GoldOutline))
}

@Composable
private fun SoundModeMenu(mode: SoundMode, onSelect: (SoundMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("${mode.label} · Change") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SoundMode.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
