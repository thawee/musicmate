package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R

@Composable
fun ActionFilesDialog(
    title: String,
    titleIconRes: Int,
    state: ActionFilesState,
    okButtonText: String,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    val isBusy by state.isBusy
    val progress by state.progress

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF2161618),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier.padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .padding(top = 10.dp, bottom = 8.dp)
                    .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = titleIconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = R.drawable.round_close_24),
                        contentDescription = "Close",
                        tint = Color(0xFFBDBDBD)
                    )
                }
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                if (isBusy) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Processing...",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(8.dp),
                            color = Color(0xFF80CBC4),
                            trackColor = Color(0x3380CBC4)
                        )
                    }
                }

                Text(
                    text = "Files",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color(0x64000000), RoundedCornerShape(8.dp))
                        .height(240.dp)
                ) {
                    LazyColumn {
                        itemsIndexed(state.tracks) { index, track ->
                            val status = state.statusMap[track] ?: "-"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.simpleName ?: track.title ?: track.path ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = status,
                                        color = if (status.contains("Deleted") || status.contains("Moved")) Color(0xFF80CBC4) else Color(0xFF9E9E9E),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (index < state.tracks.size - 1) {
                                HorizontalDivider(color = Color(0xFF37474F))
                            }
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel, enabled = !isBusy) {
                        Text(text = "Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOk,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF80CBC4),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF37474F),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        Text(
                            text = okButtonText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
