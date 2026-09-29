package apincer.android.mmate.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import apincer.android.mmate.R
import apincer.android.mmate.service.MediaNotificationListener
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.PermissionScreen
import apincer.android.mmate.ui.compose.SystemAccessCapability
import apincer.android.mmate.ui.compose.SystemAccessState
import apincer.android.mmate.utils.PermissionUtils

class PermissionActivity : ComponentActivity() {

    private var systemAccess by mutableStateOf(SystemAccessState())
    private var focusedCapability by mutableStateOf(SystemAccessCapability.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        focusedCapability = intent.getStringExtra(EXTRA_FOCUS_CAPABILITY)
            ?.let { runCatching { SystemAccessCapability.valueOf(it) }.getOrNull() }
            ?: SystemAccessCapability.NONE
        refreshSystemAccess()

        setContent {
            MusicMateTheme {
                PermissionScreen(
                    systemAccess = systemAccess,
                    focusedCapability = focusedCapability,
                    onStorageAccessClick = ::openStorageAccessSettings,
                    onExternalPlayerAccessClick = ::openExternalPlayerAccessSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemAccess()
    }

    private fun refreshSystemAccess() {
        systemAccess = SystemAccessState(
            hasFullStorageAccess = PermissionUtils.checkFullStorageAccessPermissions(this),
            hasExternalPlayerAccess = PermissionUtils.isNotificationListenerEnabled(this)
        )
    }

    private fun openStorageAccessSettings() {
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        launchSettings(appSettings, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    private fun openExternalPlayerAccessSettings() {
        val listener = ComponentName(this, MediaNotificationListener::class.java)
        val detailSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        }
        launchSettings(detailSettings, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun launchSettings(primary: Intent, fallback: Intent) {
        try {
            startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(fallback)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.system_access_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val EXTRA_FOCUS_CAPABILITY = "focus_capability"

        @JvmStatic
        fun createIntent(context: Context, focus: SystemAccessCapability): Intent =
            Intent(context, PermissionActivity::class.java).apply {
                putExtra(EXTRA_FOCUS_CAPABILITY, focus.name)
            }
    }
}
