package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.music.core.model.Track
import apincer.android.mmate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatFilesDialog(
    state: FormatFilesState,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    val isBusy by state.isBusy
    val progress by state.progress
    var formatExpanded by remember { mutableStateOf(false) }
    var sampleRateExpanded by remember { mutableStateOf(false) }

    val formatOptions = stringArrayResource(id = R.array.output_formats)
    val sampleRateOptions = stringArrayResource(id = R.array.sample_rates)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF262626))
        ) {
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
                        painter = painterResource(id = R.drawable.rounded_swap_horiz_24),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Format",
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
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text("Processing...", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(8.dp),
                            color = Color(0xFF80CBC4),
                            trackColor = Color(0x3380CBC4)
                        )
                    }
                }

                // Format & Sample Rate Pickers
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { if (!isBusy) formatExpanded = !formatExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = state.selectedFormat.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Output Format", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF80CBC4),
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false },
                            modifier = Modifier.background(Color(0xFF333333))
                        ) {
                            formatOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.White) },
                                    onClick = {
                                        state.selectedFormat.value = option
                                        formatExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = sampleRateExpanded,
                        onExpandedChange = { if (!isBusy) sampleRateExpanded = !sampleRateExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = state.selectedSampleRate.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sample Rate", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sampleRateExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF80CBC4),
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = sampleRateExpanded,
                            onDismissRequest = { sampleRateExpanded = false },
                            modifier = Modifier.background(Color(0xFF333333))
                        ) {
                            sampleRateOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.White) },
                                    onClick = {
                                        state.selectedSampleRate.value = option
                                        sampleRateExpanded = false
                                    }
                                )
                            }
                        }
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
                        .height(200.dp)
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
                                Text("${index + 1}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(24.dp))
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
                                        color = if (status.contains("Done") || status.contains("Success")) Color(0xFF80CBC4) else Color(0xFF9E9E9E),
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
                        Text(text = "Convert", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
