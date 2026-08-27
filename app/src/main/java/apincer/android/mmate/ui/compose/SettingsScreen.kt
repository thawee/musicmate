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
import apincer.android.mmate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverEngine: String,
    onServerEngineChange: (String) -> Unit,
    showStorageSpace: Boolean,
    onShowStorageSpaceChange: (Boolean) -> Unit,
    prefixTrackNumber: Boolean,
    onPrefixTrackNumberChange: (Boolean) -> Unit,
    listFollowsNowPlaying: Boolean,
    onListFollowsNowPlayingChange: (Boolean) -> Unit,
    artistAwareSimilarSongs: Boolean,
    onArtistAwareSimilarSongsChange: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings Master",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section 1: Streaming & Server Engine ──────────────────────────
            SettingsCard(title = "DLNA MEDIA SERVER") {
                Text(
                    text = "Streaming Engine",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Select high-performance HTTP web server engine for streaming",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val engines = listOf(
                        "httpcore" to "CoreHTTP",
                        "nio" to "SonicNIO",
                        "netty" to "Netty"
                    )
                    engines.forEach { (key, label) ->
                        val isSelected = serverEngine.equals(key, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x33FFB300) else Color(0xFF1F1F28))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFFFB300) else Color(0x1FFFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onServerEngineChange(key) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFFFFB300) else Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Section 2: Library & User Interface ───────────────────────────
            SettingsCard(title = "USER INTERFACE") {
                SettingsSwitchRow(
                    title = "Display Storage Space",
                    subtitle = "Show storage usage metrics on drawer menu",
                    checked = showStorageSpace,
                    onCheckedChange = onShowStorageSpaceChange
                )
                HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                SettingsSwitchRow(
                    title = "Show Track Number",
                    subtitle = "Prefix track number to song title in library",
                    checked = prefixTrackNumber,
                    onCheckedChange = onPrefixTrackNumberChange
                )
                HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                SettingsSwitchRow(
                    title = "Auto Scroll to Now Playing",
                    subtitle = "Automatically scroll music list to currently playing track",
                    checked = listFollowsNowPlaying,
                    onCheckedChange = onListFollowsNowPlayingChange
                )
                HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                SettingsSwitchRow(
                    title = "Artist-Aware Similar Songs",
                    subtitle = "Matches title and artist (Turn off for title-only matching)",
                    checked = artistAwareSimilarSongs,
                    onCheckedChange = onArtistAwareSimilarSongsChange
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF16161E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFFFB300),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFFFFB300),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2C2C36)
            )
        )
    }
}
