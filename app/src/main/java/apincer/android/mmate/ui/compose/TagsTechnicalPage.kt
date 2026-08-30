package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

data class CoverArtInfo(
    val exists: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val sizeKb: Long = 0,
    val filePath: String = ""
)

data class TechData(
    val ffmpegInfo: String = "",
    val coverArtInfo: CoverArtInfo = CoverArtInfo(),
    val parsedFields: List<TechField> = emptyList()
)

@Composable
fun TagsTechnicalPage(
    track: Track,
    fileRepos: FileRepository
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val musicMatePath = fileRepos.buildCollectionPath(track, true)
    
    // Read tags asynchronously on IO thread to prevent UI frame drops
    val techData by androidx.compose.runtime.produceState(
        initialValue = TechData("Loading technical diagnostics...", CoverArtInfo(), emptyList()),
        key1 = track.path
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val defaultTrack = track.copy()
            TagReader.readFullTag(context, defaultTrack)
            
            val ffmpegTag = try {
                FFMPegReader(context).extractTagFromFile(track.path)
            } catch (e: Exception) {
                null
            }
            val ffmpegInfo = ffmpegTag?.rawOutput ?: "No Output"

            // Artwork Inspection
            val coverArtFile = FileRepository.getCoverArt(context, track)
            val coverArtInfo = if (coverArtFile != null && coverArtFile.exists()) {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(coverArtFile.absolutePath, options)
                CoverArtInfo(
                    exists = true,
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType ?: "image/jpeg",
                    sizeKb = coverArtFile.length() / 1024,
                    filePath = coverArtFile.absolutePath
                )
            } else {
                CoverArtInfo(exists = false)
            }

            // Parse fields via reflection
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

            TechData(ffmpegInfo, coverArtInfo, parsedFields)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        TechCard(title = "METADATA HEALTH AUDITOR") {
            MetadataHealthCard(track = track, coverArtInfo = techData.coverArtInfo)
        }

        TechCard(title = "EMBEDDED COVER ART INSPECTOR") {
            CoverArtInspector(info = techData.coverArtInfo)
        }

        TechCard(title = "FILE STORAGE") {
            Text(
                text = "Current Path:\n${track.path}\n\nMusicMate Path:\n$musicMatePath",
                fontSize = 12.sp,
                color = Color.LightGray
            )
        }

        TechCard(title = "EMBEDDED METADATA") {
            MetadataTable(techData.parsedFields)
        }

        TechCard(title = "AUDIO DIAGNOSTICS (FFMPEG)") {
            Text(
                text = techData.ffmpegInfo,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
fun MetadataHealthCard(track: Track, coverArtInfo: CoverArtInfo) {
    val hasTitle = !track.title.isNullOrBlank()
    val hasArtist = !track.artist.isNullOrBlank()
    val hasAlbum = !track.album.isNullOrBlank()
    val hasYear = !track.year.isNullOrBlank() && track.year != "0"
    val hasGenre = !track.genre.isNullOrBlank()
    val hasTrackNum = !track.track.isNullOrBlank() && track.track != "0"
    val hasHiResArt = coverArtInfo.exists && coverArtInfo.width >= 500
    val hasLossless = track.audioBitsDepth >= 16 || listOf("flac", "dsf", "dff", "alac", "wav", "aiff").contains(track.fileType?.lowercase())

    val checks = listOf(
        "Title" to hasTitle,
        "Artist" to hasArtist,
        "Album" to hasAlbum,
        "Year" to hasYear,
        "Genre" to hasGenre,
        "Track #" to hasTrackNum,
        "Hi-Res Art" to hasHiResArt,
        "Lossless" to hasLossless
    )

    val passedCount = checks.count { it.second }
    val healthPercent = (passedCount * 100) / checks.size

    val gradeColor = when {
        healthPercent >= 90 -> Color(0xFF66BB6A) // Green
        healthPercent >= 65 -> Color(0xFFFFCA28) // Gold
        else -> Color(0xFFEF5350) // Red/Orange
    }

    val gradeLabel = when {
        healthPercent == 100 -> "STUDIO MASTER COMPLETE"
        healthPercent >= 75 -> "HIGH FIDELITY READY"
        healthPercent >= 50 -> "METADATA INCOMPLETE"
        else -> "NEEDS TAG CLEANUP"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = gradeLabel,
                    color = gradeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "$passedCount / ${checks.size} Quality Standards Passed",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .background(gradeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$healthPercent%",
                    color = gradeColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))

        // Grid of check items
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            checks.forEach { (label, passed) ->
                val pillBg = if (passed) Color(0x2266BB6A) else Color(0x22EF5350)
                val pillTextColor = if (passed) Color(0xFF81C784) else Color(0xFFE57373)
                val icon = if (passed) "✓" else "✕"

                Row(
                    modifier = Modifier
                        .background(pillBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "$icon $label",
                        color = pillTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CoverArtInspector(info: CoverArtInfo) {
    if (!info.exists) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x1AFFB74D), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "⚠️ No embedded cover art found in audio file container.",
                color = Color(0xFFFFB74D),
                fontSize = 12.sp
            )
        }
        return
    }

    val isHiRes = info.width >= 1000 && info.height >= 1000
    val qualityTag = if (isHiRes) "Ultra HD Master" else if (info.width >= 500) "Standard HD" else "Low-Res"
    val qualityColor = if (isHiRes) Color(0xFF66BB6A) else if (info.width >= 500) Color(0xFFFFCA28) else Color(0xFFEF5350)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "${info.width} × ${info.height} px",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .background(qualityColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = qualityTag,
                    color = qualityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Format: ${info.mimeType.substringAfterLast("/") .uppercase()}",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Text(
                text = "File Size: ${info.sizeKb} KB",
                color = Color.LightGray,
                fontSize = 12.sp
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
