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
import apincer.android.mmate.R
import apincer.android.mmate.utils.TagUIUtils
import apincer.music.core.model.Track
import apincer.music.core.utils.TagUtils

@Composable
fun QualityBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null) return
    var label = track.qualityInd ?: ""
    if (label.isEmpty() || label == "-") {
        label = TagUtils.getQualityIndicator(track)
    }
    if (label.isEmpty()) label = "-"
    else if (label.startsWith("MQA")) label = "MQA"

    val accentColor = when {
        TagUtils.isDSD(track) -> Color(0xFF00E5FF)
        TagUtils.isHiRes(track) || TagUtils.isPCM24Bits(track) -> Color(0xFFFFD700)
        TagUtils.isMQA(track) -> Color(0xFFE040FB)
        TagUtils.isLossless(track) -> Color(0xFF64B5F6)
        else -> Color(0xFF9E9E9E)
    }

    val bgBase = Color(0xD9101010)
    val bgTint = accentColor.copy(alpha = 0.08f)
    val borderColor = accentColor.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgBase)
            .background(bgTint)
            .border(0.75.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
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
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun QualityBadge(labelStr: String?, modifier: Modifier = Modifier) {
    var label = labelStr ?: "-"
    if (label.isEmpty()) label = "-"
    else if (label.startsWith("MQA")) label = "MQA"

    val accentColor = when {
        label.contains("DSD", ignoreCase = true) -> Color(0xFF00E5FF)
        label.contains("Hi-Res", ignoreCase = true) || label.contains("Studio", ignoreCase = true) || label.contains("HR", ignoreCase = true) || label.contains("24-BIT", ignoreCase = true) -> Color(0xFFFFD700)
        label.contains("MQA", ignoreCase = true) -> Color(0xFFE040FB)
        label.contains("CD", ignoreCase = true) || label.contains("Lossless", ignoreCase = true) -> Color(0xFF64B5F6)
        else -> Color(0xFF9E9E9E)
    }

    val bgBase = Color(0xD9101010)
    val bgTint = accentColor.copy(alpha = 0.08f)
    val borderColor = accentColor.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgBase)
            .background(bgTint)
            .border(0.75.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
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
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 0.2.sp,
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
