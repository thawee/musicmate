package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import apincer.android.mmate.service.PlayerSong
import apincer.android.mmate.service.PlayerSongMonitor
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PlayerSongUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun realBroadcastReachesCardWithoutReplacingLocalTrack() {
        val context = composeRule.activity
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val wasEnabled = preferences.getBoolean(PlayerSongMonitor.PREF_ENABLED, false)
        val monitor = PlayerSongMonitor(context)
        val state = NowPlayingState()
        val localPlayback = state.playbackState.value
        try {
            composeRule.runOnIdle {
                preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, true).commit()
                monitor.song.observe(context) { state.playerSong.value = it }
                monitor.start()
            }
            composeRule.setContent {
                MusicMateTheme { state.playerSong.value?.let { PlayerSongCard(it) } }
            }
            context.sendBroadcast(Intent("com.maxmpz.audioplayer.TRACK_CHANGED")
                .setPackage(context.packageName).putExtra("title", "Broadcast song"))
            context.sendBroadcast(Intent("com.maxmpz.audioplayer.STATUS_CHANGED")
                .setPackage(context.packageName).putExtra("state", 1))
            composeRule.waitUntil(5000) { state.playerSong.value?.isPlaying == true }
            composeRule.onNodeWithText("Broadcast song").assertExists()
            composeRule.onNodeWithText("Poweramp · Playing").assertExists()
            composeRule.runOnIdle {
                assertNull(state.track.value)
                assertSame(localPlayback, state.playbackState.value)
                preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, false).commit()
                assertNull(state.playerSong.value)
            }
            composeRule.onNodeWithText("Broadcast song").assertDoesNotExist()
        } finally {
            composeRule.runOnIdle {
                monitor.stop()
                preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, wasEnabled).commit()
            }
        }
    }

    @Test
    fun settingsIntegrationIsOptInAndToggleable() {
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            MusicMateTheme {
                SettingsScreen(
                    prefixTrackNumber = false, onPrefixTrackNumberChange = {},
                    listFollowsNowPlaying = true, onListFollowsNowPlayingChange = {},
                    artistAwareSimilarSongs = true, onArtistAwareSimilarSongsChange = {},
                    playerSongDetection = enabled.value,
                    onPlayerSongDetectionChange = { enabled.value = it },
                    onBackClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Show song from other players").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithText("Show song from other players").assertIsOn().performClick().assertIsOff()
    }

    @Test
    fun externalSongIdentifiesSourceAndLimitsOfControls() {
        val song = mutableStateOf(PlayerSong("Poweramp", "Take On Me", "a-ha", "Hunting High and Low", null))
        composeRule.setContent { MusicMateTheme { PlayerSongCard(song.value) } }
        composeRule.onNodeWithText("Reported by Poweramp").assertExists()
        composeRule.onNodeWithText("Take On Me").assertExists()
        composeRule.onNodeWithText("a-ha · Hunting High and Low").assertExists()
        composeRule.onNodeWithText("Controls and MateTune below apply to MusicMate playback.").assertExists()
        composeRule.runOnIdle { song.value = song.value.copy(isPlaying = true) }
        composeRule.onNodeWithText("Poweramp · Playing").assertExists()
        composeRule.runOnIdle { song.value = song.value.copy(isPlaying = false) }
        composeRule.onNodeWithText("Poweramp · Paused").assertExists()
        composeRule.runOnIdle { song.value = song.value.copy(player = "Neutron", isPlaying = true) }
        composeRule.onNodeWithText("Neutron · Playing").assertExists()
    }
}
