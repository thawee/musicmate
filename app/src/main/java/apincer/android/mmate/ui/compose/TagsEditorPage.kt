package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import apincer.music.core.model.Track
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.target.ImageViewTarget
import apincer.android.mmate.coil3.CoverartFetcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsEditorPage(
    track: Track?,
    state: TagsEditorState,
    albumArtistOptions: List<String>
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        // Preview Card
        TechCard(title = "PREVIEW") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                (64 * resources.displayMetrics.density).toInt(),
                                (64 * resources.displayMetrics.density).toInt()
                            )
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    update = { imageView ->
                        track?.let {
                            val request = CoverartFetcher.builder(context, it)
                                .data(it)
                                .target(ImageViewTarget(imageView))
                                .build()
                            SingletonImageLoader.get(context).enqueue(request)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = track?.title ?: "Unknown Title", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(text = track?.simpleName ?: "", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Basic Info Card
        TechCard(title = "BASIC INFORMATION") {
            EditorTextField(
                value = state.title,
                onValueChange = { state.title = it; state.titleModified = true },
                label = "Title"
            )
            EditorTextField(
                value = state.artist,
                onValueChange = { state.artist = it; state.artistModified = true },
                label = "Artist"
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
            EditorTextField(
                value = state.genre,
                onValueChange = { state.genre = it; state.genreModified = true },
                label = "Genre"
            )
            EditorTextField(
                value = state.style,
                onValueChange = { state.style = it; state.styleModified = true },
                label = "Style"
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorTextField(
                    value = state.origin,
                    onValueChange = { state.origin = it; state.originModified = true },
                    label = "Origin",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                EditorTextField(
                    value = state.mood,
                    onValueChange = { state.mood = it; state.moodModified = true },
                    label = "Mood",
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
            EditorTextField(
                value = state.publisher,
                onValueChange = { state.publisher = it; state.publisherModified = true },
                label = "Publisher"
            )
        }
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
        onValueChange = onValueChange,
        label = { Text(label) },
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

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth(),
            singleLine = true
        )
        if (options.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onValueChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
