package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.music.core.model.AudioTag
import apincer.android.mmate.ui.navigation.LibraryDestination
import apincer.android.mmate.ui.navigation.MainNavigationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainLibraryAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun populatedLibrary_hasNamedTrackActionsAndPassesAccessibilityChecks() {
        val track = AudioTag(101).apply {
            setUniqueKey("accessibility-track-101")
            setTitle("So What")
            setArtist("Miles Davis")
            setAlbum("Kind of Blue")
            setAudioDuration(545.0)
            setAudioEncoding("FLAC")
            setAudioSampleRate(96_000)
            setAudioBitsDepth(24)
            setQualityInd("Hi-Res")
        }

        composeRule.setContent {
            MusicMateTheme {
                MusicListScreen(
                    tracks = listOf(track),
                    selectedTracks = setOf(track),
                    nowPlayingTrack = null,
                    isPlaying = false,
                    isRefreshing = false,
                    onRefresh = {},
                    onTrackClick = { _, _ -> },
                    onTrackLongClick = { _, _ -> },
                    onTrackMenuClick = { _, _ -> },
                    onTrackQuickPlayClick = {},
                    onFolderPlayClick = {},
                    onFolderEnqueueClick = {},
                    showGestureHints = false,
                    trackArtwork = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF5B3A55))
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play So What").assertExists()
        composeRule.onNodeWithContentDescription("More options for So What").assertExists()
        composeRule.onNodeWithText("So What").assertIsSelected()
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptyLibrary_hasActionableStateAndPassesAccessibilityChecks() {
        composeRule.setContent {
            MusicMateTheme {
                MusicListScreen(
                    tracks = emptyList(),
                    selectedTracks = emptySet(),
                    nowPlayingTrack = null,
                    isPlaying = false,
                    isRefreshing = false,
                    libraryEmpty = true,
                    onRefresh = {},
                    onTrackClick = { _, _ -> },
                    onTrackLongClick = { _, _ -> },
                    onTrackMenuClick = { _, _ -> },
                    onFolderPlayClick = {},
                    onFolderEnqueueClick = {},
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithText("Choose folders / Scan").assertExists()
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadError_hasRetryActionAndPassesAccessibilityChecks() {
        composeRule.setContent {
            MusicMateTheme {
                MusicListScreen(
                    tracks = emptyList(),
                    selectedTracks = emptySet(),
                    nowPlayingTrack = null,
                    isPlaying = false,
                    isRefreshing = false,
                    loadError = "Music unavailable",
                    onRefresh = {},
                    onTrackClick = { _, _ -> },
                    onTrackLongClick = { _, _ -> },
                    onTrackMenuClick = { _, _ -> },
                    onFolderPlayClick = {},
                    onFolderEnqueueClick = {},
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithText("Music unavailable").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mainShell_hasNamedPrimaryActionsAndPassesAccessibilityChecks() {
        composeRule.setContent {
            val state = remember {
                MainScaffoldState().apply {
                    libraryEmpty.value = true
                    headerStatsText.value = "No music indexed"
                    isFloatingDockVisible.value = false
                }
            }
            MusicMateTheme {
                MainScaffold(
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    state = state,
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("MusicMate Menu").assertExists()
        composeRule.onNodeWithContentDescription("Search music").assertExists()
        composeRule.onNodeWithContentDescription("Open Music Center").assertExists()
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun mainShell_opensAndDismissesMusicCenterThroughNavigationRoute() {
        composeRule.setContent {
            val state = remember {
                MainScaffoldState().apply {
                    libraryEmpty.value = true
                    isFloatingDockVisible.value = false
                }
            }
            MusicMateTheme {
                MainScaffold(
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    state = state,
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Music Center").performClick()
        composeRule.onNodeWithText("Music Center").assertExists()

        composeRule.onNodeWithContentDescription("Close Music Center").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Music Center").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun drawerSelectionUpdatesTheTypedLibraryRouteWithoutACallbackOwner() {
        val navigationState = MainNavigationState()
        composeRule.setContent {
            val state = remember {
                MainScaffoldState().apply {
                    libraryEmpty.value = true
                    isFloatingDockVisible.value = false
                }
            }
            MusicMateTheme {
                MainScaffold(
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    state = state,
                    navigationState = navigationState,
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("MusicMate Menu").performClick()
        composeRule.onNodeWithText("Artists").performClick()

        composeRule.runOnIdle {
            assert(navigationState.selectedLibraryDestination == LibraryDestination.ARTISTS)
        }
    }

    @Test
    fun selectedLibraryRouteSurvivesSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            val state = remember {
                MainScaffoldState().apply {
                    libraryEmpty.value = true
                    isFloatingDockVisible.value = false
                }
            }
            MusicMateTheme {
                MainScaffold(
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    state = state,
                    showGestureHints = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("MusicMate Menu").performClick()
        composeRule.onNodeWithText("Genres").performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithContentDescription("MusicMate Menu").performClick()

        composeRule.onNodeWithText("Genres").assertIsSelected()
    }

}
