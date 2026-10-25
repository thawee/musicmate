package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.music.core.model.AudioTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeAndLongPressGesturesAreOfferedAsActions() {
        var previous = 0
        var scrolled = 0
        composeRule.setContent {
            MusicMateTheme {
                FloatingMiniPlayerDock(
                    track = AudioTag(7L).apply { setTitle("So What"); setArtist("Miles Davis") },
                    isPlaying = true,
                    outputTarget = "",
                    progress = 0.3f,
                    onPlayPauseClick = {},
                    onNextClick = {},
                    onPreviousClick = { previous++ },
                    onOpenAudioHub = {},
                    onScrollToPlaying = { scrolled++ }
                )
            }
        }

        // The artwork and the title both open Music Center, and both carry the gesture actions
        val openers = composeRule.onAllNodes(
            SemanticsMatcher("opens Music Center") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Music Center"
            }
        ).fetchSemanticsNodes()
        assertEquals(2, openers.size)
        openers.forEach { node ->
            assertEquals(
                listOf("Previous", "Show playing song in list"),
                node.config[SemanticsActions.CustomActions].map { it.label }
            )
        }
        composeRule.runOnUiThread { openers[1].config[SemanticsActions.CustomActions].forEach { it.action() } }
        assertEquals(1, previous)
        assertEquals(1, scrolled)

        composeRule.onNodeWithContentDescription("Pause")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithContentDescription("Next").assertExists()
    }

    @Test
    fun retryOnPlaybackErrorRunsTheRequestAndClearsTheMessage() {
        val state = MainScaffoldState()
        var retried = 0
        composeRule.setContent { MusicMateTheme { PlaybackMessageHost(state) } }

        composeRule.runOnUiThread {
            state.playbackMessage.value = PlaybackMessage("Couldn't start playback", "Retry") { retried++ }
        }
        composeRule.onNodeWithText("Couldn't start playback").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(1, retried)
        assertNull(state.playbackMessage.value)
    }
}
