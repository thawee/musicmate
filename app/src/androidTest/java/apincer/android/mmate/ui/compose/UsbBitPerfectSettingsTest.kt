package apincer.android.mmate.ui.compose

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.ui.SettingsActivity
import apincer.music.core.Settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbBitPerfectSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun switchSavesTheUsbPlaybackPreference() {
        val context = composeRule.activity
        val initial = Settings.isUsbBitPerfectEnabled(context)
        val toggle = composeRule.onNodeWithText("USB bit-perfect").performScrollTo()
        try {
            toggle.performClick()
            composeRule.runOnIdle {
                assertEquals(!initial, Settings.isUsbBitPerfectEnabled(context))
            }
            if (initial) toggle.assertIsOff() else toggle.assertIsOn()
        } finally {
            Settings.setUsbBitPerfectEnabled(context, initial)
        }
    }
}
