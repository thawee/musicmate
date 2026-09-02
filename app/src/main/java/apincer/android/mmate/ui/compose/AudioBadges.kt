package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontFamily
import apincer.android.mmate.R
import apincer.android.mmate.ui.viewmodel.StudioProvenanceInfo
import apincer.android.mmate.utils.TagUIUtils
import apincer.music.core.Constants
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.TagUtils
import apincer.music.core.utils.ThaiEncodingUtils
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow

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
    val cornerRadius = if (expanded) 8.dp else 6.dp
    val dotSize = if (expanded) 5.dp else 3.5.dp
    val dotSpacing = if (expanded) 5.dp else 3.5.dp
    val textFontSize = if (expanded) 10.5.sp else 9.sp
    val hPadding = if (expanded) 8.dp else 5.dp
    val vPadding = if (expanded) 3.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBase)
            .background(bgTint)
            .border(0.75.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(horizontal = hPadding, vertical = vPadding),
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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

    val bgBase = Color(0xD9101010)
    val bgTint = accentColor.copy(alpha = if (expanded) 0.12f else 0.08f)
    val borderColor = accentColor.copy(alpha = if (expanded) 0.45f else 0.38f)
    val cornerRadius = if (expanded) 8.dp else 6.dp
    val dotSize = if (expanded) 5.dp else 3.5.dp
    val dotSpacing = if (expanded) 5.dp else 3.5.dp
    val textFontSize = if (expanded) 10.5.sp else 9.sp
    val hPadding = if (expanded) 8.dp else 5.dp
    val vPadding = if (expanded) 3.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBase)
            .background(bgTint)
            .border(0.75.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(horizontal = hPadding, vertical = vPadding),
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xD9141414))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = resText,
            color = Color(0xFFDDDDDD),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            softWrap = false
        )
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
private fun StudioCapsule(
    icon: String,
    name: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val countStr = if (count > 0) " • $count" else ""
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD9141414))
            .background(color.copy(alpha = 0.08f))
            .border(0.75.dp, color.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$icon$name$countStr ❯",
                color = Color(0xFFEEEEEE),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        Spacer(modifier = Modifier.height(6.dp))

        // Line 1: Music Provenance (Artist & Album)
        if (hasArtist || hasAlbum) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasArtist) {
                    StudioCapsule(
                        icon = "👤 ",
                        name = provenance.artist,
                        count = provenance.artistCount,
                        color = Color(0xFFFFD700),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ARTIST, provenance.artist, "More by ${provenance.artist}")
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (hasArtist && hasAlbum) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (hasAlbum) {
                    StudioCapsule(
                        icon = "💿 ",
                        name = provenance.album,
                        count = provenance.albumCount,
                        color = Color(0xFF80CBC4),
                        onClick = {
                            onOpenRelated(Constants.FILTER_TYPE_ALBUM, provenance.album, "Album: ${provenance.album}")
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        // Line 2: Storage & Directory Location (Folder)
        if (hasFolder) {
            if (hasArtist || hasAlbum) {
                Spacer(modifier = Modifier.height(4.5.dp))
            }
            val fName = provenance.folderName.ifBlank { "Folder" }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioCapsule(
                    icon = "📁 ",
                    name = fName,
                    count = provenance.folderCount,
                    color = Color(0xFF90CAF9),
                    onClick = {
                        onOpenRelated(Constants.FILTER_TYPE_PATH, provenance.folderPath, "Folder: $fName")
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )
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
        // 1. Batch Mode Indicator (if multiple tracks selected)
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

        // 2. Primary Badges Row (Quality, Resolution, DR, Rating, New)
        TagHeaderBadges(track = track)

        // 3. Studio Provenance & Related Discography Capsules
        if (provenance != null && onOpenRelated != null) {
            StudioProvenanceSection(
                provenance = provenance,
                onOpenRelated = onOpenRelated
            )
        }

        // 4. Musical Taxonomy & Character Pills (Genre, Mood, Style, Origin)
        val tagPills = mutableListOf<Pair<String, String>>()
        if (!track.genre.isNullOrBlank()) tagPills.add("🎸 " to track.genre)
        if (!track.mood.isNullOrBlank()) tagPills.add("🎭 " to track.mood)
        if (!track.style.isNullOrBlank()) tagPills.add("🎨 " to track.style)
        if (!track.origin.isNullOrBlank()) tagPills.add("🌏 " to track.origin)

        if (tagPills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tagPills.take(4).forEachIndexed { idx, (icon, value) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xD9161616))
                            .border(0.5.dp, Color(0x44888888), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$icon$value",
                            color = Color(0xFFDDDDDD),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (idx < tagPills.size - 1 && idx < 3) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        // 5. Technical Telemetry Specs Monospace Strip (Grounding Footer Baseline)
        val metaParts = mutableListOf<String>()
        track.fileType?.let { if (it.isNotBlank()) metaParts.add(it.uppercase()) }
        if (track.audioBitRate > 0) metaParts.add(StringUtils.formatAudioBitRate(track.audioBitRate))
        val ch = track.audioChannels
        if (!ch.isNullOrBlank()) metaParts.add(if (ch == "2" || ch.equals("Stereo", ignoreCase = true)) "Stereo" else "$ch ch")
        if (track.audioDuration > 0) metaParts.add(StringUtils.formatDurationAsMinute(track.audioDuration))
        if (track.fileSize > 0) metaParts.add(StringUtils.formatStorageSize(track.fileSize))

        if (metaParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xD9101010))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = metaParts.joinToString(" • "),
                    color = Color(0xFFCCCCCC),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    softWrap = false
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
    val bgBase = Color(0xD9101010) // 85% deep obsidian glass

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgBase)
            .border(0.75.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
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
