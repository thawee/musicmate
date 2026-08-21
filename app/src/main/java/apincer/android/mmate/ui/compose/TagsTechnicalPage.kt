package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.music.core.codec.FFMPegReader
import apincer.music.core.codec.TagReader
import apincer.music.core.model.Track
import apincer.music.core.repository.FileRepository
import apincer.music.core.utils.ReflectUtil
import apincer.music.core.utils.StringUtils

data class TechField(val name: String, val currentVal: String, val defaultVal: String)

@Composable
fun TagsTechnicalPage(
    track: Track,
    fileRepos: FileRepository
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val musicMatePath = fileRepos.buildCollectionPath(track, true)
    
    // Read tags
    val defaultTrack = track.copy()
    TagReader.readFullTag(context, defaultTrack)
    
    val ffmpegTag = FFMPegReader(context).extractTagFromFile(track.path)
    val ffmpegInfo = ffmpegTag.rawOutput ?: "No Output"

    // Parse fields
    val fields = ReflectUtil.getAllFields(track.javaClass)
    val excludeFields = setOf("path", "simpleName", "audioStartTime", "fileLastModified",
        "isManaged", "id", "uniqueKey", "normalizedArtist", "normalizedTitle", 
        "waveformData", "rawOutput", "storageId", "CREATOR", "originTag")
    
    val parsedFields = fields.filter { !excludeFields.contains(it.name) && !it.name.startsWith("shadow") }
        .map { field ->
            val mateVal = StringUtils.format(ReflectUtil.getFieldValue(field, track), 20, "\n") ?: ""
            val stdVal = StringUtils.format(ReflectUtil.getFieldValue(field, defaultTrack), 20, "\n") ?: ""
            TechField(field.name, mateVal, stdVal)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        TechCard(title = "FILE STORAGE") {
            Text(
                text = "Current Path:\n${track.path}\n\nMusicMate Path:\n$musicMatePath",
                fontSize = 12.sp,
                color = Color.LightGray
            )
        }

        TechCard(title = "EMBEDDED METADATA") {
            MetadataTable(parsedFields)
        }

        TechCard(title = "AUDIO DIAGNOSTICS (FFMPEG)") {
            Text(
                text = ffmpegInfo,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun TechCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x33000000)), // transparentish
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFBB86FC), // Primary
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun MetadataTable(fields: List<TechField>) {
    val horizontalScroll = rememberScrollState()
    
    val headerBgColor = Color(0x33FFFFFF)
    val rowBgColor = Color(0x1AFFFFFF)
    val borderColor = Color.Black

    Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
        // Header
        Row(modifier = Modifier.background(borderColor).padding(bottom = 2.dp)) {
            TableCell("Field", headerBgColor, isHeader = true, width = 120)
            TableCell("Current", headerBgColor, isHeader = true, width = 160)
            TableCell("Default Reader", headerBgColor, isHeader = true, width = 160)
        }

        // Rows
        for (field in fields) {
            val isMatch = field.currentVal == field.defaultVal
            Row(modifier = Modifier.background(borderColor).padding(bottom = 2.dp)) {
                TableCell(field.name, rowBgColor, width = 120, textColor = if (isMatch) Color.White else Color.Red)
                TableCell(field.currentVal, rowBgColor, width = 160)
                TableCell(field.defaultVal, rowBgColor, width = 160)
            }
        }
    }
}

@Composable
fun TableCell(
    text: String,
    bgColor: Color,
    width: Int,
    isHeader: Boolean = false,
    textColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}
