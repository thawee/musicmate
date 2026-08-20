package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (state.isServerRunning) Color(0xFF69F0AE) else Color(0xFFFF5252),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.serverStatusText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                if (state.isServerRunning || !state.isNetworkAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.serverUrl,
                            color = Color(0xFFE0E0E0),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        )
                        if (state.isServerRunning) {
                            IconButton(
                                onClick = onCopyUrlClicked,
                                modifier = Modifier.size(28.dp).padding(start = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_file_24),
                                    contentDescription = "Copy URL",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onOpenUrlClicked,
                                modifier = Modifier.size(28.dp).padding(start = 2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_open_in_new_24),
                                    contentDescription = "Open in Browser",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (state.isServerRunning && state.broadcastInfo.isNotEmpty()) {
                    Text(
                        text = state.broadcastInfo,
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (state.isServerRunning && state.qrCodeBitmap != null) {
                Image(
                    bitmap = state.qrCodeBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(76.dp)
                        .padding(start = 8.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(5.dp)
                        .clickable(onClick = onQrCodeClicked)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Streaming Engine",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9E9E9E)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            EngineButton("nio", "SonicNIO", state.currentEngine == "nio", onEngineChanged, Modifier.weight(1f))
            EngineButton("httpcore", "CoreHTTP", state.currentEngine == "httpcore", onEngineChanged, Modifier.weight(1f))
            EngineButton("netty", "Netty", state.currentEngine == "netty", onEngineChanged, Modifier.weight(1f))
        }

        Text(
            text = state.engineDescription,
            color = Color(0xFFBDBDBD),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0x33FFFFFF))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isServerRunning) {
                Button(
                    onClick = onStopClicked,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x26FF5252),
                        contentColor = Color(0xFFFF5252)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFF5252))
                ) {
                    Text(text = stringResource(id = R.string.button_stop_server))
                }
            } else {
                Button(
                    onClick = onStartClicked,
                    modifier = Modifier.weight(1f),
                    enabled = state.isNetworkAvailable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF37474F),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text(text = stringResource(id = R.string.button_start_server), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EngineButton(
    engineId: String,
    label: String,
    isSelected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = { onClick(engineId) },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0x33FFB300) else Color.Transparent,
            contentColor = if (isSelected) Color(0xFFFFB300) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFFFB300) else Color(0x4DFFFFFF)
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}
