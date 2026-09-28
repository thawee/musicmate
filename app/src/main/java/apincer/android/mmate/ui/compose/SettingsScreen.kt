package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import apincer.android.mmate.R

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    replayGainMode: String = "track",
    onReplayGainModeChange: (String) -> Unit = {},
    replayGainPreamp: Float = 0.0f,
    onReplayGainPreampChange: (Float) -> Unit = {},
    replayGainPreventClipping: Boolean = true,
    onReplayGainPreventClippingChange: (Boolean) -> Unit = {},
    tapActionMode: String = "listen",
    onTapActionModeChange: (String) -> Unit = {},
    studioKeepScreenOn: Boolean = true,
    onStudioKeepScreenOnChange: (Boolean) -> Unit = {},
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val useTwoColumns = UiLayoutPolicy.useTwoColumnSettings(configuration.screenWidthDp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FlowRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = if (useTwoColumns) 2 else 1
            ) {
            // Put the setting that changes everyday behavior first.
            SettingsCard(
                title = "INTERACTION",
                modifier = if (useTwoColumns) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "When I tap a track",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (tapActionMode == "listen")
                        "Play it immediately. Long-press opens the tag editor."
                    else
                        "Open the tag editor. Tap the album art to play.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                AdaptiveChoiceGroup(
                    options = listOf(
                        "listen" to "Play tracks",
                        "curate" to "Edit tags"
                    ),
                    selected = { tapActionMode.equals(it, ignoreCase = true) },
                    onSelect = onTapActionModeChange
                )
            }

            // ── Section 2: Audiophile Playback (ReplayGain) ───────────────────
            SettingsCard(
                title = "AUDIOPHILE PLAYBACK (REPLAYGAIN)",
                modifier = if (useTwoColumns) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Loudness Leveling Mode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Normalizes playback loudness across tracks using EBU R128 / ReplayGain tags",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                AdaptiveChoiceGroup(
                    options = listOf(
                        "off" to "Off",
                        "track" to "Track Gain",
                        "album" to "Album Gain"
                    ),
                    selected = { replayGainMode.equals(it, ignoreCase = true) },
                    onSelect = onReplayGainModeChange
                )

                if (!replayGainMode.equals("off", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val preampDisplay = if (replayGainPreamp > 0) "+${replayGainPreamp.toInt()}" else "${replayGainPreamp.toInt()}"
                    Text(
                        text = "Pre-Amp Gain: $preampDisplay dB",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AdaptiveChoiceGroup(
                        options = listOf(
                            -3.0f to "-3 dB",
                            0.0f to "0 dB",
                            3.0f to "+3 dB"
                        ),
                        selected = { kotlin.math.abs(replayGainPreamp - it) < 0.1f },
                        onSelect = onReplayGainPreampChange
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSwitchRow(
                        title = "Prevent Clipping",
                        subtitle = "True-peak limiter automatically guards against inter-sample digital clipping",
                        checked = replayGainPreventClipping,
                        onCheckedChange = onReplayGainPreventClippingChange
                    )
                }
            }

            // ── Section 3: Library & User Interface ───────────────────────────
            SettingsCard(
                title = "USER INTERFACE",
                modifier = if (useTwoColumns) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
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
                HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                SettingsSwitchRow(
                    title = "Keep Screen Awake in Fullscreen",
                    subtitle = "Keeps the screen on while Fullscreen Desk Mode is open",
                    checked = studioKeepScreenOn,
                    onCheckedChange = onStudioKeepScreenOnChange
                )
            }

            SettingsCard(
                title = "ADVANCED STREAMING",
                modifier = if (useTwoColumns) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DLNA server engine",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Change this only when troubleshooting streaming compatibility.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                AdaptiveChoiceGroup(
                    options = listOf(
                        "httpcore" to "CoreHTTP",
                        "nio" to "SonicNIO",
                        "netty" to "Netty"
                    ),
                    selected = { serverEngine.equals(it, ignoreCase = true) },
                    onSelect = onServerEngineChange
                )
            }
        }
        }
    }
}

@Composable
private fun <T> AdaptiveChoiceGroup(
    options: List<Pair<T, String>>,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val stackChoices = UiLayoutPolicy.stackChoiceControls(
        windowWidthDp = configuration.screenWidthDp,
        fontScale = fontScale
    )

    if (stackChoices) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                SettingsChoice(
                    label = label,
                    selected = selected(value),
                    onClick = { onSelect(value) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                SettingsChoice(
                    label = label,
                    selected = selected(value),
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) Color(0x33FFB300) else Color(0xFF1F1F28))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFFFB300) else Color(0x1FFFFFFF),
                shape = shape
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFFB300) else Color.White,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF16161E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
        modifier = modifier
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
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
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
            onCheckedChange = null,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFFFFB300),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2C2C36)
            )
        )
    }
}

@Preview(name = "Settings phone", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Settings large text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Settings tablet", widthDp = 1000, heightDp = 700, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        serverEngine = "httpcore",
        onServerEngineChange = {},
        showStorageSpace = true,
        onShowStorageSpaceChange = {},
        prefixTrackNumber = false,
        onPrefixTrackNumberChange = {},
        listFollowsNowPlaying = true,
        onListFollowsNowPlayingChange = {},
        artistAwareSimilarSongs = true,
        onArtistAwareSimilarSongsChange = {},
        onBackClick = {}
    )
}
