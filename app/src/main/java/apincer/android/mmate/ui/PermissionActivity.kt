package apincer.android.mmate.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.PermissionScreen
import apincer.android.mmate.utils.PermissionUtils

class PermissionActivity : ComponentActivity() {

    companion object {
        const val REQUEST_CODE_STORAGE_PERMISSION = 1010
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MusicMateTheme {
                PermissionScreen(
                    onGrantPermissionsClick = {
                        requestAppPermissions()
                    }
                )
            }
        }
    }

    private fun requestAppPermissions() {
        ActivityCompat.requestPermissions(
            this,
            PermissionUtils.PERMISSIONS_ALL,
            REQUEST_CODE_STORAGE_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
    }
}
