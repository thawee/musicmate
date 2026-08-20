package apincer.android.mmate.ui.compose

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.playback.PlaybackState

@Composable
fun NowPlayingPage(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onTrackClicked: () -> Unit
) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(400)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val track = state.track.value
        val bitmap = state.albumArt.value
        val duration = state.durationMs.value
        val progress = state.progressMs.value

        // Artwork / Specs Flip Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { onTrackClicked() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onPlayPause() },
                        onLongPress = { flipped = !flipped }
                    )
                }
                .pointerInput(Unit) {
                    var dragAmount = 0f
                    detectDragGestures(
                        onDragEnd = {
                            if (dragAmount > 80f) onPrevious()
                            else if (dragAmount < -80f) onNext()
                            dragAmount = 0f
                        }
                    ) { change, drag ->
                        change.consume()
                        dragAmount += drag.x
                    }
                }
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 8 * density
                },
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                // Front: Album Art
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_now_playing_idle),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.6f),
                        tint = Color.Gray
                    )
                }
                
                IconButton(
                    onClick = { flipped = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0x66000000), RoundedCornerShape(20.dp))
                ) {
                    Icon(painterResource(id = R.drawable.rounded_swap_horiz_24), contentDescription = "Flip", tint = Color.White)
                }
            } else {
                // Back: Tech Specs
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2C2C2C))
                        .graphicsLayer { rotationY = 180f }
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "TECHNICAL SPECS", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Text(text = state.specsFormat.value, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = state.specsBitrate.value, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = state.specsDr.value, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = state.specsFileSize.value, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    IconButton(
                        onClick = { flipped = false },
                        modifier = Modifier.padding(top = 24.dp).background(Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    ) {
                        Icon(painterResource(id = R.drawable.rounded_swap_horiz_24), contentDescription = "Flip Back", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title and Artist
        Text(
            text = track?.title ?: "Music Mate Ready",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track?.artist ?: "Select a song or player target",
            color = Color(0xFFBDBDBD),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Progress Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val curSec = (progress / 1000).toInt()
            val totSec = (duration / 1000).toInt()
            
            Text(text = String.format("%d:%02d", curSec / 60, curSec % 60), color = Color.Gray, fontSize = 12.sp)
            Slider(
                value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
                onValueChange = onSeek,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF80CBC4),
                    inactiveTrackColor = Color.DarkGray
                )
            )
            Text(text = String.format("%d:%02d", totSec / 60, totSec % 60), color = Color.Gray, fontSize = 12.sp)
        }

        // Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffleToggle) {
                Icon(painterResource(id = R.drawable.ic_baseline_shuffle_24), contentDescription = "Shuffle", tint = if (state.isShuffle.value) Color(0xFF80CBC4) else Color.Gray)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(id = R.drawable.ic_skip_previous_rounded), contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp).background(Color(0xFF2C2C2C), RoundedCornerShape(36.dp))
            ) {
                val isPlaying = state.playbackState.value?.currentState == PlaybackState.State.PLAYING
                Icon(
                    painterResource(id = if (isPlaying) R.drawable.ic_pause_rounded else R.drawable.ic_play_rounded),
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(id = R.drawable.ic_skip_next_rounded), contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onRepeatToggle) {
                val repeatIcon = if (state.repeatMode.value == 2) R.drawable.ic_baseline_repeat_one_24 else R.drawable.ic_baseline_repeat_24
                Icon(painterResource(id = repeatIcon), contentDescription = "Repeat", tint = if (state.repeatMode.value > 0) Color(0xFF80CBC4) else Color.Gray)
            }
        }
    }
}
