package apincer.android.mmate.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.pro.Pro
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Play build sells Pro through Google Play. */
@RunWith(AndroidJUnit4::class)
class ProSoldTest {
    @Test
    fun thePlayBuildSellsPro() {
        assertTrue(Pro.store(InstrumentationRegistry.getInstrumentation().targetContext).sellsPro)
    }
}
