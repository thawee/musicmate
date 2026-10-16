package apincer.android.mmate.ui.compose

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R

@Composable
fun MusicFoldersDialog(
    initialDirectories: List<String>,
    defaultPaths: Set<String>,
    storageIds: List<String>,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onScan: (Boolean, List<String>) -> Unit,
    onAddStorage: (String, (String) -> Unit) -> Unit,
    folderGrants: FolderGrants? = null
) {
    var isDeepAudit by remember { mutableStateOf(false) }
    // Bumped after the picker returns, so rows check their access again
    var grantCheck by remember { mutableIntStateOf(0) }
    val dirs = remember { mutableStateListOf(*initialDirectories.distinct().toTypedArray()) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "folders_dialog_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "folders_dialog_alpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        // Opaque: song titles behind the dialog showed through the translucent panel
        color = Color(0xFF161618),
        border = androidx.compose.foundation.BorderStroke(1.dp, MusicMateDesignTokens.Surfaces.BorderPrimary),
        modifier = Modifier
            .padding(12.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
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
                        painter = painterResource(id = R.drawable.rounded_label_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Discover Music Folders",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monitored Folders",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9E9E9E)
                    )
                    Row {
                        storageIds.forEach { sid ->
                            Button(
                                onClick = {
                                    onAddStorage(sid) { directory ->
                                        if (directory !in dirs) dirs.add(directory)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x26FFFFFF),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .height(34.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_folder_24),
                                    contentDescription = null,
                                    tint = MusicMateDesignTokens.Brand.Gold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = addFolderLabel(sid, storageIds.size),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (folderGrants != null) {
                    Text(
                        text = stringResource(id = R.string.directories_grant_explanation),
                        fontSize = 12.sp,
                        color = MusicMateDesignTokens.Text.Muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (dirs.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.directories_empty),
                        fontSize = 13.sp,
                        color = MusicMateDesignTokens.Text.Secondary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MusicMateDesignTokens.Surfaces.Surface, RoundedCornerShape(8.dp))
                        .heightIn(max = 250.dp)
                ) {
                    LazyColumn {
                        itemsIndexed(dirs, key = { _, dir -> dir }) { index, dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = readableFolderPath(dir),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { contentDescription = dir }
                                )
                                val needsAccess = folderGrants != null &&
                                    !remember(dir, grantCheck) { folderGrants.isGranted(dir) }
                                if (needsAccess) {
                                    TextButton(
                                        onClick = {
                                            folderGrants!!.requestAccess(dir) { picked ->
                                                // A different folder than this row's is added as its own row
                                                if (!coversFolder(picked, dir) && picked !in dirs) dirs.add(picked)
                                                grantCheck++
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.directories_allow_access),
                                            color = MusicMateDesignTokens.Brand.Gold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                if (defaultPaths.contains(dir)) {
                                    // Default folders cannot be removed; say so instead of hiding the action
                                    Text(
                                        text = "Default",
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = { dirs.remove(dir) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.rounded_delete_24),
                                            contentDescription = "Remove",
                                            tint = Color(0xFFEF5350), // Red 400
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (index < dirs.size - 1) {
                                HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { isDeepAudit = !isDeepAudit }
                ) {
                    Checkbox(
                        checked = isDeepAudit,
                        onCheckedChange = { isDeepAudit = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MusicMateDesignTokens.Brand.Gold,
                            checkmarkColor = Color.Black
                        )
                    )
                    Text(
                        text = stringResource(id = R.string.directories_deep_audit),
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp
                    )
                }

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = "Cancel", color = Color(0xFFBDBDBD))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onScan(isDeepAudit, dirs.toList()) },
                        enabled = dirs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MusicMateDesignTokens.Brand.Gold,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.directories_execute_scan),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** How the Play build checks and asks for access to a folder; the GitHub build has all-files access. */
class FolderGrants(
    val isGranted: (String) -> Boolean,
    /** Opens the system folder picker at the folder and reports the folder the user granted. */
    val requestAccess: (String, (String) -> Unit) -> Unit
)

/** True when [root] is [path] or one of its parent folders. */
internal fun coversFolder(root: String, path: String): Boolean =
    path == root || path.startsWith(root.trimEnd('/') + "/")

/** "Add folder" when there is one storage volume; otherwise name the volume. */
internal fun addFolderLabel(storageId: String, storageCount: Int): String = when {
    storageCount <= 1 -> "Add folder"
    storageId.equals("primary", ignoreCase = true) -> "Internal"
    else -> "SD card"
}

/** "/storage/emulated/0/Music" -> "Internal storage › Music"; "/storage/1234-ABCD/x" -> "SD card › x". */
internal fun readableFolderPath(path: String): String {
    val internal = Regex("^/storage/emulated/\\d+/?")
    val removable = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/?")
    val (volume, rest) = when {
        internal.containsMatchIn(path) -> "Internal storage" to path.replaceFirst(internal, "")
        removable.containsMatchIn(path) -> "SD card" to path.replaceFirst(removable, "")
        else -> return path
    }
    val parts = rest.trim('/').split('/').filter { it.isNotEmpty() }
    return if (parts.isEmpty()) volume else (listOf(volume) + parts).joinToString(" › ")
}
