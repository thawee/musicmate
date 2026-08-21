package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.utils.TagUIUtils
import apincer.music.core.model.Track
import apincer.music.core.utils.TagUtils

@Composable
fun DynamicRangeMeter(
    track: Track?,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Text(
            text = "-",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        
        Spacer(modifier = Modifier.width(4.dp))

        // Draw meter bar
        Canvas(modifier = Modifier.size(width = 30.dp, height = 6.dp)) {
            val maxScore = 20f
            val normalized = (scoreVal.coerceIn(0, 20) / maxScore).coerceIn(0f, 1f)
            
            // Background track
            drawRoundRect(
                color = Color(0xFF333333),
                size = size,
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            
            // Filled track
            if (normalized > 0f) {
                drawRoundRect(
                    color = drsColor,
                    size = Size(width = size.width * normalized, height = size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}
