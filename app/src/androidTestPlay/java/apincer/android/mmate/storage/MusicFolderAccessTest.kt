package apincer.android.mmate.storage

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import apincer.android.mmate.ui.FolderGrantActivity
import apincer.android.mmate.ui.MusicFolderAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Play build: which folders MusicMate may change, and what Organize needs (a grant on Music itself). */
@RunWith(AndroidJUnit4::class)
class MusicFolderAccessTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val music = "/storage/emulated/0/Music"
    private val sub = "$music/MMGrantTest"

    private fun releaseAll() {
        for (permission in context.contentResolver.persistedUriPermissions) {
            context.contentResolver.releasePersistableUriPermission(permission.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun grant(path: String) {
        val scenario = ActivityScenario.launchActivityForResult<FolderGrantActivity>(
            FolderGrantActivity.createIntent(context, path))
        val device = UiDevice.getInstance(instrumentation)
        device.wait(Until.findObject(By.text(Pattern.compile("(?i)use this folder"))), 15_000).click()
        device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))), 15_000).click()
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(path, scenario.result.resultData.getStringExtra(FolderGrantActivity.EXTRA_PATH))
    }

    @Test
    fun organizeNeedsTheMusicFolderItself() {
        instrumentation.uiAutomation.executeShellCommand("mkdir -p $sub").close()
        releaseAll()
        assertFalse(MusicFolderAccess.canWrite(context, music))
        assertFalse(MusicFolderAccess.hasOrganizeAccess(context))

        grant(sub)
        assertTrue(MusicFolderAccess.canWrite(context, "$sub/Album/01.flac"))
        assertFalse("A subfolder grant does not cover Music", MusicFolderAccess.hasOrganizeAccess(context))

        grant(music)
        assertTrue(MusicFolderAccess.hasOrganizeAccess(context))
        assertEquals(music, MusicFolderAccess.organizeRoot(context))
    }
}
