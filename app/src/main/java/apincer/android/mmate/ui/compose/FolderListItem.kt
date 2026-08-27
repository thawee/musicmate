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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.model.Track
import coil3.compose.AsyncImage

private val folderCardShape  = RoundedCornerShape(12.dp)
private val folderCardBgDark = Color(0x80000000)
private val folderCardBg     = Color(0x1AFFFFFF)
private val folderCardBorder = Color(0x4DFFFFFF)

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
            // ── Image Frame (72dp matches XML) ───────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2C))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_collections),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = Color(0xFF666666)
                )
                AsyncImage(
                    model = track,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Subtle overlay (matches XML alpha=0.2)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Text Column ───────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: "Collection Title",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val subtitle = track.description?.takeIf { it.isNotEmpty() } ?: track.album ?: ""
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Statistics line: e.g. "45 tracks • 3:24:10"
                val count = track.childCount
                val durStr = if (track.audioDuration > 0) apincer.music.core.utils.StringUtils.formatDuration(track.audioDuration, true) else ""
                val statsText = when {
                    count > 0 && durStr.isNotEmpty() -> "$count tracks • $durStr"
                    count > 0 -> "$count tracks"
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

            // ── Quick Actions (Enqueue / Play) ────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEnqueueClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_queue_music_24),
                        contentDescription = "Add to queue",
                        tint = Color(0xFF888888)
                    )
                }
                IconButton(onClick = onPlayClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_rounded),
                        contentDescription = "Play",
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}
