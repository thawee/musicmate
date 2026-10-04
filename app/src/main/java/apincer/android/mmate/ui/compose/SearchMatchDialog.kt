package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import apincer.android.mmate.R
import apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Obsidian Glass Search Query Content
 */
@Composable
fun SearchQueryContent(
    initialTitle: String,
    initialArtist: String,
    onDismissRequest: () -> Unit,
    onSearch: (title: String, artist: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF2141416),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.round_auto_awesome_24),
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Search & Match Tags",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.round_close_24),
                        contentDescription = "Close",
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Track Title", color = Color(0xAAFFFFFF)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = Color(0xFFFFD700)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Artist Input
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist", color = Color(0xAAFFFFFF)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = Color(0xFFFFD700)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Cancel", color = Color(0xAAFFFFFF))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onDismissRequest()
                        onSearch(title.trim(), artist.trim())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x33FFD700),
                        contentColor = Color(0xFFFFD700)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFD700)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.round_auto_awesome_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search Online", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Obsidian Glass Search Query Dialog
 */
@Composable
fun SearchQueryDialog(
    initialTitle: String,
    initialArtist: String,
    onDismissRequest: () -> Unit,
    onSearch: (title: String, artist: String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SearchQueryContent(
            initialTitle = initialTitle,
            initialArtist = initialArtist,
            onDismissRequest = onDismissRequest,
            onSearch = onSearch
        )
    }
}

/**
 * Obsidian Glass Search Results Content
 */
@Composable
fun SearchResultsContent(
    results: List<MusicBrainzSearchResult>,
    onDismissRequest: () -> Unit,
    onSelect: (MusicBrainzSearchResult) -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF2141416),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .fillMaxHeight(0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.round_auto_awesome_24),
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select Best Match",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${results.size} recordings found on MusicBrainz",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.5.sp
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.round_close_24),
                        contentDescription = "Close",
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Results List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x1AFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x26FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                onSelect(result)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover Art Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222226)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!result.releaseId.isNullOrEmpty()) {
                                    val coverUrl = "https://coverartarchive.org/release/${result.releaseId}/front-250"
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_context_album_24dp),
                                        contentDescription = null,
                                        tint = Color(0x55FFFFFF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Metadata Texts
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.title ?: "Unknown Title",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = result.artist ?: "Unknown Artist",
                                    color = Color(0xFFFFD700),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!result.album.isNullOrEmpty()) {
                                    val albumText = buildString {
                                        append(result.album)
                                        if (!result.year.isNullOrEmpty()) {
                                            append(" (${result.year})")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = albumText,
                                        color = Color(0x99FFFFFF),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Cancel Button
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1FFFFFFF),
                    contentColor = Color(0xCCFFFFFF)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", fontSize = 13.sp)
            }
        }
    }
}

/**
 * Obsidian Glass Search Results Dialog
 */
@Composable
fun SearchResultsDialog(
    results: List<MusicBrainzSearchResult>,
    onDismissRequest: () -> Unit,
    onSelect: (MusicBrainzSearchResult) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SearchResultsContent(
            results = results,
            onDismissRequest = onDismissRequest,
            onSelect = onSelect
        )
    }
}
