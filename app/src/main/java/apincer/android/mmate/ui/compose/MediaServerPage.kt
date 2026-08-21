package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import apincer.android.mmate.R

@Composable
fun MediaServerPage(
    state: MediaServerState,
    onEngineChanged: (String) -> Unit,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onCopyUrlClicked: () -> Unit,
    onOpenUrlClicked: () -> Unit,
    onQrCodeClicked: () -> Unit
) {
    var showQrZoomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. HERO STATUS & POWER CONTROL CARD ──────────────────────────────
        Surface(
            color = Color(0xFF1B1B24),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status info & network indicator
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (state.isServerRunning) Color(0x3300E676) else Color(0x33FF5252),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (state.isServerRunning) Color(0xFF00E676) else Color(0xFFFF5252),
                                    shape = CircleShape
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (state.isServerRunning) state.serverStatusText else "Server Stopped",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.isServerRunning) {
                                if (state.broadcastInfo.isNotEmpty()) state.broadcastInfo else "DLNA 1.5 • Port 9000"
                            } else if (state.isNetworkAvailable) {
                                "Ready to stream"
                            } else {
                                "Wi-Fi / Hotspot disconnected"
                            },
                            color = if (state.isServerRunning) Color(0xFFB0B0B0) else if (state.isNetworkAvailable) Color(0xFF888888) else Color(0xFFFFAB91),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Prominent Power Button
                if (state.isServerRunning) {
                    Button(
                        onClick = onStopClicked,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x2BFF5252),
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF5252)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_round_power_settings_new_24),
                            contentDescription = "Stop",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stop",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onStartClicked,
                        enabled = state.isNetworkAvailable,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF2C2C2C),
                            disabledContentColor = Color(0xFF666666)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_round_power_settings_new_24),
                            contentDescription = "Start",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── 2. WEBUI ENDPOINT & DLNA ACCESS (When Running) ────────────────────
        if (state.isServerRunning) {
            Surface(
                color = Color(0xFF14141A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header with badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Web Interface & Remote",
                                color = Color(0xFFE0E0E0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "PORT 9000",
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x22FFB300), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Body: URL & actions + QR Code thumbnail
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            // URL Monospace box
                            Surface(
                                color = Color(0xFF0C0C10),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCopyUrlClicked() }
                            ) {
                                Text(
                                    text = state.serverUrl,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenUrlClicked,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0x1AFFFFFF),
                                        contentColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Open WebUI",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                OutlinedButton(
                                    onClick = onCopyUrlClicked,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0x1AFFFFFF),
                                        contentColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_round_content_copy_24),
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // QR Code thumbnail
                        if (state.qrCodeBitmap != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        showQrZoomDialog = true
                                        onQrCodeClicked()
                                    }
                            ) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Image(
                                        bitmap = state.qrCodeBitmap!!.asImageBitmap(),
                                        contentDescription = "Scan QR Code",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Tap to enlarge",
                                    color = Color(0xFF888888),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ── 2B. OFFLINE CAPABILITY BANNER (When Stopped) ──────────────────
            Surface(
                color = Color(0xFF14141A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_cast_24),
                        contentDescription = null,
                        tint = if (state.isNetworkAvailable) Color(0xFFFFB300) else Color(0xFF888888),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isNetworkAvailable) "Local DLNA & WebUI Streaming" else "Network Unavailable",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.isNetworkAvailable) {
                                "Start server to stream to DLNA renderers and access playback controls from any browser on your Wi-Fi network."
                            } else {
                                "Connect to Wi-Fi or turn on Hotspot to enable the local media server."
                            },
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // ── 3. STREAMING ENGINE SELECTOR ──────────────────────────────────────
        Surface(
            color = Color(0xFF14141A),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STREAMING ENGINE",
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "ACTIVE ARCHITECTURE",
                        color = Color(0xFF666666),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Segmented Switcher (No text truncation!)
                Surface(
                    color = Color(0xFF0C0C10),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EngineSegment(
                            id = "nio",
                            label = "SonicNIO",
                            isSelected = state.currentEngine == "nio",
                            onSelect = onEngineChanged,
                            modifier = Modifier.weight(1f)
                        )
                        EngineSegment(
                            id = "httpcore",
                            label = "CoreHTTP",
                            isSelected = state.currentEngine == "httpcore",
                            onSelect = onEngineChanged,
                            modifier = Modifier.weight(1f)
                        )
                        EngineSegment(
                            id = "netty",
                            label = "Netty",
                            isSelected = state.currentEngine == "netty",
                            onSelect = onEngineChanged,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Engine Feature Specs
                Surface(
                    color = Color(0x0FFFFFFF),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.engineDescription,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // ── 4. ZOOMABLE QR CODE DIALOG ───────────────────────────────────────────
    if (showQrZoomDialog && state.qrCodeBitmap != null) {
        Dialog(onDismissRequest = { showQrZoomDialog = false }) {
            Surface(
                color = Color(0xFF1E1E28),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan to Open WebUI",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan with your phone or tablet camera to control playback remotely",
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(200.dp)
                    ) {
                        Image(
                            bitmap = state.qrCodeBitmap!!.asImageBitmap(),
                            contentDescription = "WebUI QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = Color(0xFF0C0C10),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.serverUrl,
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onCopyUrlClicked()
                                showQrZoomDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Copy Link", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showQrZoomDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB300),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSegment(
    id: String,
    label: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0x33FFB300) else Color.Transparent)
            .border(
                if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                else androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect(id) }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFFFB300) else Color(0xFF9E9E9E),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
