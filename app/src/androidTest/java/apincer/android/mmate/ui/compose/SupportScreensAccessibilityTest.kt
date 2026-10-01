package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportScreensAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settings_exposesSingleToggleAndSelectedRadioSemantics() {
        composeRule.setContent {
            MusicMateTheme {
                SettingsScreen(
                    serverEngine = "nio",
                    onServerEngineChange = {},
                    showStorageSpace = true,
                    onShowStorageSpaceChange = {},
                    prefixTrackNumber = false,
                    onPrefixTrackNumberChange = {},
                    listFollowsNowPlaying = true,
                    onListFollowsNowPlayingChange = {},
                    artistAwareSimilarSongs = true,
                    onArtistAwareSimilarSongsChange = {},
                    onBackClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Display Storage Space").assertIsOn()
        composeRule.onNodeWithText("SonicNIO").assertIsSelected()
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun permission_explainsRequirementsAndNamesRecoveryAction() {
        composeRule.setContent {
            MusicMateTheme {
                PermissionScreen(
                    systemAccess = SystemAccessState(
                        hasFullStorageAccess = false,
                        hasExternalPlayerAccess = false
                    ),
                    focusedCapability = SystemAccessCapability.STORAGE,
                    onStorageAccessClick = {},
                    onExternalPlayerAccessClick = {}
                )
            }
        }

        composeRule.onNodeWithText("System Access").assertExists()
        composeRule.onNodeWithText("Full Storage Access").assertExists()
        composeRule.onNodeWithText("External Player Access").assertExists()
        composeRule.onNodeWithText("Open storage settings").assertHasClickAction()
        composeRule.onNodeWithText("Enable external player access").assertHasClickAction()
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun about_linksNameTheirDestinationWithoutIconStops() {
        composeRule.setContent {
            MusicMateTheme {
                AboutScreen(
                    appVersion = "1.0-test",
                    pieEntries = emptyList(),
                    onBackClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open MusicMate on Google Play").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open MusicMate source on GitHub").assertHasClickAction()
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
