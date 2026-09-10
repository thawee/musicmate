package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track
import coil3.compose.AsyncImage

private val folderCardShape  = RoundedCornerShape(12.dp)
private val folderCardBgDark = Color(0x80000000)
private val folderCardBg     = Color(0x1AFFFFFF)
private val folderCardBorder = Color(0x4DFFFFFF)

private data class SmartArtworkTheme(
    val brush: Brush,
    val badgeText: String,
    val badgeSubtext: String,
    val accentColor: Color
)

@Composable
fun FolderListItem(
    track: Track,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onEnqueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(folderCardShape)
            .background(folderCardBgDark, folderCardShape)
            .background(folderCardBg, folderCardShape)
            .border(width = 1.5.dp, color = folderCardBorder, shape = folderCardShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 88.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Dynamic Audiophile Cover Art Frame ───────────────────────────
            PlaylistCoverArt(track = track)

            Spacer(modifier = Modifier.width(12.dp))

            // ── Text Column (Title, Description, Stats) ──────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = track.title ?: "Collection Title",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )
                
                val subtitle = track.description?.takeIf { it.isNotEmpty() } ?: track.album ?: ""
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        maxLines = 2,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Statistics line: e.g. "45 tracks • 3:24:10" or "0 tracks"
                val count = track.childCount
                val durStr = if (track.audioDuration > 0) apincer.music.core.utils.StringUtils.formatDuration(track.audioDuration, true) else ""
                val statsText = when {
                    count > 0 && durStr.isNotEmpty() -> "$count tracks • $durStr"
                    count > 0 -> "$count tracks"
                    count == 0L && track.isContainer -> "0 tracks"
                    durStr.isNotEmpty() -> durStr
                    else -> track.artist ?: ""
                }

                if (statsText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = statsText,
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // ── Quick Actions (Enqueue / Play) ────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onEnqueueClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_queue_music_24),
                        contentDescription = "Add to queue",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_rounded),
                        contentDescription = "Play",
                        tint = Color(0xFFEEEEEE),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverArt(track: Track, modifier: Modifier = Modifier) {
    val title = track.title ?: ""
    val isDr12 = title.contains("DR12", ignoreCase = true) || title.contains("Sanctuary", ignoreCase = true)
    val isHiRes = title.contains("Hi-Res", ignoreCase = true) || title.contains("Studio Masters", ignoreCase = true)
    val isDsd = title.contains("DSD", ignoreCase = true)
    val isLossless = title.contains("Lossless", ignoreCase = true) || title.contains("Vault", ignoreCase = true)
    val isClassical = title.contains("Classical", ignoreCase = true)
    val isMasterpiece = title.contains("Masterpiece", ignoreCase = true)

    val theme = when {
        isDr12 -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF2E1C05), Color(0xFF5A3C08), Color(0xFFB78103))),
            badgeText = "DR 12+",
            badgeSubtext = "AUDIOPHILE",
            accentColor = Color(0xFFFFD54F)
        )
        isHiRes -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF091629), Color(0xFF133658), Color(0xFF1E6F9F))),
            badgeText = "24-BIT",
            badgeSubtext = "STUDIO",
            accentColor = Color(0xFF64B5F6)
        )
        isDsd -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF02231A), Color(0xFF005844), Color(0xFF00897B))),
            badgeText = "DSD",
            badgeSubtext = "1-BIT DIRECT",
            accentColor = Color(0xFF4DB6AC)
        )
        isLossless -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF200C2E), Color(0xFF43165E), Color(0xFF7227A6))),
            badgeText = "VAULT",
            badgeSubtext = "LOSSLESS",
            accentColor = Color(0xFFBA68C8)
        )
        isClassical -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF290812), Color(0xFF521326), Color(0xFF7A1C39))),
            badgeText = "CLASSICAL",
            badgeSubtext = "HERITAGE",
            accentColor = Color(0xFFEF9A9A)
        )
        isMasterpiece -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF241406), Color(0xFF4D2C0C), Color(0xFF8A5319))),
            badgeText = "REFERENCE",
            badgeSubtext = "ARCHIVE",
            accentColor = Color(0xFFFFB74D)
        )
        else -> SmartArtworkTheme(
            brush = Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF161616))),
            badgeText = "",
            badgeSubtext = "",
            accentColor = Color(0xFFFFD700)
        )
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.brush)
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (theme.badgeText.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = theme.badgeText,
                    color = theme.accentColor,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
                if (theme.badgeSubtext.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = theme.badgeSubtext,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_collections),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color(0xFFCCCCCC)
            )
        }

        // Overlay with actual album / collection cover art if present on disk
        AsyncImage(
            model = CoverartFetcher.builder(LocalContext.current, track).data(track).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
