package apincer.android.mmate.ui.compose

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.music.core.playback.spi.PlaybackTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportScreensAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun installedApp_doesNotDeclareNotificationListenerAccess() {
        val context = composeRule.activity
        val services = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services.orEmpty()

        assertTrue(services.isNotEmpty())
        assertFalse(services.any {
            it.name.endsWith(".MediaNotificationListener") ||
                it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        })
    }

    @Test
    fun playerPicker_keepsLocalAndNetworkOutputsWithoutAccessPrompt() {
        val localTarget = object : PlaybackTarget {
            override fun isStreaming() = false
            override fun getTargetId() = "local-test"
            override fun getDisplayName() = "This phone"
            override fun getDescription() = "Local audio"
            override fun canReadSate() = true
        }
        var selectedTarget: PlaybackTarget? = null
        var serverRequested = false
        var systemOutputRequested = false
        composeRule.setContent {
            MusicMateTheme {
                PlayerPickerDialog(
                    targets = listOf(PlayerTargetItem(localTarget, "This phone")),
                    serverRunning = false,
                    onStartServerClick = { serverRequested = true },
                    onDismissRequest = {},
                    onTargetSelected = { selectedTarget = it },
                    onBluetoothOutputClick = { systemOutputRequested = true }
                )
            }
        }

        composeRule.onNodeWithText("External Player Access").assertDoesNotExist()
        composeRule.onNodeWithText("Start server").performClick()
        composeRule.onNodeWithText("This phone").performClick()
        composeRule.onNodeWithText("Bluetooth / System Output…").performClick()
        composeRule.runOnIdle {
            assertTrue(serverRequested)
            assertSame(localTarget, selectedTarget)
            assertTrue(systemOutputRequested)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settings_exposesSingleToggleAndSelectedRadioSemantics() {
        composeRule.setContent {
            MusicMateTheme {
                SettingsScreen(
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

        composeRule.onNodeWithText("Open audio tuning").assertDoesNotExist()
        composeRule.onNodeWithText("AUDIO TUNING").assertDoesNotExist()
        composeRule.onNodeWithText("Auto Scroll to Now Playing").assertIsOn()
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun permission_explainsRequirementsAndNamesRecoveryAction() {
        composeRule.setContent {
            MusicMateTheme {
                PermissionScreen(
                    systemAccess = SystemAccessState(
                        hasStorageAccess = false
                    ),
                    focusedCapability = SystemAccessCapability.STORAGE,
                    onStorageAccessClick = {}
                )
            }
        }

        composeRule.onNodeWithText("System Access").assertExists()
        // The Play build words the storage card its own way (src/play/res)
        composeRule.onNodeWithText(composeRule.activity.getString(apincer.android.mmate.R.string.permission_storage_title)).assertExists()
        composeRule.onNodeWithText("External Player Access").assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(apincer.android.mmate.R.string.action_open_storage_settings)).assertHasClickAction()
        composeRule.onNodeWithText("Enable external player access").assertDoesNotExist()
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
