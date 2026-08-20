package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val context = LocalContext.current
    var label = track?.qualityInd ?: "-"
    if (label.isEmpty()) label = "-"
    else if (label.startsWith("MQA")) label = "MQA"

    val textColor = Color(TagUIUtils.getQualityTextColor(context, label))
    val bgColor = Color(TagUIUtils.getQualityBgColor(context, label))

    Box(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun QualityBadge(labelStr: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var label = labelStr ?: "-"
    if (label.isEmpty()) label = "-"
    else if (label.startsWith("MQA")) label = "MQA"

    val textColor = Color(TagUIUtils.getQualityTextColor(context, label))
    val bgColor = Color(TagUIUtils.getQualityBgColor(context, label))

    Box(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
fun NewBadge(track: Track?, modifier: Modifier = Modifier) {
    if (track == null || track.isManaged) return

    val isDownload = TagUtils.isOnDownloadDir(track)
    val textColor = if (isDownload) colorResource(R.color.new_download_indicator_text) else colorResource(R.color.new_indicator_text)
    val bgColor = if (isDownload) colorResource(R.color.new_download_indicator_background) else colorResource(R.color.new_indicator_background)

    Row(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "NEW",
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
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
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
