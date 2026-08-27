package apincer.android.mmate.ui.compose

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
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

                // Premium Now Playing Overlay - Glassmorphism circular badge
                if (isNowPlaying) {
                    // Subtle darkening to make the badge pop, without hiding the art completely
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x33000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Circular Frosted Glass Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0x80000000)) // Dark base
                                .background(Color(0x1AFFFFFF)) // Frosted glass tint
                                .border(1.dp, Color(0x4DFFFFFF), androidx.compose.foundation.shape.CircleShape), // Glass stroke
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.rounded_equalizer_24
                                    else R.drawable.ic_baseline_pause_24
                                ),
                                contentDescription = "Now Playing",
                                tint = colorGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(track = track)
                    Spacer(modifier = Modifier.width(4.dp))
                    ResolutionBadge(track = track)
                    Spacer(modifier = Modifier.width(5.dp))
                    DynamicRangeMeter(track = track)
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
