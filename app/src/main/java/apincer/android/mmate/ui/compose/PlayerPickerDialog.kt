package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import apincer.android.mmate.R
import apincer.music.core.playback.spi.PlaybackTarget

enum class PlayerCategory {
    NETWORK_STREAMER,
    THIS_DEVICE,
    MUSIC_APP
}

data class PlayerTargetItem @JvmOverloads constructor(
    val target: PlaybackTarget,
    val title: String,
    val subtitle: String = "",
    val iconResId: Int = R.drawable.rounded_music_cast_24,
    val isSelected: Boolean = false,
    val isStreaming: Boolean = false,
    val appPackageName: String? = null,
    val category: PlayerCategory = PlayerCategory.THIS_DEVICE
)

@Composable
fun PlayerPickerDialog(
    targets: List<PlayerTargetItem>,
    isScanning: Boolean,
    onDismissRequest: () -> Unit,
    onTargetSelected: (PlaybackTarget) -> Unit,
    onRescanClick: () -> Unit,
    onBluetoothOutputClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "player_dialog_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "player_dialog_alpha"
    )

    val streamers = remember(targets) {
        targets.filter { it.category == PlayerCategory.NETWORK_STREAMER }
            .sortedWith(compareByDescending<PlayerTargetItem> { it.isSelected }.thenBy { it.title })
    }
    val localOutputs = remember(targets) {
        targets.filter { it.category == PlayerCategory.THIS_DEVICE }
            .sortedWith(compareByDescending<PlayerTargetItem> { it.isSelected }.thenBy { it.title })
    }
    val musicApps = remember(targets) {
        targets.filter { it.category == PlayerCategory.MUSIC_APP }
            .sortedWith(compareByDescending<PlayerTargetItem> { it.isSelected }.thenBy { it.title })
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp), // DESIGN.md §8A: 24dp rounded corners
            color = Color(0xF2161618),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row (DESIGN.md §8A)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_music_cast_24),
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio Output Picker",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.round_close_24),
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = Color(0x22FFFFFF),
                    thickness = 0.75.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Categorized Player Target Items
                if (targets.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (streamers.isNotEmpty()) {
                            item(key = "hdr_streamers") {
                                CategoryHeader("NETWORK STREAMERS")
                            }
                            items(streamers, key = { "streamer_" + it.target.targetId }) { item ->
                                PlayerTargetRow(item = item, onTargetSelected = onTargetSelected)
                            }
                        }

                        if (localOutputs.isNotEmpty()) {
                            item(key = "hdr_local") {
                                CategoryHeader("THIS DEVICE")
                            }
                            items(localOutputs, key = { "local_" + it.target.targetId }) { item ->
                                PlayerTargetRow(item = item, onTargetSelected = onTargetSelected)
                            }
                        }

                        if (musicApps.isNotEmpty()) {
                            item(key = "hdr_apps") {
                                CategoryHeader("INSTALLED MUSIC APPS")
                            }
                            items(musicApps, key = { "app_" + it.target.targetId }) { item ->
                                PlayerTargetRow(item = item, onTargetSelected = onTargetSelected)
                            }
                        }
                    }
                } else {
                    // Empty Scanning Placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Scanning for audio output devices…",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(
                    color = Color(0x22FFFFFF),
                    thickness = 0.75.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Group 1: Utilities (Rescan & Bluetooth / System Output)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Rescan Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onRescanClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                            contentDescription = "Rescan",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isScanning) "Rescanning DLNA players…" else "Rescan for DLNA players",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isScanning) {
                            Spacer(modifier = Modifier.weight(1f))
                            val transition = rememberInfiniteTransition(label = "rescan_pulse")
                            val alpha by transition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "rescan_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD700))
                                    .alpha(alpha)
                            )
                        }
                    }

                    // Bluetooth / System Output Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBluetoothOutputClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_round_bluetooth_audio_24),
                            contentDescription = "System Output",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bluetooth / System Output…",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Open Android media output settings",
                                color = Color(0x77FFFFFF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        color = Color(0x88FFFFFF),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun PlayerTargetRow(
    item: PlayerTargetItem,
    onTargetSelected: (PlaybackTarget) -> Unit
) {
    val cardBg = if (item.isSelected) Color(0x2BFFD700) else Color(0x14FFFFFF)
    val cardBorder = if (item.isSelected) Color(0x80FFD700) else Color(0x22FFFFFF)
    val iconTint = if (item.isSelected) Color(0xFFFFD700) else if (item.isStreaming) Color(0xFF00E5FF) else Color(0xFFB0BEC5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(0.75.dp, cardBorder, RoundedCornerShape(14.dp))
            .clickable { onTargetSelected(item.target) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTargetIcon(
            item = item,
            iconTint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotEmpty()) {
                Text(
                    text = item.subtitle,
                    color = if (item.isSelected) Color(0xFFFFD700).copy(alpha = 0.85f) else Color(0x99FFFFFF),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "✓",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val appIconCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(32)

@Composable
private fun PlayerTargetIcon(
    item: PlayerTargetItem,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIconBitmap = remember(item.appPackageName) {
        val pkg = item.appPackageName
        if (!pkg.isNullOrEmpty()) {
            appIconCache.get(pkg) ?: run {
                try {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(pkg)
                    val bmp = drawableToBitmap(drawable, 72, 72).asImageBitmap()
                    appIconCache.put(pkg, bmp)
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }

    if (appIconBitmap != null) {
        Image(
            bitmap = appIconBitmap,
            contentDescription = item.title,
            modifier = modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Icon(
            painter = painterResource(id = item.iconResId),
            contentDescription = null,
            tint = iconTint,
            modifier = modifier.size(24.dp)
        )
    }
}

private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else width
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else height
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
