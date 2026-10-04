package apincer.android.mmate.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Settings compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Settings compact 200% text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Settings expanded", widthDp = 1000, heightDp = 700, showBackground = true)
@Preview(name = "Settings expanded 200% text", widthDp = 1000, heightDp = 700, fontScale = 2f, showBackground = true)
@Composable
fun SettingsVisualBaselinePreview() {
    MusicMateTheme {
        SettingsScreen(
            showStorageSpace = true,
            onShowStorageSpaceChange = {},
            prefixTrackNumber = false,
            onPrefixTrackNumberChange = {},
            listFollowsNowPlaying = true,
            onListFollowsNowPlayingChange = {},
            artistAwareSimilarSongs = true,
            onArtistAwareSimilarSongsChange = {},
            replayGainMode = "track",
            onReplayGainModeChange = {},
            replayGainPreamp = 0f,
            onReplayGainPreampChange = {},
            replayGainPreventClipping = true,
            onReplayGainPreventClippingChange = {},
            tapActionMode = "listen",
            onTapActionModeChange = {},
            studioKeepScreenOn = true,
            onStudioKeepScreenOnChange = {},
            onBackClick = {}
        )
    }
}
