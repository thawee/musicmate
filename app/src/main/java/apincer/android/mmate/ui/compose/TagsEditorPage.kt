package apincer.android.mmate.ui.compose

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsEditorPage(
    track: Track?,
    tracks: List<Track> = emptyList(),
    state: TagsEditorState,
    onScanLibrary: () -> Unit = {},
    onApplyFilenamePattern: (String) -> Unit = {},
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

    if (state.showFilenameParserSheet && track != null) {
        val tracksList = remember(track, tracks) {
            if (tracks.isNotEmpty()) tracks else listOf(track)
        }
        TagsFromFilenameSheet(
            tracks = tracksList,
            onDismiss = { state.showFilenameParserSheet = false },
            onApply = { pattern ->
                state.showFilenameParserSheet = false
                onApplyFilenamePattern(pattern)
            }
        )
    }

    if (track == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .graphicsLayer { clip = true }
                    .background(Color(0x1AFFB300), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x33FFB300), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_library_music_24),
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Track Selected",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select a track from the library to edit its tags.",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onScanLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Scan Library",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

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

        Spacer(modifier = Modifier.height(16.dp))
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
    val isMulti = TagsEditorState.isMultiValues(value)
    val displayValue = if (isMulti) "" else value

    OutlinedTextField(
        value = displayValue,
        onValueChange = onValueChange,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (isMulti) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Mixed",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        placeholder = {
            if (isMulti) {
                Text("Multiple values (type to overwrite)", color = Color.Gray, fontSize = 12.sp)
            }
        },
        supportingText = {
            if (isMulti) {
                Text("Leave blank to preserve individual track values", color = Color(0xFF9E9E9E), fontSize = 10.sp)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
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
    val isMulti = TagsEditorState.isMultiValues(value)
    val displayValue = if (isMulti) "" else value

    val filteredOptions = remember(displayValue, options) {
        val trimmed = displayValue.trim()
        if (trimmed.isEmpty() || trimmed == "-") {
            options.take(60)
        } else {
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
            .padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = onValueChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (isMulti) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Mixed",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            placeholder = {
                if (isMulti) {
                    Text("Multiple values (type to overwrite)", color = Color.Gray, fontSize = 12.sp)
                }
            },
            supportingText = {
                if (isMulti) {
                    Text("Leave blank to preserve individual track values", color = Color(0xFF9E9E9E), fontSize = 10.sp)
                }
            },
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
