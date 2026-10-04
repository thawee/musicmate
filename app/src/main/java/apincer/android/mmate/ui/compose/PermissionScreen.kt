package apincer.android.mmate.ui.compose

import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R

@Composable
fun PermissionScreen(
    systemAccess: SystemAccessState,
    focusedCapability: SystemAccessCapability = SystemAccessCapability.NONE,
    onStorageAccessClick: () -> Unit,
    onExternalPlayerAccessClick: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Surface(
        color = Color(0xFF0A0A0E),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // A visible way back: the screen previously offered only Android settings links
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start).size(48.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {

                // Hero Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFB300))
                        .border(1.5.dp, Color(0xFFFFB300), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.system_access_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.system_access_intro),
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Permission Cards
                PermissionItem(
                    iconRes = R.drawable.round_sd_storage_24,
                    title = stringResource(R.string.permission_storage_title),
                    requirementText = stringResource(R.string.permission_required),
                    desc = stringResource(R.string.permission_storage_description),
                    granted = systemAccess.hasFullStorageAccess,
                    actionText = stringResource(R.string.action_open_storage_settings),
                    focused = focusedCapability == SystemAccessCapability.STORAGE,
                    onClick = onStorageAccessClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionItem(
                    iconRes = R.drawable.ic_round_notification_add_24,
                    title = stringResource(R.string.permission_external_player_title),
                    requirementText = stringResource(R.string.permission_optional),
                    desc = stringResource(R.string.permission_external_player_description),
                    granted = systemAccess.hasExternalPlayerAccess,
                    actionText = stringResource(R.string.action_enable_external_player_access),
                    focused = focusedCapability == SystemAccessCapability.EXTERNAL_PLAYERS,
                    onClick = onExternalPlayerAccessClick
                )

                Spacer(modifier = Modifier.height(24.dp))
                // Continue once the required access is granted; otherwise allow leaving for now
                if (systemAccess.hasFullStorageAccess) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Continue", fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Not now", color = Color(0xFFBDBDBD))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    iconRes: Int,
    title: String,
    requirementText: String,
    desc: String,
    granted: Boolean,
    actionText: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF16161F),
        border = androidx.compose.foundation.BorderStroke(
            if (focused) 1.5.dp else 1.dp,
            if (focused) Color(0xFFFFB300) else Color(0x1AFFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22222E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = if (granted) {
                                    stringResource(R.string.permission_granted)
                                } else {
                                    requirementText
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = if (granted) Color(0xFF63D890) else Color(0xFFFFB300),
                            disabledContainerColor = Color.Transparent
                        ),
                        border = null,
                        modifier = Modifier.heightIn(min = 24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = desc,
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionText,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
