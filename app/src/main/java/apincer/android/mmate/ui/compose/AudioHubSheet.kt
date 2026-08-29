package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.model.Track
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioHubSheet(
    nowPlayingState: NowPlayingState,
    queueState: QueueState,
    mediaServerState: MediaServerState,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit,
    onSelectTargetPlayer: () -> Unit,
    // Callbacks for NowPlayingPage
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onSleepTimerSelected: (Long, Boolean) -> Unit = { _, _ -> },
    onTrackClicked: () -> Unit,
    // Callbacks for QueuePage
    onQueueTrackClicked: (Track) -> Unit,
    onQueueTrackRemoved: (Track, Int) -> Unit,
    onQueueTrackMoved: (Int, Int) -> Unit = { _, _ -> },
    onQueueClear: () -> Unit,
    onQueueJumpToPlaying: () -> Unit,
    // Callbacks for MediaServerPage
    onEngineChanged: (String) -> Unit,
    onStartServerClicked: () -> Unit,
    onStopServerClicked: () -> Unit,
    onCopyUrlClicked: () -> Unit,
    onOpenUrlClicked: () -> Unit,
    onQrCodeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // DESIGN.md §8C & ADR-004: Sheet opens fully expanded at fixed 65% of screen height
    val sheetHeight = (configuration.screenHeightDp.dp * 0.65f).coerceIn(420.dp, 680.dp)

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2)) { 3 }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Save sticky tab selection
    LaunchedEffect(pagerState.currentPage) {
        MainScaffoldState.get().audioHubInitialTab.intValue = pagerState.currentPage
    }

    // Dynamic Tab Titles matching DESIGN.md §8C & §6C
    val queueCount = queueState.tracks.size
    val queueTitle = if (queueCount > 0) "Queue ($queueCount)" else "Queue"
    val isServerActive = mediaServerState.isServerRunning
    val serverTitle = if (isServerActive) "Server 🟢" else "Server"
    val tabs = listOf("Playback", queueTitle, serverTitle)

    // Dynamic Cast Icon Tint (§5B: Gold #FFC107 when DLNA active)
    val target = nowPlayingState.targetTitle.value
    val isDlnaCastActive = target.contains("DLNA", ignoreCase = true) || target.contains("Renderer", ignoreCase = true) || target.contains("Streamer", ignoreCase = true)
    val castIconTint = if (isDlnaCastActive) Color(0xFFFFC107) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), // DESIGN.md §8C: 24dp top corner radius
        containerColor = Color(0xF0121212),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x4DFFFFFF))
            )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            // Header Row: Music Center title, Cast picker & Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Music Center",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSelectTargetPlayer, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_music_cast_24),
                            contentDescription = "Select Player",
                            tint = castIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_round_keyboard_arrow_down_24),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Segmented Pill Switcher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0x1AFFFFFF))
                    .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(19.dp))
                    .padding(3.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0x33FFD700) else Color.Transparent)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color(0xFFFFD700) else Color(0xFFAAAAAA),
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Horizontal Pager with the 3 Pages (Playback | Queue | Server)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val pageScale = (1f - 0.05f * kotlin.math.abs(pageOffset)).coerceIn(0.94f, 1f)
                val pageAlpha = (1f - 0.25f * kotlin.math.abs(pageOffset)).coerceIn(0.75f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pageScale
                            scaleY = pageScale
                            alpha = pageAlpha
                        }
                ) {
                    when (page) {
                        0 -> NowPlayingPage(
                            state = nowPlayingState,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onShuffleToggle = onShuffleToggle,
                            onRepeatToggle = onRepeatToggle,
                            onSeek = onSeek,
                            onVolumeDown = onVolumeDown,
                            onVolumeUp = onVolumeUp,
                            onVolumeChanged = onVolumeChanged,
                            onSleepTimerSelected = onSleepTimerSelected,
                            onTrackClicked = onTrackClicked,
                            onSelectTargetPlayer = onSelectTargetPlayer
                        )
                        1 -> QueuePage(
                            state = queueState,
                            onTrackClicked = onQueueTrackClicked,
                            onTrackRemoved = onQueueTrackRemoved,
                            onClearQueue = onQueueClear,
                            onJumpToPlaying = onQueueJumpToPlaying,
                            onMoveTrack = onQueueTrackMoved
                        )
                        2 -> MediaServerPage(
                            state = mediaServerState,
                            onEngineChanged = onEngineChanged,
                            onStartClicked = onStartServerClicked,
                            onStopClicked = onStopServerClicked,
                            onCopyUrlClicked = onCopyUrlClicked,
                            onOpenUrlClicked = onOpenUrlClicked,
                            onQrCodeClicked = onQrCodeClicked
                        )
                    }
                }
            }
        }
    }
}
