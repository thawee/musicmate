package apincer.android.mmate.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import apincer.music.core.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReplayGainSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun presetWorksFromOffAndUntaggedPolicySurvivesRecreation() {
        val context = compose.activity.applicationContext
        val mode = Settings.getReplayGainMode(context)
        val target = Settings.getReplayGainTarget(context)
        val preamp = Settings.getReplayGainPreamp(context)
        val protection = Settings.isReplayGainPreventClipping(context)
        val policy = Settings.getReplayGainUntaggedPolicy(context)
        try {
            Settings.setReplayGainMode(context, "off")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Use quality preset").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals("album", Settings.getReplayGainMode(context))
                assertEquals(-18f, Settings.getReplayGainTarget(context), 0f)
                assertEquals(0f, Settings.getReplayGainPreamp(context), 0f)
                assertTrue(Settings.isReplayGainPreventClipping(context))
                assertEquals("unchanged", Settings.getReplayGainUntaggedPolicy(context))
            }
            compose.onNodeWithText("Estimate attenuation").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertEquals("attenuate", Settings.getReplayGainUntaggedPolicy(context))
            }
            compose.onNodeWithText("Leave unchanged").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals("unchanged", Settings.getReplayGainUntaggedPolicy(context))
            }
        } finally {
            Settings.setReplayGainMode(context, mode)
            Settings.setReplayGainTarget(context, target)
            Settings.setReplayGainPreamp(context, preamp)
            Settings.setReplayGainPreventClipping(context, protection)
            Settings.setReplayGainUntaggedPolicy(context, policy)
        }
    }
}
