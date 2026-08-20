package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.music.core.model.Track
import apincer.music.core.utils.TagUtils
import apincer.android.mmate.utils.TagUIUtils
import androidx.compose.ui.platform.LocalContext

@Composable
fun DynamicRangeMeter(
    track: Track?,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Text(
            text = "-",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val drScoreStr = TagUtils.getDynamicRangeScore(track) ?: ""
    val scoreVal = track.drScore.toInt()
    val drsColor = Color(TagUIUtils.getDRScoreColor(context, scoreVal))

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (drScoreStr.isEmpty()) " -- " else "DR$drScoreStr",
            color = drsColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.width(6.dp))

        // Draw meter bar
        Canvas(modifier = Modifier.size(width = 40.dp, height = 8.dp)) {
            val maxScore = 20f
            val normalized = (scoreVal.coerceIn(0, 20) / maxScore).coerceIn(0f, 1f)
            
            // Background track
            drawRoundRect(
                color = Color.DarkGray,
                size = size,
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            
            // Filled track
            drawRoundRect(
                color = drsColor,
                size = Size(width = size.width * normalized, height = size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
