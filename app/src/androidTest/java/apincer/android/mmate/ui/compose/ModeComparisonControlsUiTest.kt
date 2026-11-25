package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModeComparisonControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun comparisonSwitchesSidesAndClearsOnSettingChangeAndDismissal() {
        var visible by mutableStateOf(true)
        var identity by mutableStateOf(0)
        val calls = mutableListOf<Boolean?>()
        compose.setContent {
            MusicMateTheme {
                if (visible) ModeComparisonControls(identity, true) { calls.add(it) }
            }
        }
        compose.onNodeWithText("Compare levels").performClick()
        compose.runOnIdle { assertEquals(false, calls.last()) }
        compose.onNodeWithText("Hear correction only").performClick()
        compose.runOnIdle { assertEquals(true, calls.last()) }
        compose.onNodeWithText("End comparison").performClick()
        compose.runOnIdle { assertNull(calls.last()) }
        compose.onNodeWithText("Compare levels").performClick()
        compose.runOnIdle { identity++ }
        compose.onNodeWithText("Compare levels").assertExists()
        compose.runOnIdle { assertNull(calls.last()) }
        compose.onNodeWithText("Compare levels").performClick()
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(calls.last()) }
    }

    @Test fun backgroundingClearsComparison() {
        val calls = mutableListOf<Boolean?>()
        compose.setContent { MusicMateTheme { ModeComparisonControls("active", true) { calls.add(it) } } }
        compose.onNodeWithText("Compare levels").performClick()
        compose.runOnIdle { assertEquals(false, calls.last()) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Compare levels").assertExists()
        compose.runOnIdle { assertNull(calls.last()) }
    }

    @Test fun bypassCannotStartComparison() {
        val calls = mutableListOf<Boolean?>()
        compose.setContent { MusicMateTheme { ModeComparisonControls("off", false) { calls.add(it) } } }
        compose.onNodeWithText("Compare levels").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(calls.all { it == null }) }
    }
}
