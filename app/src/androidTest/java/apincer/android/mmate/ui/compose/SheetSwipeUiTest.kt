package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.ui.viewmodel.RelatedTracksSheetState
import apincer.music.core.model.AudioTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SheetSwipeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tracks = (1L..40L).map { id ->
        AudioTag(id).apply {
            title = "Swipe track $id"
            artist = "Swipe artist"
            album = "Swipe album"
            path = "/music/Swipe artist - Swipe track $id.flac"
            setUniqueKey("swipe-track-$id")
            setAudioDuration(180.0)
        }
    }

    @Test fun modalMusicCenterQueueStaysAnchoredDuringBodySwipes() {
        val positions = mutableListOf<Float>()
        var dismissed = false
        var selected = 0L
        compose.setContent {
            MusicMateTheme {
                AudioHubSheet(
                    nowPlayingState = NowPlayingState(),
                    queueState = QueueState(tracks, null),
                    mediaServerState = MediaServerState(), initialTab = 1,
                    onDismissRequest = { dismissed = true }, onSelectTargetPlayer = {},
                    onPlayPause = {}, onNext = {}, onPrevious = {},
                    onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {},
                    onVolumeDown = {}, onVolumeUp = {}, onVolumeChanged = {},
                    onTrackClicked = {}, onQueueTrackClicked = { selected = it.id },
                    onQueueTrackRemoved = { _, _ -> }, onQueueClear = {},
                    onQueueJumpToPlaying = {}, onQueueBrowseLibrary = {},
                    onStartServerClicked = {}, onStopServerClicked = {},
                    onCopyUrlClicked = {}, onOpenUrlClicked = {}, onQrCodeClicked = {},
                    showGestureHints = false, presentation = MusicCenterPresentation.MODAL,
                    modifier = Modifier.onGloballyPositioned { positions.add(it.positionInWindow().y) }
                )
            }
        }
        verifyAnchoredBody(positions)
        compose.runOnIdle { assertTrue("Queue body swipe dismissed the sheet", !dismissed) }
        compose.onNode(verticalBody).performScrollToNode(hasText("Swipe track 40"))
        compose.onNodeWithText("Swipe track 40").performClick()
        compose.runOnIdle { assertEquals(40L, selected); assertTrue(!dismissed) }
        compose.onNodeWithText("Playback").performClick().assertIsSelected()
        compose.onNodeWithText("Queue").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Close Music Center").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun relatedTracksStaysAnchoredDuringBodySwipes() {
        val positions = mutableListOf<Float>()
        var dismissed = false
        var queued = 0
        compose.setContent {
            MusicMateTheme {
                RelatedTracksSheet(
                    state = RelatedTracksSheetState(isVisible = true, title = "Related swipe tracks",
                        subtitle = "40 tracks", tracks = tracks),
                    onDismissRequest = { dismissed = true }, onPlayTrack = {}, onPlayAll = {},
                    onQueueAll = { queued = it.size }, onViewInLibrary = { _, _ -> },
                    modifier = Modifier.onGloballyPositioned { positions.add(it.positionInWindow().y) }
                )
            }
        }
        verifyAnchoredBody(positions)
        compose.runOnIdle { assertTrue("Related tracks body swipe dismissed the sheet", !dismissed) }
        compose.onNode(verticalBody).performScrollToNode(hasText("Swipe track 40"))
        compose.onNodeWithText("Swipe track 40").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add All to Queue").performClick()
        compose.runOnIdle { assertEquals(40, queued); assertTrue(!dismissed) }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun filenameTagsBodyScrollKeepsSheetOpenAndCancelReachable() {
        var dismissed = false
        var applied = false
        compose.setContent {
            MusicMateTheme {
                TagsFromFilenameSheet(tracks, onDismiss = { dismissed = true }, onApply = { applied = true })
            }
        }
        val title = compose.onNodeWithText("TAGS FROM FILENAME")
        val top = title.fetchSemanticsNode().boundsInRoot.top
        val body = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("TAGS FROM FILENAME")))
        // Release the pointer before querying Compose; an active overscroll never becomes idle.
        compose.mainClock.autoAdvance = false
        try {
            body.performTouchInput { swipeDown(durationMillis = 400) }
            repeat(12) {
                compose.mainClock.advanceTimeByFrame()
                assertEquals("Filename body swipe moved the sheet", top,
                    title.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        repeat(3) { body.performTouchInput { swipeUp() } }
        compose.runOnIdle { assertTrue("Filename body swipe dismissed the sheet", !dismissed) }
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertTrue(!applied) }
    }

    private fun verifyAnchoredBody(positions: MutableList<Float>) {
        val body = compose.onNode(verticalBody)
        val top = compose.runOnIdle { positions.last().also { positions.clear() } }
        body.performTouchInput { swipeDown(durationMillis = 400) }
        compose.runOnIdle {
            assertTrue("Body edge swipe moved the sheet: $positions",
                positions.all { kotlin.math.abs(it - top) <= 1f })
        }
        repeat(3) {
            body.performTouchInput { swipeUp() }
            compose.runOnIdle { assertEquals(top, positions.lastOrNull() ?: top, 1f) }
        }
    }

    private val verticalBody = hasScrollAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
}
