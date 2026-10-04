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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ime
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
    genreLibraryOptions: List<String> = emptyList(),
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
            .imePaddingWithinWindow()
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
                options = genreOptions,
                libraryOptions = genreLibraryOptions,
                hint = "Main category, e.g. Jazz. Separate several with commas."
            )
            EditorDropdownField(
                value = state.style,
                onValueChange = { state.style = it; state.styleModified = true },
                label = "Style",
                options = styleOptions,
                hint = "How it is performed or produced, e.g. Live, Ballad."
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorDropdownField(
                    value = state.origin,
                    onValueChange = { state.origin = it; state.originModified = true },
                    label = "Origin",
                    options = originOptions,
                    hint = "Where the music is from",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                EditorDropdownField(
                    value = state.mood,
                    onValueChange = { state.mood = it; state.moodModified = true },
                    label = "Mood",
                    options = moodOptions,
                    hint = "How it feels",
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
                    label = "Year / Date",
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
/** One row of a tag dropdown: a section header, a value, or "Clear". */
internal sealed interface DropdownEntry {
    data class Header(val title: String) : DropdownEntry
    data class Value(val text: String) : DropdownEntry
    data object Clear : DropdownEntry
}

/**
 * Builds the dropdown: presets first, then values already in the library that are not presets.
 * Until the user types, everything is listed (the current value is ticked, not used as a filter);
 * after typing, only matches are listed - words starting with the text first.
 */
internal fun dropdownEntries(
    presets: List<String>,
    library: List<String>,
    query: String,
    typed: Boolean,
    hasValue: Boolean
): List<DropdownEntry> {
    val q = query.trim()
    fun filter(values: List<String>): List<String> {
        if (!typed || q.isEmpty()) return values
        val words = values.filter { v -> v.split(' ', '-', '&', ',').any { it.startsWith(q, ignoreCase = true) } }
        val contains = values.filter { it.contains(q, ignoreCase = true) && it !in words }
        return words + contains
    }
    val presetKeys = presets.map { it.trim().lowercase() }.toSet()
    val extra = library.filter { it.trim().isNotEmpty() && it.trim().lowercase() !in presetKeys }
    val shownPresets = filter(presets)
    val shownLibrary = filter(extra)
    val entries = mutableListOf<DropdownEntry>()
    if (hasValue && (!typed || q.isEmpty())) entries += DropdownEntry.Clear
    shownPresets.forEach { entries += DropdownEntry.Value(it) }
    if (shownLibrary.isNotEmpty()) {
        if (shownPresets.isNotEmpty()) entries += DropdownEntry.Header("In your library")
        shownLibrary.forEach { entries += DropdownEntry.Value(it) }
    }
    return entries
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    libraryOptions: List<String> = emptyList(),
    hint: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // Filter only after the user types; opening the menu shows every choice
    var typed by remember { mutableStateOf(false) }
    val isMulti = TagsEditorState.isMultiValues(value)
    val displayValue = if (isMulti) "" else value

    val entries = remember(displayValue, options, libraryOptions, typed) {
        dropdownEntries(options, libraryOptions, displayValue, typed, hasValue = displayValue.isNotBlank())
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (it) typed = false
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {
                typed = true
                expanded = true
                onValueChange(it)
            },
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
            supportingText = when {
                isMulti -> { { Text("Leave blank to preserve individual track values", color = Color(0xFF9E9E9E), fontSize = 10.sp) } }
                hint != null -> { { Text(hint, color = Color(0xFF9E9E9E), fontSize = 10.sp) } }
                else -> null
            },
            trailingIcon = {
                if (options.isNotEmpty() || libraryOptions.isNotEmpty()) {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth(),
            singleLine = true
        )
        if (entries.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is DropdownEntry.Header -> Text(
                            text = entry.title.uppercase(),
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                        )
                        DropdownEntry.Clear -> DropdownMenuItem(
                            text = { Text("Clear", color = Color(0xFFBDBDBD)) },
                            onClick = {
                                onValueChange("")
                                expanded = false
                            }
                        )
                        is DropdownEntry.Value -> {
                            val selected = entry.text.equals(displayValue.trim(), ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = entry.text,
                                        maxLines = 1,
                                        color = if (selected) Color(0xFFFFB300) else Color.Unspecified,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = if (selected) {
                                    { Text("✓", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold) }
                                } else null,
                                onClick = {
                                    onValueChange(entry.text)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pads only the part of the keyboard that overlaps this layout. The editor page ends above the
 * bottom action dock, so plain imePadding() reserved the dock's height twice and left a blank band.
 */
@Composable
private fun Modifier.imePaddingWithinWindow(): Modifier {
    val density = LocalDensity.current
    val rootView = LocalView.current.rootView
    var gapBelow by remember { mutableIntStateOf(0) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val overlap = with(density) { (imeBottom - gapBelow).coerceAtLeast(0).toDp() }
    return this
        .onGloballyPositioned { coords ->
            val bottomInWindow = coords.positionInWindow().y + coords.size.height
            gapBelow = (rootView.height - bottomInWindow).roundToInt().coerceAtLeast(0)
        }
        .padding(bottom = overlap)
}
