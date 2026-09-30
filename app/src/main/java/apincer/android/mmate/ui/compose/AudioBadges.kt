package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.ui.viewmodel.StudioProvenanceInfo
import apincer.music.core.Constants
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.TagUtils

@Composable
fun QualityBadge(track: Track?, modifier: Modifier = Modifier, expanded: Boolean = false) {
    if (track == null) return
    val label = AudioPresentation.qualityLabel(track, expanded)
    val accentColor = AudioPresentation.accent(track)

    val bgBase = Color(0xD9101010)
    val bgTint = accentColor.copy(alpha = if (expanded) 0.12f else 0.08f)
    val borderColor = accentColor.copy(alpha = if (expanded) 0.45f else 0.38f)
    val dotSize = if (expanded) 5.dp else 3.5.dp
    val dotSpacing = if (expanded) 5.dp else 3.5.dp
    val textFontSize = if (expanded) 10.5.sp else 9.sp
    val hPadding = if (expanded) 8.dp else 5.dp
    val vPadding = if (expanded) 3.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(0.75.dp, borderColor, CircleShape)
            .padding(horizontal = hPadding, vertical = vPadding)
            .semantics { contentDescription = "Audio quality: $label" },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(dotSpacing))
            Text(
                text = label,
                color = Color.White,
                fontSize = textFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = if (expanded) 0.3.sp else 0.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun QualityBadge(labelStr: String?, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val label = AudioPresentation.qualityLabel(labelStr, expanded)
    val accentColor = AudioPresentation.accent(labelStr)

    val bgBase = Color(0xD9141414)
    val bgTint = accentColor.copy(alpha = if (expanded) 0.12f else 0.08f)
    val borderColor = accentColor.copy(alpha = if (expanded) 0.38f else 0.30f)
    val dotSize = if (expanded) 5.dp else 3.5.dp
    val dotSpacing = if (expanded) 5.dp else 3.5.dp
    val textFontSize = if (expanded) 10.5.sp else 9.sp
    val hPadding = if (expanded) 8.dp else 5.dp
    val vPadding = if (expanded) 3.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(0.5.dp, borderColor, CircleShape)
            .padding(horizontal = hPadding, vertical = vPadding)
            .semantics { contentDescription = "Audio quality: $label" },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(dotSpacing))
            Text(
                text = label,
                color = Color.White,
                fontSize = textFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = if (expanded) 0.3.sp else 0.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ResolutionBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null) return
    val resText = AudioPresentation.compactResolution(track)

    if (resText.isEmpty()) return

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xD9141414))
            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "Resolution: ${AudioPresentation.resolutionDescription(track)}" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = resText,
            color = Color(0xFFDDDDDD),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

fun getUnifiedBadgeText(track: Track?): String = AudioPresentation.unifiedBadgeText(track)

@Composable
fun UnifiedAudioBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null) return

    val text = getUnifiedBadgeText(track)

    val accentColor = AudioPresentation.accent(track)

    val bgBase = Color(0xD9141414)
    val bgTint = accentColor.copy(alpha = 0.08f)
    val borderColor = accentColor.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(0.5.dp, borderColor, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "Audio quality: ${AudioPresentation.qualityLabel(track, true)}; ${AudioPresentation.resolutionDescription(track)}" },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun TagHeaderBadges(
    track: Track?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {}
) {
    if (track == null) return
    FlowRow(
        modifier = modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QualityBadge(track = track, expanded = true)
        ResolutionBadge(track = track)
        DynamicRangeMeter(track = track, highContrast = true)
        RatingBadge(track = track, mode = "mini")
        NewBadge(track = track)
        trailingContent()
    }
}

