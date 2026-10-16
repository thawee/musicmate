package apincer.android.mmate.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.provider.StorageMode
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Play policy: the Play build must not request all-files access, from its own manifest or a library's. */
@RunWith(AndroidJUnit4::class)
class StorageModeTest {
    @Test
    fun thePlayBuildWorksThroughFolderGrants() {
        assertFalse(StorageMode.usesAllFilesAccess(InstrumentationRegistry.getInstrumentation().targetContext))
    }
}
