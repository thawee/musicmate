package apincer.android.mmate.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R

/** Stacked tiles on a narrow screen fold their detail into one line, so both fit above the controls. */
private val LocalCompactTile = staticCompositionLocalOf { false }

/** Matching destinations: where music plays and how it sounds. */
@Composable
internal fun AudioControlTile(
    title: String,
    value: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color = MusicMateDesignTokens.Brand.AcousticTeal,
    description: String = listOfNotNull(value, detail).joinToString(". "),
    indicator: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val compact = LocalCompactTile.current
    Surface(onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 56.dp else 72.dp).semantics(mergeDescendants = true) {
            contentDescription = title
            stateDescription = description
        }, shape = RoundedCornerShape(12.dp),
        color = MusicMateDesignTokens.Surfaces.GlassPill,
        border = BorderStroke(1.dp, MusicMateDesignTokens.Surfaces.BorderSubtle)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = MusicMateDesignTokens.Text.Primary, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false))
                    indicator?.invoke()
                }
                Text(if (compact) listOfNotNull(value, detail?.takeIf { it.isNotBlank() }).joinToString(" · ") else value,
                    color = MusicMateDesignTokens.Text.Secondary, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!compact && !detail.isNullOrBlank()) Text(detail, color = MusicMateDesignTokens.Text.Secondary,
                    fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.ic_expand_more), null,
                tint = MusicMateDesignTokens.Text.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun AudioControlPair(modifier: Modifier = Modifier,
    output: @Composable (Modifier) -> Unit, tuning: @Composable (Modifier) -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp || largeText) {
            // Large text keeps every line; it scrolls instead
            CompositionLocalProvider(LocalCompactTile provides !largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    output(Modifier.fillMaxWidth())
                    tuning(Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                output(Modifier.weight(1f).fillMaxHeight())
                tuning(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun PlaybackAudioControls(outputName: String, outputDetail: String?, remoteTarget: String?,
    onSelectOutput: () -> Unit, modifier: Modifier = Modifier) {
    AudioControlPair(modifier,
        output = { tile -> AudioControlTile(stringResource(R.string.playback_output), outputName,
            R.drawable.rounded_speaker_24, tile, detail = outputDetail, onClick = onSelectOutput) },
        tuning = { tile -> AudioTuningChip(tile, remoteTarget) })
}
