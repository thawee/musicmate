package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.animateDpAsState
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
    onTrackClicked: () -> Unit,
    // Callbacks for QueuePage
    onQueueTrackClicked: (Track) -> Unit,
    onQueueTrackRemoved: (Track, Int) -> Unit,
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
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2)) { 3 }
    val tabs = listOf("Playback", "Queue", "Server")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xF0121212),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x4DFFFFFF))
            )
        },
        modifier = modifier.fillMaxHeight(0.94f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
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
                            tint = Color.White,
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
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1AFFFFFF))
                    .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(3.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(17.dp))
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

            // Horizontal Pager with the 3 Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
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
                        onTrackClicked = onTrackClicked
                    )
                    1 -> QueuePage(
                        state = queueState,
                        onTrackClicked = onQueueTrackClicked,
                        onTrackRemoved = onQueueTrackRemoved,
                        onClearQueue = onQueueClear,
                        onJumpToPlaying = onQueueJumpToPlaying
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
