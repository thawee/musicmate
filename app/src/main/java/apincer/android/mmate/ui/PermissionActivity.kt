package apincer.android.mmate.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.PermissionScreen
import apincer.android.mmate.utils.PermissionUtils

class PermissionActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasGranted = permissions.values.any { it }
        if (hasGranted) {
            if (!PermissionUtils.checkFullStorageAccessPermissions(applicationContext)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                }
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MusicMateTheme {
                PermissionScreen(
                    onGrantPermissionsClick = {
                        requestPermissionsLauncher.launch(PermissionUtils.PERMISSIONS_ALL)
                    }
                )
            }
        }
    }
}
