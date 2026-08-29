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
                            text = "Bit-Perfect Streaming, Crafted for the Music You Love",
                            color = Color(0xFFB0B0B0),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── 2. Library Quality Breakdown Donut Chart ────────────────────
            if (pieEntries.isNotEmpty()) {
                val context = LocalContext.current
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ── Share Library Snapshot Button (Viral / Social Promo) ──
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val totalTracks = pieEntries.sumOf { it.value.toInt() }
                                val breakdown = pieEntries.joinToString("\n") { entry ->
                                    val pct = if (totalTracks > 0) (entry.value / totalTracks * 100).toInt() else 0
                                    "  • ${entry.label}: ${entry.value.toInt()} tracks ($pct%)"
                                }
                                val shareText = buildString {
                                    appendLine("🎼 My Audiophile Collection on MusicMate v$appVersion")
                                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    appendLine("📊 Total Tracks: $totalTracks")
                                    appendLine(breakdown)
                                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    appendLine("⚡ Bit-Perfect Streaming, Crafted for the Music You Love")
                                    appendLine("🔗 https://github.com/thawee/musicmate")
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "My MusicMate Collection")
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Library Snapshot"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x2BFFD700),
                                contentColor = Color(0xFFFFD700)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFD700)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_share_24),
                                contentDescription = "Share",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share Library Snapshot",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── 3. Marquee Architectural Superpowers Showcase ────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CORE CAPABILITIES",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    FeatureItem(
                        iconRes = R.drawable.rounded_music_cast_24,
                        title = "UPnP / DLNA Lossless Media Server",
                        desc = "Stream 24-bit/192kHz & DSD bit-perfect across your home network with zero-latency RAM stream pre-caching."
                    )
                    FeatureItem(
                        iconRes = R.drawable.rounded_style_24,
                        title = "Studio Tag Master & Artwork",
                        desc = "Full ID3v2.4, Vorbis, and DSF metadata curation with embedded lossless album art and lyrics."
                    )
                    FeatureItem(
                        iconRes = R.drawable.rounded_equalizer_24,
                        title = "Pure Acoustic Fidelity Engine",
                        desc = "Direct DAC & DMR hardware volume sync, dynamic range metering, and gradual fade sleep timer."
                    )
                }
            }

            // ── 4. The Audiophile Philosophy Manifesto ───────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF12121A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFB300)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "THE AUDIOPHILE PHILOSOPHY",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "“MusicMate is engineered for listeners who believe that sound reproduction should be authentic, bit-perfect, and dynamic — preserving the master studio intent without artificial coloration.”",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // ── 5. Audio Format & Quality Encyclopedia ──────────────────────
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

            // ── 6. Connect & Community ───────────────────────────────────────
            val ctx = LocalContext.current
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CONNECT & SUPPORT",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=apincer.android.mmate"))
                            try { ctx.startActivity(intent) } catch (e: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painterResource(id = R.drawable.ic_baseline_star_24), contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rate on Google Play", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/thawee/musicmate"))
                            try { ctx.startActivity(intent) } catch (e: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painterResource(id = R.drawable.ic_baseline_open_in_new_24), contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitHub Project & Source", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    iconRes: Int,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22222E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = Color(0xFFB0B0B0),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
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
