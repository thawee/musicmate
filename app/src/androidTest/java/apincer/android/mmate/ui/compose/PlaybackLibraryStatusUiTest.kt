package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import apincer.music.core.model.AudioTag
import org.junit.Rule
import org.junit.Test

class PlaybackLibraryStatusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun playbackShowsOrganizationAndUpdatesWhenTheTrackChanges() {
        val state = NowPlayingState().apply {
            track.value = track(true, "/Music/Library/song.flac")
        }
        compose.setContent {
            MusicMateTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                    NowPlayingPage(state, onPlayPause = {}, onNext = {}, onPrevious = {},
                        onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {}, onTrackClicked = {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Organized in your library").assertIsDisplayed()
        compose.runOnIdle { state.track.value = track(false, "/Music/Incoming/song.flac") }
        compose.onNodeWithText("NEW").assertIsDisplayed()
        compose.onNodeWithContentDescription("Organized in your library").assertDoesNotExist()
        compose.runOnIdle { state.track.value = track(false, "/Download/song.flac") }
        compose.onNodeWithText("DL").assertIsDisplayed()
        compose.onNodeWithText("NEW").assertDoesNotExist()
    }

    @Test fun idlePlaybackHasNoOrganizationBadge() {
        compose.setContent { MusicMateTheme { PlaybackLibraryStatus(null, Modifier.width(320.dp)) } }
        compose.onNodeWithText("Organized").assertDoesNotExist()
        compose.onNodeWithText("NEW").assertDoesNotExist()
        compose.onNodeWithText("DL").assertDoesNotExist()
    }

    private fun track(managed: Boolean, path: String) = AudioTag(9876).apply {
        setTitle("A song with a long title"); setArtist("Artist"); setAlbum("Album")
        setUniqueKey(path); setPath(path); setIsManaged(managed); setAudioDuration(240.0)
    }
}
