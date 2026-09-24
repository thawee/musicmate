package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.utils.FilenameTagParser
import apincer.android.mmate.utils.ParsedTags
import apincer.music.core.model.Track
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsFromFilenameSheet(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onApply: (pattern: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var pattern by remember { mutableStateOf(FilenameTagParser.PRESET_PATTERNS.first()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x66FFFFFF)) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TAGS FROM FILENAME",
                        color = Color(0xFFFFD700),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (tracks.size > 1) "Extracting tags for ${tracks.size} tracks" else "Extracting tags from filename",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pattern Input Field
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Filename Pattern") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (pattern.isNotEmpty()) {
                        IconButton(onClick = { pattern = "" }) {
                            Icon(
                                painter = painterResource(id = R.drawable.round_close_24),
                                contentDescription = "Clear",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD700),
                    focusedLabelColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Token Inserter Chips Row
            Text(
                text = "INSERT TOKENS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilenameTagParser.AVAILABLE_TOKENS.forEach { token ->
                    TokenChip(token = token) {
                        pattern = if (pattern.isEmpty()) token else "$pattern$token"
                    }
                }
                TokenChip(token = " - ") {
                    pattern = "$pattern - "
                }
                TokenChip(token = ". ") {
                    pattern = "$pattern. "
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preset Patterns Row
            Text(
                text = "PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilenameTagParser.PRESET_PATTERNS.forEach { preset ->
                    val isSelected = pattern == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x33FFD700) else Color(0x1AFFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFFD700) else Color(0x1AFFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                pattern = preset
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) Color(0xFFFFD700) else Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview Card
            Text(
                text = "LIVE PARSER PREVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val previewTracks = remember(tracks) { tracks.take(3) }
            val parsedResults = remember(pattern, tracks) {
                tracks.map { track ->
                    FilenameTagParser.parse(track.path, pattern)
                }
            }
            val matchCount = parsedResults.count { it != null && !it.isEmpty }
            val hasMatches = matchCount > 0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x22000000)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasMatches) Color(0x3366BB6A) else Color(0x33EF5350)
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(previewTracks.indices.toList()) { index ->
                        val track = previewTracks[index]
                        val parsed = if (index < parsedResults.size) parsedResults[index] else null
                        PreviewTrackItem(track = track, parsed = parsed)
                        if (index < previewTracks.size - 1) {
                            HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (tracks.size > 3) {
                        item {
                            Text(
                                text = "+ ${tracks.size - 3} more track(s) will be parsed",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Text("Cancel", color = Color.White)
                }

                Button(
                    onClick = {
                        if (hasMatches) {
                            onApply(pattern)
                        }
                    },
                    enabled = hasMatches,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0x33FFD700),
                        disabledContentColor = Color(0x66FFFFFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (tracks.size > 1) "Apply ($matchCount/${tracks.size})" else "Apply",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenChip(token: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22FFD700))
            .border(0.75.dp, Color(0x44FFD700), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = token,
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PreviewTrackItem(track: Track, parsed: ParsedTags?) {
    val fileName = File(track.path).name
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = fileName,
            fontSize = 12.sp,
            color = Color.LightGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (parsed != null && !parsed.isEmpty) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!parsed.track.isNullOrBlank()) {
                    TagPreviewPill("Track", parsed.track)
                }
                if (!parsed.artist.isNullOrBlank()) {
                    TagPreviewPill("Artist", parsed.artist)
                }
                if (!parsed.title.isNullOrBlank()) {
                    TagPreviewPill("Title", parsed.title)
                }
                if (!parsed.album.isNullOrBlank()) {
                    TagPreviewPill("Album", parsed.album)
                }
                if (!parsed.year.isNullOrBlank()) {
                    TagPreviewPill("Year", parsed.year)
                }
            }
        } else {
            Text(
                text = "⚠️ No matching pattern extracted",
                color = Color(0xFFEF5350),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TagPreviewPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x3366BB6A))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            color = Color(0xFF81C784),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
