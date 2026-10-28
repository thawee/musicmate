package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.headphones.AutoEqEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoEqSearchUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hd650 = AutoEqEntry("Sennheiser HD 650", "oratory1990", null, "oratory1990/over-ear/Sennheiser%20HD%20650")
    private val hd600 = AutoEqEntry("Sennheiser HD 600", "crinacle", "GRAS 43AG-7", "crinacle/GRAS%2043AG-7%20over-ear/Sennheiser%20HD%20600")

    @Test fun typingFiltersRowsAndTappingOnePicksIt() {
        var picked: AutoEqEntry? = null
        composeRule.setContent {
            MusicMateTheme {
                AutoEqSearchDialog(listOf(hd600, hd650), busy = false, error = null, onPick = { picked = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Headphone model").performTextInput("hd 650")
        composeRule.onNodeWithText("by oratory1990").assertIsDisplayed()
        composeRule.onNodeWithText("Sennheiser HD 600").assertDoesNotExist()
        composeRule.onNodeWithText("Sennheiser HD 650").performClick()
        composeRule.runOnIdle { assertEquals(hd650, picked) }
    }

    @Test fun rowShowsSourceAndRig() {
        composeRule.setContent {
            MusicMateTheme {
                AutoEqSearchDialog(listOf(hd600), busy = false, error = null, onPick = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Headphone model").performTextInput("600")
        composeRule.onNodeWithText("by crinacle on GRAS 43AG-7").assertIsDisplayed()
    }

    @Test fun loadingDisablesSearch() {
        composeRule.setContent {
            MusicMateTheme {
                AutoEqSearchDialog(null, busy = true, error = null, onPick = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Loading the AutoEq headphone list").assertIsDisplayed()
        composeRule.onNodeWithText("Headphone model").assertIsNotEnabled()
    }

    @Test fun loadFailureShowsTheErrorInsteadOfLoading() {
        var dismissed = false
        val message = "Could not reach AutoEq. Check the internet connection and try again."
        composeRule.setContent {
            MusicMateTheme {
                AutoEqSearchDialog(null, busy = false, error = message, onPick = {}, onDismiss = { dismissed = true })
            }
        }
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("Loading the AutoEq headphone list").assertDoesNotExist()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.runOnIdle { assertEquals(true, dismissed) }
    }
}