@Composable
fun TaxonomyChip(
    label: String,
    icon: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val clickableMod = if (onClick != null) {
        Modifier.clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(clickableMod)
            .heightIn(min = 48.dp)
            .padding(horizontal = 9.dp, vertical = 3.5.dp)
            .semantics {
                contentDescription = if (onClick != null) "$label. Tap to explore related tracks." else label
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color(0xFFEEEEEE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewGenreChip(
    track: Track?,
    onOpenRelated: ((filterType: String, filterKeyword: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    fun cleanTag(s: String?): String {
        val trimmed = s?.trim().orEmpty()
        return if (trimmed.equals(Constants.UNKNOWN, ignoreCase = true) ||
            trimmed.equals(Constants.NONE, ignoreCase = true) ||
            trimmed == "-" ||
            trimmed.startsWith("[")
        ) "" else trimmed
    }

    val genre = cleanTag(track.genre)

    if (genre.isEmpty()) return

    TaxonomyChip(
        label = "${stringResource(R.string.preview_genre_label)}: $genre",
        icon = R.drawable.rounded_label_24,
        accentColor = Color(0xFF90CAF9),
        modifier = modifier,
        onClick = onOpenRelated?.let { callback ->
            { callback(Constants.FILTER_TYPE_GENRE, genre, "Genre: $genre") }
        }
    )
}

@Composable
private fun ProvenanceRow(
    icon: Int,
    fieldLabel: String,
    label: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = fieldLabel, color = Color(0xFFBCC5CF), fontSize = 12.sp)
                Text(
                    text = label,
                    color = Color(0xFFEEEEEE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.preview_track_count, count, count),
                    color = Color(0xFFBCC5CF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StudioProvenanceSection(
    provenance: StudioProvenanceInfo,
    onOpenRelated: (filterType: String, filterKeyword: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasArtist = provenance.artist.isNotBlank() && !provenance.artist.startsWith("[")
    val hasAlbum = provenance.album.isNotBlank() && !provenance.album.startsWith("[")
    val hasFolder = provenance.folderPath.isNotBlank()

    if (!hasArtist && !hasAlbum && !hasFolder) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF20262B)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Consistent full-width rows keep labels and navigation affordances aligned.
        if (hasArtist || hasAlbum) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hasArtist) {
                    ProvenanceRow(
                        icon = R.drawable.ic_artist_black_24dp,
                        fieldLabel = stringResource(R.string.preview_artist_label),
                        label = provenance.artist,
                        count = provenance.artistCount,
                        accentColor = Color(0xFF90CAF9),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ARTIST, provenance.artist, "More by ${provenance.artist}")
                        }
                    )
                }
                if (hasAlbum) {
                    if (hasArtist) ProvenanceDivider()
                    ProvenanceRow(
                        icon = R.drawable.ic_album_black_24dp,
                        fieldLabel = stringResource(R.string.preview_album_label),
                        label = provenance.album,
                        count = provenance.albumCount,
                        accentColor = Color(0xFF90CAF9),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ALBUM, provenance.album, "Album: ${provenance.album}")
                        }
                    )
                }
            }
        }

        // Location is labeled separately even when it shares the album name.
        if (hasFolder) {
            val fName = provenance.folderName.ifBlank { "Folder" }
            if (hasArtist || hasAlbum) ProvenanceDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProvenanceRow(
                    icon = R.drawable.rounded_folder_24,
                    fieldLabel = stringResource(R.string.preview_folder_label),
                    label = fName,
                    count = provenance.folderCount,
                    accentColor = Color(0xFF90CAF9),
                    onClick = {
                        onOpenRelated(Constants.FILTER_TYPE_PATH, provenance.folderPath, "Folder: $fName")
                    }
                )
            }
        }
    }
}

@Composable
private fun ProvenanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp, end = 12.dp),
        thickness = 0.5.dp,
        color = Color(0xFF354049)
    )
}

@Composable
fun NewBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null || track.isManaged) return

    val isDownload = TagUtils.isOnDownloadDir(track)
    val accentColor = if (isDownload) Color(0xFF64B5F6) else Color(0xFFFFD700)
    val borderColor = if (isDownload) Color(0x5564B5F6) else Color(0x55FFD700)
    val bgBase = Color(0xD9141414)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .border(0.5.dp, borderColor, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = if (isDownload) "Downloaded track" else "New track" },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            Text(
                text = if (isDownload) "DL" else "NEW",
                color = accentColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun RatingBadge(track: Track?, mode: String?, modifier: Modifier = Modifier) {
    if (track == null) return
    val rating = TagUtils.getRating(track)
    
    when (mode) {
        "icon", "mini" -> {
            if (rating >= 3) {
                Text(
                    text = "🤍",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        else -> {
            val ratingColor = colorResource(R.color.rating_text)
            Row(modifier = modifier) {
                for (i in 0 until 5) {
                    Text(
                        text = if (i < rating) "✭" else "✩",
                        color = if (i < rating) ratingColor else Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
@Composable
fun TagPreviewHeader(
    track: Track?,
    itemCount: Int = 1,
    provenance: StudioProvenanceInfo? = null,
    onOpenRelated: ((filterType: String, filterKeyword: String, title: String) -> Unit)? = null,
    onQuickFixClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (track == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (itemCount > 1) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FFD700))
                    .border(0.75.dp, Color(0x66FFD700), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎯 BATCH MODE • $itemCount TRACKS SELECTED",
                    color = Color(0xFFFFD700),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        TagHeaderBadges(track = track, modifier = Modifier.fillMaxWidth()) {
            PreviewGenreChip(track = track, onOpenRelated = onOpenRelated)
        }
        if (provenance != null && onOpenRelated != null) {
            Spacer(modifier = Modifier.height(8.dp))
            StudioProvenanceSection(
                provenance = provenance,
                onOpenRelated = onOpenRelated
            )
        }
    }
}
