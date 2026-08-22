package apincer.android.mmate.ui.compose

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import apincer.android.mmate.R
import apincer.android.mmate.ui.MainActivity
import kotlinx.coroutines.launch

// ── App-wide dark colour palette ────────────────────────────────────────────
private val drawerBg        = Color(0xFF121212)
private val drawerSurface   = Color(0xFF1E1E1E)
private val drawerGold      = Color(0xFFFFD700)
private val drawerWhite     = Color.White
private val drawerGray      = Color(0xFFAAAAAA)
private val drawerDivider   = Color(0xFF2C2C2C)
private val drawerSelected  = Color(0xFF2A2A2A)

@Composable
fun MainScaffold(
    activity: MainActivity,
    legacyView: View,
    drawerState: DrawerState,
    activeItemId: Int = R.id.menu_library_all_songs
) {
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            // ── Dark-themed drawer sheet ────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF121212))
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header branding
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                                contentDescription = null,
                                tint = drawerGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MusicMate",
                                color = drawerWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }

                    HorizontalDivider(color = drawerDivider, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Library section ──────────────────────────────────────
                    DrawerSectionHeader("Library")
                    DrawerItem(
                        text = "All Songs",
                        iconResId = R.drawable.rounded_library_music_24,
                        isSelected = (activeItemId == R.id.menu_library_all_songs)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_library_all_songs)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Artists",
                        iconResId = R.drawable.rounded_for_you_24,
                        isSelected = (activeItemId == R.id.menu_tag_artist)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_tag_artist)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Genres",
                        iconResId = R.drawable.rounded_style_24,
                        isSelected = (activeItemId == R.id.menu_tag_genre)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_tag_genre)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Playlists",
                        iconResId = R.drawable.rounded_order_play_24,
                        isSelected = (activeItemId == R.id.menu_collection)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_collection)
                        coroutineScope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // ── Discover section ─────────────────────────────────────
                    DrawerSectionHeader("Discover")
                    DrawerItem(
                        text = "Incoming Tracks",
                        iconResId = R.drawable.rounded_add_diamond_24,
                        isSelected = (activeItemId == R.id.menu_library_recently_added)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_library_recently_added)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Discover Similar",
                        iconResId = R.drawable.rounded_auto_awesome_motion_24,
                        isSelected = (activeItemId == R.id.menu_library_similar_songs)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_library_similar_songs)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Sound Grade",
                        iconResId = R.drawable.rounded_equalizer_24,
                        isSelected = (activeItemId == R.id.menu_sound_grade)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_sound_grade)
                        coroutineScope.launch { drawerState.close() }
                    }

                    DrawerDivider()

                    // ── Configuration section ────────────────────────────────
                    DrawerSectionHeader("Settings & More")
                    DrawerItem(
                        text = "Manage Library",
                        iconResId = R.drawable.rounded_folder_managed_24,
                        isSelected = (activeItemId == R.id.menu_directories)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_directories)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Settings",
                        iconResId = R.drawable.ic_round_settings_24,
                        isSelected = (activeItemId == R.id.menu_settings)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_settings)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Storage Access",
                        iconResId = R.drawable.round_sd_storage_24,
                        isSelected = (activeItemId == R.id.menu_files_permission)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_files_permission)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Notifications",
                        iconResId = R.drawable.ic_round_notification_add_24,
                        isSelected = (activeItemId == R.id.menu_notification_access)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_notification_access)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "Diagnostics",
                        iconResId = R.drawable.rounded_bug_report_24,
                        isSelected = (activeItemId == R.id.menu_about_crash)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_about_crash)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem(
                        text = "About MusicMate",
                        iconResId = R.drawable.rounded_info_24,
                        isSelected = (activeItemId == R.id.menu_about_music_mate)
                    ) {
                        activity.handleNavigationItemClick(R.id.menu_about_music_mate)
                        coroutineScope.launch { drawerState.close() }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    ) {
        AndroidView(
            factory = { legacyView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = drawerGold,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(
        color = drawerDivider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DrawerItem(
    text: String,
    iconResId: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor = if (isSelected) Color(0x22FFD700) else Color.Transparent
    val contentColor = if (isSelected) drawerGold else drawerWhite
    val iconColor = if (isSelected) drawerGold else drawerGray
    val borderModifier = if (isSelected) {
        Modifier.border(0.75.dp, drawerGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(borderModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(drawerGold)
            )
        }
    }
}
