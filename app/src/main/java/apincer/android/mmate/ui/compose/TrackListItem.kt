package apincer.android.mmate.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import apincer.android.mmate.R
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.target.ImageViewTarget
import android.widget.ImageView

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            coil3.compose.AsyncImage(
                model = track,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // New Badge Overlay
            NewBadge(
                track = track,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )

            // Now Playing Overlay
            if (isNowPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_equalizer_active else R.drawable.ic_baseline_pause_24),
                        contentDescription = "Now Playing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title ?: "Unknown Title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Duration
                Text(
                    text = StringUtils.formatDuration(track.audioDuration, false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(6.dp))

                DynamicRangeMeter(track = track)

                Spacer(modifier = Modifier.width(6.dp))

                QualityBadge(track = track)
            }
        }

        // More Menu
        IconButton(onClick = onMenuClick) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_more_vert_24),
                contentDescription = "More Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
