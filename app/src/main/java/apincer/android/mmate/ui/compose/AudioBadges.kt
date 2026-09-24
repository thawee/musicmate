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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
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
    val label = if (expanded) {
        when {
            TagUtils.isDSD(track) -> "DSD AUDIO"
            TagUtils.isMQA(track) -> if (TagUtils.isMQAStudio(track)) "MQA STUDIO" else "MQA MASTER"
            TagUtils.isHiRes(track) -> "HI-RES LOSSLESS"
            TagUtils.isPCM24Bits(track) -> "24-BIT STUDIO"
            TagUtils.isLossless(track) -> "CD QUALITY"
            TagUtils.isLossy(track) -> "STANDARD QUALITY"
            else -> {
                val raw = track.qualityInd ?: TagUtils.getQualityIndicator(track)
                if (raw.isEmpty() || raw == "-") "CD QUALITY" else raw.uppercase()
            }
        }
    } else {
        var raw = track.qualityInd ?: ""
        if (raw.isEmpty() || raw == "-") {
            raw = TagUtils.getQualityIndicator(track)
        }
        if (raw.isEmpty()) "-"
        else if (raw.startsWith("MQA")) "MQA"
        else raw
    }

    val accentColor = when {
        TagUtils.isDSD(track) -> Color(0xFF00E5FF)
        TagUtils.isHiRes(track) || TagUtils.isPCM24Bits(track) -> Color(0xFFFFD700)
        TagUtils.isMQA(track) -> Color(0xFFE040FB)
        TagUtils.isLossless(track) -> Color(0xFF64B5F6)
        else -> Color(0xFF9E9E9E)
    }

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
    val raw = labelStr ?: "-"
    val label = if (expanded) {
        when {
            raw.contains("DSD", ignoreCase = true) -> "DSD AUDIO"
            raw.contains("MQA", ignoreCase = true) -> "MQA MASTER"
            raw.contains("Hi-Res", ignoreCase = true) || raw.contains("HI-RES", ignoreCase = true) -> "HI-RES LOSSLESS"
            raw.contains("24-BIT", ignoreCase = true) || raw.contains("Studio", ignoreCase = true) -> "24-BIT STUDIO"
            raw.contains("CD", ignoreCase = true) || raw.contains("Lossless", ignoreCase = true) -> "CD QUALITY"
            raw.contains("Standard", ignoreCase = true) || raw.contains("Lossy", ignoreCase = true) -> "STANDARD QUALITY"
            raw.isNotEmpty() && raw != "-" -> raw.uppercase()
            else -> "CD QUALITY"
        }
    } else {
        if (raw.isEmpty()) "-"
        else if (raw.startsWith("MQA")) "MQA"
        else raw
    }

    val accentColor = when {
        label.contains("DSD", ignoreCase = true) -> Color(0xFF00E5FF)
        label.contains("Hi-Res", ignoreCase = true) || label.contains("Studio", ignoreCase = true) || label.contains("HR", ignoreCase = true) || label.contains("24-BIT", ignoreCase = true) -> Color(0xFFFFD700)
        label.contains("MQA", ignoreCase = true) -> Color(0xFFE040FB)
        label.contains("CD", ignoreCase = true) || label.contains("Lossless", ignoreCase = true) -> Color(0xFF64B5F6)
        else -> Color(0xFF9E9E9E)
    }

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
    val resText = when {
        TagUtils.isDSD(track) -> {
            val dsdRate = track.audioSampleRate
            if (dsdRate >= 22579200) "DSD512"
            else if (dsdRate >= 11289600) "DSD256"
            else if (dsdRate >= 5644800) "DSD128"
            else "DSD64"
        }
        track.audioBitsDepth > 0 && track.audioSampleRate > 0 -> {
            val bit = track.audioBitsDepth
            val sr = (track.audioSampleRate / 1000.0)
            val srFormatted = if (sr % 1.0 == 0.0) "${sr.toInt()}" else "$sr"
            "$bit/$srFormatted"
        }
        track.audioBitRate > 0 -> {
            "${(track.audioBitRate / 1000)}k"
        }
        else -> ""
    }

    if (resText.isEmpty()) return

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xD9141414))
            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "Resolution: $resText" },
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

fun getUnifiedBadgeText(track: Track?): String {
    if (track == null) return "-"

    val qualityLabel = when {
        TagUtils.isDSD(track) -> "DSD"
        TagUtils.isHiRes(track) -> "HI-RES"
        TagUtils.isPCM24Bits(track) -> "24-BIT"
        TagUtils.isMQA(track) -> "MQA"
        TagUtils.isLossless(track) -> "CD"
        else -> ""
    }

    val resText = when {
        TagUtils.isDSD(track) -> {
            val dsdRate = track.audioSampleRate
            if (dsdRate >= 22579200) "512"
            else if (dsdRate >= 11289600) "256"
            else if (dsdRate >= 5644800) "128"
            else "64"
        }
        track.audioBitsDepth > 0 && track.audioSampleRate > 0 -> {
            val bit = track.audioBitsDepth
            val sr = (track.audioSampleRate / 1000.0)
            val srFormatted = if (sr % 1.0 == 0.0) "${sr.toInt()}" else "$sr"
            "$bit/$srFormatted"
        }
        track.audioBitRate > 0 -> {
            "${(track.audioBitRate / 1000)}k"
        }
        else -> ""
    }

    return when {
        qualityLabel.isNotEmpty() && resText.isNotEmpty() -> "$qualityLabel $resText"
        qualityLabel.isNotEmpty() -> qualityLabel
        resText.isNotEmpty() -> resText
        else -> "-"
    }
}

