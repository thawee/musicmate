package apincer.android.mmate.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.pro.Pro
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The GitHub build includes every Pro feature and never offers a purchase. */
@RunWith(AndroidJUnit4::class)
class ProIncludedTest {
    @Test
    fun theGithubBuildIsAlwaysPro() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(Pro.isPro(context))
        assertFalse(Pro.store(context).sellsPro)
        assertTrue(Pro.store(context).isPro.value)
    }
}
