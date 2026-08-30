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
import apincer.android.mmate.utils.TagUIUtils
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.TagUtils
import apincer.music.core.utils.ThaiEncodingUtils

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
        QualityBadge(track = track)
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
fun TagPreviewHeader(
    track: Track?,
    itemCount: Int = 1,
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

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Lossless Telemetry Specs Monospace Strip
        val metaParts = mutableListOf<String>()
        track.fileType?.let { if (it.isNotBlank()) metaParts.add(it.uppercase()) }
        //val bitDepth = StringUtils.formatAudioBitsDepth(track.audioBitsDepth)
        //if (bitDepth.isNotBlank()) metaParts.add(bitDepth)
        //val sampleRate = StringUtils.formatAudioSampleRate(track.audioSampleRate, true)
        //if (sampleRate.isNotBlank()) metaParts.add(sampleRate)
        if (track.audioBitRate > 0) metaParts.add(StringUtils.formatAudioBitRate(track.audioBitRate))
        val ch = track.audioChannels
        if (!ch.isNullOrBlank()) metaParts.add(if (ch == "2" || ch.equals("Stereo", ignoreCase = true)) "Stereo" else "$ch ch")
        if (track.audioDuration > 0) metaParts.add(StringUtils.formatDurationAsMinute(track.audioDuration))
        if (track.fileSize > 0) metaParts.add(StringUtils.formatStorageSize(track.fileSize))

        if (metaParts.isNotEmpty()) {
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

        // 4. Interactive Tag Pills Row (Origin, Genre, Mood, Style)
        val tagPills = mutableListOf<Pair<String, String>>()
        if (!track.origin.isNullOrBlank()) tagPills.add("Origin" to track.origin)
        if (!track.genre.isNullOrBlank()) tagPills.add("Genre" to track.genre)
        if (!track.mood.isNullOrBlank()) tagPills.add("Mood" to track.mood)
        if (!track.style.isNullOrBlank()) tagPills.add("Style" to track.style)

        if (tagPills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tagPills.take(4).forEach { (_, value) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xD9161616))
                            .border(0.5.dp, Color(0x44888888), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = value,
                            color = Color(0xFFEEEEEE),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 5. Contextual Quick-Fix Suggestion Chips (if anomalies or missing tags detected)
        val quickFixes = mutableListOf<Pair<String, String>>()
        val hasGarbledThai = listOf(track.title, track.artist, track.album, track.genre, track.composer)
            .any { ThaiEncodingUtils.isGarbledThai(it) }
        if (hasGarbledThai) {
            quickFixes.add("thai_fix" to "🇹🇭 Fix Thai Encoding")
        }
        val isMissingTags = track.title.isNullOrBlank() || track.artist.isNullOrBlank() || track.album.isNullOrBlank()
        if (isMissingTags) {
            quickFixes.add("auto_tag" to "⚡ Auto-Tag")
        }
        if (track.dynamicRange <= 0 && track.audioBitsDepth >= 16) {
            quickFixes.add("spectrum" to "🎼 Lossless Verifier")
        }

        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        if (quickFixes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickFixes.forEach { (actionId, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x3364B5F6))
                            .border(0.75.dp, Color(0x7764B5F6), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onQuickFixClick?.invoke(actionId)
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color(0xFF90CAF9),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
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
