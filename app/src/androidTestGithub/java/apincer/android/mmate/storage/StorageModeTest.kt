package apincer.android.mmate.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.provider.StorageMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The GitHub build keeps all-files access and works on the library by path. */
@RunWith(AndroidJUnit4::class)
class StorageModeTest {
    @Test
    fun theGithubBuildUsesAllFilesAccess() {
        assertTrue(StorageMode.usesAllFilesAccess(InstrumentationRegistry.getInstrumentation().targetContext))
    }
}
