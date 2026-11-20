package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import apincer.android.mmate.MusixMateApp
import apincer.android.mmate.R
import apincer.music.core.model.PlaylistEntry
import apincer.music.core.model.SearchCriteria
import apincer.music.core.repository.PlaylistRepository
import apincer.music.core.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MoodCount(val tracks: Int, val seconds: Double)

private val moodIcons = mapOf(
    "mood-drive" to R.drawable.ic_baseline_directions_car_24,
    "mood-focus" to R.drawable.ic_center_focus_strong_black_24dp,
    "mood-wind-down" to R.drawable.rounded_equalizer_24,
    "mood-sleep" to R.drawable.ic_baseline_bedtime_24,
)

@Composable
fun MoodPickerDialog(
    activePlaylistName: String?,
    onDismissRequest: () -> Unit,
    onMoodSelected: (PlaylistEntry) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isVisible by remember { mutableStateOf(false) }
    var moods by remember { mutableStateOf(emptyList<PlaylistEntry>()) }
    // null while the library scan runs, so rows say "Counting" instead of "0 tracks"
    var counts by remember { mutableStateOf<Map<String, MoodCount>?>(null) }

    LaunchedEffect(Unit) {
        PlaylistRepository.loadPlaylists(context)
        moods = PlaylistRepository.getPlaylists()?.filter { it.isMood } ?: emptyList()
        isVisible = true
        counts = withContext(Dispatchers.IO) {
            val containers = MusixMateApp.getInstance()?.tagRepository
                ?.findPlaylist(SearchCriteria(SearchCriteria.TYPE.PLAYLIST))
                .orEmpty()
            containers.associate { it.title to MoodCount(it.childCount.toInt(), it.audioDuration) }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mood_dialog_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "mood_dialog_alpha"
    )
    val accent = MusicMateDesignTokens.SourceAccent.Mood

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MusicMateDesignTokens.Surfaces.Dialog,
            border = BorderStroke(1.dp, MusicMateDesignTokens.Surfaces.BorderPrimary),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_graphic_eq_black_24dp),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.queue_mood_title),
                                color = MusicMateDesignTokens.Text.Primary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = stringResource(R.string.queue_mood_subtitle),
                                color = MusicMateDesignTokens.Text.Secondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.round_close_24),
                            contentDescription = stringResource(R.string.close),
                            tint = MusicMateDesignTokens.Text.Telemetry,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = MusicMateDesignTokens.Surfaces.BorderSubtle,
                    thickness = 0.75.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = moods, key = { it.uuid ?: it.name.orEmpty() }) { entry ->
                        MoodRow(
                            entry = entry,
                            count = counts?.let { it[entry.name] ?: MoodCount(0, 0.0) },
                            isActive = entry.name.equals(activePlaylistName, ignoreCase = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoodSelected(entry)
                                onDismissRequest()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.queue_mood_footer),
                    color = MusicMateDesignTokens.Text.Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MoodRow(
    entry: PlaylistEntry,
    count: MoodCount?,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val accent = MusicMateDesignTokens.SourceAccent.Mood
    val shape = RoundedCornerShape(14.dp)
    val enabled = count == null || count.tracks > 0
    val countLine = when {
        count == null -> stringResource(R.string.queue_mood_counting)
        count.tracks == 0 -> stringResource(R.string.queue_mood_no_tracks)
        else -> pluralStringResource(
            R.plurals.queue_mood_track_count,
            count.tracks,
            count.tracks,
            StringUtils.formatDuration(count.seconds, true)
        )
    }
    val sessionLine = if (entry.sessionMinutes > 0) {
        stringResource(R.string.queue_mood_stops_after, entry.sessionMinutes)
    } else {
        stringResource(R.string.queue_mood_keeps_going)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(if (isActive) accent.copy(alpha = 0.12f) else MusicMateDesignTokens.Surfaces.RowFill)
            .border(
                0.75.dp,
                if (isActive) accent.copy(alpha = 0.5f) else MusicMateDesignTokens.Surfaces.BorderSubtle,
                shape
            )
            .semantics { selected = isActive }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = moodIcons[entry.uuid] ?: R.drawable.ic_graphic_eq_black_24dp),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name ?: stringResource(R.string.queue_picker_unnamed),
                    color = MusicMateDesignTokens.Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.queue_mood_selected),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            entry.description?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = MusicMateDesignTokens.Text.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$countLine\n$sessionLine",
                color = MusicMateDesignTokens.Text.Muted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        if (isActive) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_check_24),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
