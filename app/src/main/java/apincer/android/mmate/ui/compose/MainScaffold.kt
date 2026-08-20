package apincer.android.mmate.ui.compose

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import apincer.android.mmate.R
import apincer.android.mmate.ui.MainActivity
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    activity: MainActivity,
    legacyView: View,
    drawerState: DrawerState
) {
    val coroutineScope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Library", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall)
                    
                    DrawerItem("All Songs", R.drawable.rounded_library_music_24) { 
                        activity.handleNavigationItemClick(R.id.menu_library_all_songs)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Artists", R.drawable.rounded_for_you_24) { 
                        activity.handleNavigationItemClick(R.id.menu_tag_artist)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Genres", R.drawable.rounded_style_24) { 
                        activity.handleNavigationItemClick(R.id.menu_tag_genre)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Collections", R.drawable.rounded_order_play_24) { 
                        activity.handleNavigationItemClick(R.id.menu_collection)
                        coroutineScope.launch { drawerState.close() }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Discover", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall)
                    
                    DrawerItem("Recently Added", R.drawable.rounded_add_diamond_24) { 
                        activity.handleNavigationItemClick(R.id.menu_library_recently_added)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Similar Songs", R.drawable.rounded_auto_awesome_motion_24) { 
                        activity.handleNavigationItemClick(R.id.menu_library_similar_songs)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Quality", R.drawable.rounded_equalizer_24) { 
                        activity.handleNavigationItemClick(R.id.menu_sound_grade)
                        coroutineScope.launch { drawerState.close() }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("System", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall)
                    
                    DrawerItem("Manage Library", R.drawable.rounded_label_24) { 
                        activity.handleNavigationItemClick(R.id.menu_directories)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Settings", R.drawable.rounded_display_settings_24) { 
                        activity.handleNavigationItemClick(R.id.menu_settings)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Storage Access", R.drawable.rounded_folder_managed_24) { 
                        activity.handleNavigationItemClick(R.id.menu_files_permission)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Notifications", R.drawable.rounded_admin_panel_settings_24) { 
                        activity.handleNavigationItemClick(R.id.menu_notification_access)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Diagnostics", R.drawable.rounded_bug_report_24) { 
                        activity.handleNavigationItemClick(R.id.menu_about_crash)
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("About", R.drawable.rounded_help_24) { 
                        activity.handleNavigationItemClick(R.id.menu_about_music_mate)
                        coroutineScope.launch { drawerState.close() }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
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
private fun DrawerItem(text: String, iconResId: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(painterResource(id = iconResId), contentDescription = null) },
        label = { Text(text) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