@Composable
fun UnifiedAudioBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null) return

    val text = getUnifiedBadgeText(track)

    val accentColor = when {
        TagUtils.isDSD(track) -> Color(0xFF00E5FF)
        TagUtils.isHiRes(track) || TagUtils.isPCM24Bits(track) -> Color(0xFFFFD700)
        TagUtils.isMQA(track) -> Color(0xFFE040FB)
        TagUtils.isLossless(track) -> Color(0xFF64B5F6)
        else -> Color(0xFF9E9E9E)
    }

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
            .semantics { contentDescription = "Audio quality and resolution: $text" },
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
fun TagHeaderBadges(track: Track?, modifier: Modifier = Modifier) {
    if (track == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        QualityBadge(track = track, expanded = true)
        Spacer(modifier = Modifier.width(6.dp))
        ResolutionBadge(track = track)
        Spacer(modifier = Modifier.width(6.dp))
        DynamicRangeMeter(track = track)
        Spacer(modifier = Modifier.width(6.dp))
        RatingBadge(track = track, mode = "mini")
        Spacer(modifier = Modifier.width(6.dp))
        NewBadge(track = track)
    }
}

@Composable
fun TaxonomyChip(
    label: String,
    icon: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val bgBase = Color(0xD9141414)
    val bgTint = accentColor.copy(alpha = 0.10f)
    val borderColor = accentColor.copy(alpha = 0.28f)

    val clickableMod = if (onClick != null) {
        Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(0.5.dp, borderColor, CircleShape)
            .then(clickableMod)
            .padding(horizontal = 9.dp, vertical = 3.5.dp)
            .semantics {
                contentDescription = if (onClick != null) "$label. Tap to explore related tracks." else label
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color(0xFFEEEEEE),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "›",
                    color = accentColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TaxonomyChipsRow(
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
    val mood = cleanTag(track.mood)
    val style = cleanTag(track.style)

    if (genre.isEmpty() && mood.isEmpty() && style.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (genre.isNotEmpty()) {
                TaxonomyChip(
                    label = genre,
                    icon = "🏷️",
                    accentColor = Color(0xFF80CBC4),
                    onClick = onOpenRelated?.let { callback ->
                        { callback(Constants.FILTER_TYPE_GENRE, genre, "Genre: $genre") }
                    }
                )
            }
            if (mood.isNotEmpty()) {
                TaxonomyChip(
                    label = mood,
                    icon = "🎭",
                    accentColor = Color(0xFFCE93D8)
                )
            }
            if (style.isNotEmpty()) {
                TaxonomyChip(
                    label = style,
                    icon = "🎨",
                    accentColor = Color(0xFF90CAF9)
                )
            }
        }
    }
}

@Composable
fun ProvenanceCapsule(
    icon: String,
    label: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bgBase = Color(0xD9141414)
    val bgTint = accentColor.copy(alpha = 0.10f)
    val borderColor = accentColor.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(0.5.dp, borderColor, CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics {
                contentDescription = if (count > 0) "$label. $count tracks available in library." else label
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color(0xFFEEEEEE),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(3.5.dp))
                Text(
                    text = "• $count",
                    color = accentColor.copy(alpha = 0.9f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "❯",
                color = accentColor.copy(alpha = 0.65f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Row 1: Music Discography (Artist & Album in fluid natural flow)
        if (hasArtist || hasAlbum) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasArtist) {
                    ProvenanceCapsule(
                        icon = "👤",
                        label = provenance.artist,
                        count = provenance.artistCount,
                        accentColor = Color(0xFFFFD700),
                        modifier = Modifier.widthIn(max = 200.dp),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ARTIST, provenance.artist, "More by ${provenance.artist}")
                        }
                    )
                }
                if (hasAlbum) {
                    ProvenanceCapsule(
                        icon = "💿",
                        label = provenance.album,
                        count = provenance.albumCount,
                        accentColor = Color(0xFF80CBC4),
                        modifier = Modifier.widthIn(max = 200.dp),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ALBUM, provenance.album, "Album: ${provenance.album}")
                        }
                    )
                }
            }
        }

        // Row 2: Location (Folder - only if distinct from album and artist)
        val isFolderRedundant = hasFolder && (
            provenance.folderName.equals(provenance.album, ignoreCase = true) ||
            provenance.folderName.equals(provenance.artist, ignoreCase = true)
        )
        if (hasFolder && !isFolderRedundant) {
            val fName = provenance.folderName.ifBlank { "Folder" }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProvenanceCapsule(
                    icon = "📁",
                    label = fName,
                    count = provenance.folderCount,
                    accentColor = Color(0xFF90CAF9),
                    modifier = Modifier.widthIn(max = 240.dp),
                    onClick = {
                        onOpenRelated(Constants.FILTER_TYPE_PATH, provenance.folderPath, "Folder: $fName")
                    }
                )
            }
        }
    }
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
        TagHeaderBadges(track = track)
        TaxonomyChipsRow(
            track = track,
            onOpenRelated = onOpenRelated
        )
        if (provenance != null && onOpenRelated != null) {
            StudioProvenanceSection(
                provenance = provenance,
                onOpenRelated = onOpenRelated
            )
        }
    }
}
