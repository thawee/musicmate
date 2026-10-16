package apincer.android.mmate.ui

import android.Manifest
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
import androidx.activity.result.contract.ActivityResultContracts
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
import apincer.music.core.provider.StorageMode

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
                    onExternalPlayerAccessClick = ::openExternalPlayerAccessSettings,
                    onBack = { finish() }
                )
            }
        }
    }

    // Play build: READ_MEDIA_AUDIO is a runtime permission, asked here
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAndMaybeFinish()
    }

    override fun onResume() {
        super.onResume()
        refreshAndMaybeFinish()
    }

    private fun refreshAndMaybeFinish() {
        val hadStorageAccess = systemAccess.hasStorageAccess
        refreshSystemAccess()
        // Opened to grant storage for a scan: return to the folder picker as soon as it is granted
        if (focusedCapability == SystemAccessCapability.STORAGE && !hadStorageAccess
                && systemAccess.hasStorageAccess) {
            finish()
        }
    }

    private fun refreshSystemAccess() {
        systemAccess = SystemAccessState(
            hasStorageAccess = PermissionUtils.checkAccessPermissions(this),
            hasExternalPlayerAccess = PermissionUtils.isNotificationListenerEnabled(this)
        )
    }

    private fun openStorageAccessSettings() {
        if (!StorageMode.usesAllFilesAccess(this)) {
            requestAudioAccess()
            return
        }
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        launchSettings(appSettings, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    private fun requestAudioAccess() {
        val permission = Manifest.permission.READ_MEDIA_AUDIO
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUDIO_ASKED, false) && !shouldShowRequestPermissionRationale(permission)) {
            // Android stops showing the dialog once it was declined twice; the app's settings page still works
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            launchSettings(appDetails, Intent(Settings.ACTION_SETTINGS))
        } else {
            prefs.edit().putBoolean(KEY_AUDIO_ASKED, true).apply()
            requestAudio.launch(permission)
        }
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
        private const val KEY_AUDIO_ASKED = "audio_permission_asked"

        @JvmStatic
        fun createIntent(context: Context, focus: SystemAccessCapability): Intent =
            Intent(context, PermissionActivity::class.java).apply {
                putExtra(EXTRA_FOCUS_CAPABILITY, focus.name)
            }
    }
}
