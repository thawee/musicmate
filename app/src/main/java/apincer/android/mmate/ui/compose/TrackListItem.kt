package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils

// ── Glass card constants (matches selector_item_background.xml) ───────────────
private val cardShape       = RoundedCornerShape(12.dp)
private val cardBg          = Color(0x1AFFFFFF)   // 10% white frosted tint
private val cardBgDark      = Color(0x80000000)   // 50% dark base
private val cardBorderNormal = Color(0x4DFFFFFF)  // 30% white stroke
private val cardBorderSelected = Color(0xFF1E88E5) // blue stroke when selected
private val colorGold       = Color(0xFFFFD700)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    isNowPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onQuickPlayClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgOverlay = if (isSelected) Color(0x2B1E88E5) else Color.Transparent
    val borderStroke = if (isSelected) BorderStroke(1.5.dp, Color(0xFF1E88E5)) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgOverlay)
            .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 88.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Album Art (76dp) (DESIGN.md §2: Tap Art for Quick Play) ──────
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202020))
                    .border(
                        width = if (isNowPlaying) 1.5.dp else 1.dp,
                        color = if (isNowPlaying) colorGold.copy(alpha = 0.85f) else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onQuickPlayClick)
            ) {
                coil3.compose.AsyncImage(
                    model = CoverartFetcher.builder(LocalContext.current, track).data(track).build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // New badge top-right
                NewBadge(
                    track = track,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )

                // Audiophile Now Playing Indicator - Floating micro-badge at bottom-right
                if (isNowPlaying) {
                    NowPlayingCoverBadge(
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // ── Title + Artist + Metadata ────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = track.title ?: "Unknown Title",
                    fontWeight = FontWeight.Bold,
                    color = if (isNowPlaying) colorGold else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val artistAlbumText = when {
                    !track.artist.isNullOrEmpty() && !track.album.isNullOrEmpty() && !track.album.equals(track.title, ignoreCase = true) -> "${track.artist} • ${track.album}"
                    !track.artist.isNullOrEmpty() -> track.artist
                    !track.album.isNullOrEmpty() -> track.album
                    else -> "Unknown Artist"
                }

                Text(
                    text = artistAlbumText,
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val isCompact = screenWidthDp < 390

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCompact) {
                        UnifiedAudioBadge(track = track)
                        Spacer(modifier = Modifier.width(4.dp))
                        DynamicRangeMeter(track = track, compact = true)
                    } else {
                        QualityBadge(track = track)
                        Spacer(modifier = Modifier.width(4.dp))
                        ResolutionBadge(track = track)
                        Spacer(modifier = Modifier.width(5.dp))
                        DynamicRangeMeter(track = track, compact = false)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = StringUtils.formatDuration(track.audioDuration, false),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // ── Rating badge top-right (matches XML position) ────────────────
            Column(horizontalAlignment = Alignment.End) {
                RatingBadge(track = track, mode = "icon")
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_more_vert_24),
                        contentDescription = "More Options",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Ultra-refined audiophile "Now Playing" cover art badge.
 * Renders a compact, frosted glass micro-capsule in the bottom corner of the album art.
 * - When playing: Displays dynamic, 4-bar animated equalizer spectrum in champagne gold.
 * - When paused: Displays two sleek, centered precision pause bars in gold.
 */
@Composable
fun NowPlayingCoverBadge(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xE6101010)) // 90% obsidian glass
            .border(
                width = 0.75.dp,
                color = if (isPlaying) colorGold.copy(alpha = 0.65f) else Color(0x4DFFFFFF),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            AnimatedEqualizerBars(
                modifier = Modifier.size(width = 16.dp, height = 12.dp)
            )
        } else {
            PausedIndicatorBars(
                modifier = Modifier.size(width = 16.dp, height = 12.dp)
            )
        }
    }
}

/**
 * High-performance, zero-allocation animated equalizer bars.
 * Driven by an [rememberInfiniteTransition] with staggered sinusoidal frequencies.
 */
@Composable
private fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    barColorStart: Color = Color(0xFFFFE082), // Champagne gold highlight
    barColorEnd: Color = Color(0xFFFFB300)    // Warm amber-gold base
) {
    val transition = rememberInfiniteTransition(label = "NowPlayingEqualizerTransition")

    // 4 vertical bars with staggered durations and sinusoidal easing to simulate audio spectrum
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar4"
    )

    val gradientBrush = remember(barColorStart, barColorEnd) {
        Brush.verticalGradient(listOf(barColorStart, barColorEnd))
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barCount = 4
        val barWidth = (totalWidth * 0.16f).coerceAtLeast(1f)
        val totalBarWidth = barWidth * barCount
        val gap = (totalWidth - totalBarWidth) / (barCount - 1)
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        val fractions = floatArrayOf(bar1, bar2, bar3, bar4)

        for (i in 0 until barCount) {
            val fraction = fractions[i]
            val barHeight = (totalHeight * fraction).coerceIn(barWidth, totalHeight)
            val left = i * (barWidth + gap)
            val top = totalHeight - barHeight

            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Sleek, precision pause glyph for the Now Playing badge when playback is paused.
 */
@Composable
private fun PausedIndicatorBars(
    modifier: Modifier = Modifier,
    barColor: Color = colorGold
) {
    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height

        val barWidth = (totalWidth * 0.16f).coerceAtLeast(1f)
        val barHeight = totalHeight * 0.75f
        val gap = barWidth * 1.5f
        val totalPauseWidth = (barWidth * 2) + gap
        val startX = (totalWidth - totalPauseWidth) / 2f
        val top = (totalHeight - barHeight) / 2f
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Left pause bar
        drawRoundRect(
            color = barColor,
            topLeft = Offset(startX, top),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius
        )

        // Right pause bar
        drawRoundRect(
            color = barColor,
            topLeft = Offset(startX + barWidth + gap, top),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius
        )
    }
}

