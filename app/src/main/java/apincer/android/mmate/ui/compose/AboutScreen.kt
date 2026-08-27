package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appVersion: String,
    pieEntries: List<PieEntry>,
    storageStatusText: String = "",
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedLegend by remember { mutableStateOf(Constants.LEGEND_HIRES) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About MusicMate",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. App Header Hero ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF22222E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                            contentDescription = "MusicMate Logo",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "MusicMate Audiophile Player",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version $appVersion",
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "High-Resolution Audio Engine & Tag Master",
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // ── 2. Library Quality Breakdown Donut Chart ────────────────────
            if (pieEntries.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF16161E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "LIBRARY QUALITY DISTRIBUTION",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        QualityPieChart(
                            entries = pieEntries,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                }
            }

            // ── 3. Audio Format & Quality Encyclopedia ──────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "AUDIO QUALITY TIERS",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Tier Selectors
                    val tiers = listOf(
                        Constants.LEGEND_HIRES to "Hi-Res",
                        Constants.LEGEND_DSD to "DSD",
                        Constants.LEGEND_STUDIO to "Studio",
                        Constants.LEGEND_CD to "CD Quality",
                        Constants.LEGEND_MQA to "MQA",
                        Constants.LEGEND_LOSSY to "Standard"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tiers.take(3).forEach { (key, label) ->
                            TierPill(
                                label = label,
                                isSelected = selectedLegend == key,
                                onClick = { selectedLegend = key },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tiers.drop(3).forEach { (key, label) ->
                            TierPill(
                                label = label,
                                isSelected = selectedLegend == key,
                                onClick = { selectedLegend = key },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Selected Tier Specs Box
                    val tierDetails = getTierDetails(selectedLegend)
                    Surface(
                        color = Color(0xFF0F0F14),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = tierDetails.title,
                                color = tierDetails.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tierDetails.description,
                                color = Color(0xFFDDDDDD),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Formats: ${tierDetails.formats}",
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Resolution: ${tierDetails.resolution}",
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0x33FFB300) else Color(0xFF1F1F28))
            .border(
                1.dp,
                if (isSelected) Color(0xFFFFB300) else Color(0x1FFFFFFF),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFFFB300) else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

private data class TierInfo(
    val title: String,
    val description: String,
    val formats: String,
    val resolution: String,
    val accentColor: Color
)

private fun getTierDetails(key: String): TierInfo {
    return when (key) {
        Constants.LEGEND_HIRES -> TierInfo(
            title = "Hi-Res Studio Master (24-bit / 96kHz+)",
            description = "Uncompressed or losslessly compressed audio exceeding standard CD sampling frequency and dynamic bit depth.",
            formats = "FLAC, ALAC, WAV, AIFF",
            resolution = "24-bit / 96.0 kHz up to 192.0 kHz (4608 kbps)",
            accentColor = Color(0xFFFFD700)
        )
        Constants.LEGEND_DSD -> TierInfo(
            title = "Direct Stream Digital (DSD / SACD)",
            description = "1-bit Sigma-Delta modulation offering extreme time-domain precision and ultra-wide frequency response.",
            formats = "DSF, DFF, ISO, DSDRAW",
            resolution = "1-bit / 2.8224 MHz (DSD64) up to 22.5792 MHz (DSD512)",
            accentColor = Color(0xFF00E5FF)
        )
        Constants.LEGEND_STUDIO -> TierInfo(
            title = "Studio Quality (24-bit / 44.1 - 48kHz)",
            description = "24-bit depth delivering 144 dB of dynamic range, capturing full studio recording headroom.",
            formats = "FLAC, ALAC, WAV",
            resolution = "24-bit / 44.1 kHz, 48.0 kHz (2304 kbps)",
            accentColor = Color(0xFFFFC107)
        )
        Constants.LEGEND_CD -> TierInfo(
            title = "Redbook CD Quality (16-bit / 44.1kHz)",
            description = "Bit-perfect lossless standard matching physical Compact Disc digital audio without compression artifacts.",
            formats = "FLAC, ALAC, APE, WAV",
            resolution = "16-bit / 44.1 kHz Lossless (1411 kbps)",
            accentColor = Color(0xFF64B5F6)
        )
        Constants.LEGEND_MQA -> TierInfo(
            title = "Master Quality Authenticated (MQA)",
            description = "Hierarchical time-domain origami audio encapsulation with hardware unfolding support.",
            formats = "MQA-FLAC, MQA-Studio",
            resolution = "24-bit / 48 kHz (Origami Folded to 192/384 kHz)",
            accentColor = Color(0xFFE040FB)
        )
        else -> TierInfo(
            title = "Standard Quality (Lossy Compression)",
            description = "Perceptually encoded audio saving storage while maintaining everyday listening quality.",
            formats = "MP3, AAC, OGG, OPUS, M4A",
            resolution = "128 kbps to 320 kbps CBR/VBR",
            accentColor = Color(0xFF9E9E9E)
        )
    }
}
