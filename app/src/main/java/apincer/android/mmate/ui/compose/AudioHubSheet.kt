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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
    onQueueBrowseLibrary: () -> Unit,
    // Callbacks for MediaServerPage
    onEngineChanged: (String) -> Unit,
    onStartServerClicked: () -> Unit,
    onStopServerClicked: () -> Unit,
    onCopyUrlClicked: () -> Unit,
    onOpenUrlClicked: () -> Unit,
    onQrCodeClicked: () -> Unit,
    onOpenFullscreen: () -> Unit = {},
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

    // Dynamic Tab State
    val queueCount = queueState.tracks.size
    val isServerActive = mediaServerState.isServerRunning

    // Dynamic Cast Icon Tint (§5B: Gold #FFC107 when DLNA active)
    val target = nowPlayingState.targetTitle.value
    val isDlnaCastActive = target.contains("DLNA", ignoreCase = true) || target.contains("Renderer", ignoreCase = true) || target.contains("Streamer", ignoreCase = true)
    val castIconTint = if (isDlnaCastActive) Color(0xFFFFC107) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), // DESIGN.md §8C: 24dp top corner radius
        containerColor = Color(0xFF121212),
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
                                onOpenFullscreen()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_fullscreen_24),
                            contentDescription = "Studio Console Fullscreen",
                            tint = Color.White.copy(alpha = 0.9f),
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

            // Fluid Audiophile Glass Pill Switcher
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161616))
                    .border(0.75.dp, Color(0x24FFFFFF), RoundedCornerShape(20.dp))
                    .padding(3.dp)
            ) {
                val tabWidth = maxWidth / 3
                val pageFraction = (pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                val indicatorOffset = tabWidth * pageFraction

                // 1. Fluid Sliding Indicator Pill (1:1 real-time drag-tracking)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x38FFD700), // Champagne gold ambient glow
                                    Color(0x1CFFD700)  // Deep gold base
                                )
                            )
                        )
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x66FFD700), // Hairline metallic highlight
                                    Color(0x26FFD700)
                                )
                            ),
                            RoundedCornerShape(17.dp)
                        )
                )

                // 2. Interactive Tab Labels Row
                Row(modifier = Modifier.fillMaxSize()) {
                    // Tab 0: Playback
                    val isPlaybackSelected = kotlin.math.abs(pageFraction - 0f) < 0.5f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(17.dp))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Playback",
                            color = if (isPlaybackSelected) Color(0xFFFFD700) else Color(0x99FFFFFF),
                            fontSize = 12.5.sp,
                            fontWeight = if (isPlaybackSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        )
                    }

                    // Tab 1: Queue (with Monospace Count Pill)
                    val isQueueSelected = kotlin.math.abs(pageFraction - 1f) < 0.5f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(17.dp))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Queue",
                                color = if (isQueueSelected) Color(0xFFFFD700) else Color(0x99FFFFFF),
                                fontSize = 12.5.sp,
                                fontWeight = if (isQueueSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            )
                            if (queueCount > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isQueueSelected) Color(0x33FFD700) else Color(0x22FFFFFF)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = queueCount.toString(),
                                        color = if (isQueueSelected) Color(0xFFFFD700) else Color(0xBBFFFFFF),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Tab 2: Server (with Emerald Jewel LED)
                    val isServerSelected = kotlin.math.abs(pageFraction - 2f) < 0.5f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(17.dp))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Server",
                                color = if (isServerSelected) Color(0xFFFFD700) else Color(0x99FFFFFF),
                                fontSize = 12.5.sp,
                                fontWeight = if (isServerSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            )
                            if (isServerActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                            }
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
                            onBrowseLibrary = onQueueBrowseLibrary,
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
