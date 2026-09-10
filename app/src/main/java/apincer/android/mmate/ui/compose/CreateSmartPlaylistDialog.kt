package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import apincer.android.mmate.R
import apincer.music.core.model.PlaylistEntry
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils

@Composable
fun CreateSmartPlaylistDialog(
    availableTracks: List<Track>,
    onDismiss: () -> Unit,
    onSave: (PlaylistEntry) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minDrScore by remember { mutableStateOf(0f) }
    var hiresOnly by remember { mutableStateOf(false) }
    var losslessOnly by remember { mutableStateOf(false) }
    var dsdOnly by remember { mutableStateOf(false) }
    var minBitDepth by remember { mutableStateOf(0) }
    var description by remember { mutableStateOf("") }

    // Live matching calculation
    val tempEntry = remember(title, minDrScore, hiresOnly, losslessOnly, dsdOnly, minBitDepth, description) {
        PlaylistEntry().apply {
            name = title.trim()
            type = PlaylistEntry.TYPE_SMART
            this.minDrScore = minDrScore.toDouble()
            isHiresOnly = hiresOnly
            isLosslessOnly = losslessOnly
            isDsdOnly = dsdOnly
            this.minBitDepth = minBitDepth
            this.description = description.trim()
        }
    }

    val matchingTracks = remember(tempEntry, availableTracks) {
        availableTracks.filter { tempEntry.matchesSmartCriteria(it) }
    }
    val matchingCount = matchingTracks.size
    val matchingSize = remember(matchingTracks) {
        matchingTracks.sumOf { it.fileSize }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141418),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFB300)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_playlist_add_24),
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Smart Playlist",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.round_close_24),
                            contentDescription = "Close",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Nighttime Audiophile Mix") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFFB300),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = Color(0xFFFFB300),
                        unfocusedLabelColor = Color(0xFF888888)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Minimum DR Score Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minimum Dynamic Range",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (minDrScore == 0f) "Any DR" else "DR ${minDrScore.toInt()}+",
                            color = if (minDrScore >= 12f) Color(0xFF00E676) else if (minDrScore >= 8f) Color(0xFFFFB300) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = minDrScore,
                        onValueChange = { minDrScore = it },
                        valueRange = 0f..16f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFB300),
                            activeTrackColor = Color(0xFFFFB300),
                            inactiveTrackColor = Color(0xFF2C2C36)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Format & Quality Chips
                Text(
                    text = "Audio Authenticity & Format",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterToggleChip(
                        label = "Hi-Res (24-bit+)",
                        selected = hiresOnly,
                        onToggle = { hiresOnly = !hiresOnly },
                        modifier = Modifier.weight(1f)
                    )
                    FilterToggleChip(
                        label = "Lossless Only",
                        selected = losslessOnly,
                        onToggle = { losslessOnly = !losslessOnly },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterToggleChip(
                        label = "DSD Only",
                        selected = dsdOnly,
                        onToggle = { dsdOnly = !dsdOnly },
                        modifier = Modifier.weight(1f)
                    )
                    FilterToggleChip(
                        label = "24-bit Studio",
                        selected = minBitDepth == 24,
                        onToggle = { minBitDepth = if (minBitDepth == 24) 0 else 24 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E28))
                        .border(1.dp, Color(0x33FFB300), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ Live Match:",
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$matchingCount tracks • ${StringUtils.formatStorageSize(matchingSize)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCCCCCC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(tempEntry)
                            }
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0x33FFB300),
                            disabledContentColor = Color(0x66000000)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Playlist", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggleChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x33FFB300) else Color(0xFF1F1F28))
            .border(
                1.dp,
                if (selected) Color(0xFFFFB300) else Color(0x1FFFFFFF),
                RoundedCornerShape(8.dp)
            )
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFFB300) else Color(0xFFCCCCCC),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
