package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsEditorPage(
    track: Track?,
    state: TagsEditorState,
    albumArtistOptions: List<String> = emptyList(),
    artistOptions: List<String> = emptyList(),
    genreOptions: List<String> = emptyList(),
    styleOptions: List<String> = emptyList(),
    originOptions: List<String> = emptyList(),
    moodOptions: List<String> = emptyList(),
    publisherOptions: List<String> = emptyList()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        // Basic Info Card
        TechCard(title = "BASIC INFORMATION") {
            EditorTextField(
                value = state.title,
                onValueChange = { state.title = it; state.titleModified = true },
                label = "Title"
            )
            EditorDropdownField(
                value = state.artist,
                onValueChange = { state.artist = it; state.artistModified = true },
                label = "Artist",
                options = artistOptions
            )
            EditorTextField(
                value = state.album,
                onValueChange = { state.album = it; state.albumModified = true },
                label = "Album"
            )
            EditorDropdownField(
                value = state.albumArtist,
                onValueChange = { state.albumArtist = it; state.albumArtistModified = true },
                label = "Album Artist",
                options = albumArtistOptions
            )
            EditorDropdownField(
                value = state.genre,
                onValueChange = { state.genre = it; state.genreModified = true },
                label = "Genre",
                options = genreOptions
            )
            EditorDropdownField(
                value = state.style,
                onValueChange = { state.style = it; state.styleModified = true },
                label = "Style",
                options = styleOptions
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorDropdownField(
                    value = state.origin,
                    onValueChange = { state.origin = it; state.originModified = true },
                    label = "Origin",
                    options = originOptions,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                EditorDropdownField(
                    value = state.mood,
                    onValueChange = { state.mood = it; state.moodModified = true },
                    label = "Mood",
                    options = moodOptions,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Technical Metadata Card
        TechCard(title = "RELEASE INFO") {
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorTextField(
                    value = state.track,
                    onValueChange = { state.track = it; state.trackModified = true },
                    label = "Track #",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.width(12.dp))
                EditorTextField(
                    value = state.year,
                    onValueChange = { state.year = it; state.yearModified = true },
                    label = "Year",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
            }
            EditorDropdownField(
                value = state.publisher,
                onValueChange = { state.publisher = it; state.publisherModified = true },
                label = "Publisher",
                options = publisherOptions
            )
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val cleaned = if (value == " - " && input != " - ") {
                input.replace(" - ", "").trimStart()
            } else {
                input
            }
            onValueChange(cleaned)
        },
        label = { Text(label) },
        placeholder = { if (value == " - ") Text("Multiple values", color = Color.Gray) },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(value, options) {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "-") {
            options.take(60)
        } else {
            // Prioritize items that start with the query, followed by items containing the query
            val startsWith = mutableListOf<String>()
            val contains = mutableListOf<String>()
            for (opt in options) {
                if (opt.startsWith(trimmed, ignoreCase = true)) {
                    startsWith.add(opt)
                } else if (opt.contains(trimmed, ignoreCase = true)) {
                    contains.add(opt)
                }
            }
            val combined = (startsWith + contains).distinct()
            if (combined.isNotEmpty()) combined.take(50) else options.take(50)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                val cleaned = if (value == " - " && input != " - ") {
                    input.replace(" - ", "").trimStart()
                } else {
                    input
                }
                onValueChange(cleaned)
            },
            label = { Text(label) },
            placeholder = { if (value == " - ") Text("Multiple values", color = Color.Gray) },
            trailingIcon = {
                if (options.isNotEmpty()) {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth(),
            singleLine = true
        )
        if (options.isNotEmpty() && filteredOptions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                filteredOptions.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = selectionOption,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            ) 
                        },
                        onClick = {
                            val finalVal = if (selectionOption.trim() == "-") "" else selectionOption
                            onValueChange(finalVal)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
