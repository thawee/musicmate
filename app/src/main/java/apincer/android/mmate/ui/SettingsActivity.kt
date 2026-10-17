package apincer.android.mmate.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.SettingsScreen
import apincer.music.core.Constants
import apincer.music.core.Settings

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setContent {
            var prefixTrackNumber by remember {
                mutableStateOf(Settings.isShowTrackNumber(this@SettingsActivity))
            }
            var listFollowsNowPlaying by remember {
                mutableStateOf(Settings.isListFollowNowPlaying(this@SettingsActivity))
            }
            var artistAwareSimilarSongs by remember {
                mutableStateOf(Settings.isArtistAwareSimilarSongs(this@SettingsActivity))
            }
            var replayGainMode by remember {
                mutableStateOf(Settings.getReplayGainMode(this@SettingsActivity))
            }
            var replayGainPreamp by remember {
                mutableStateOf(Settings.getReplayGainPreamp(this@SettingsActivity))
            }
            var replayGainPreventClipping by remember {
                mutableStateOf(Settings.isReplayGainPreventClipping(this@SettingsActivity))
            }
            var tapActionMode by remember {
                mutableStateOf(Settings.getTapActionMode(this@SettingsActivity))
            }
            var studioKeepScreenOn by remember {
                mutableStateOf(Settings.isStudioKeepScreenOn(this@SettingsActivity))
            }
            var rendererVolumeLimit by remember {
                mutableStateOf(Settings.getRendererVolumeLimit(this@SettingsActivity))
            }

            MusicMateTheme {
                SettingsScreen(
                    prefixTrackNumber = prefixTrackNumber,
                    onPrefixTrackNumberChange = { checked ->
                        prefixTrackNumber = checked
                        prefs.edit().putBoolean(Constants.PREF_PREFIX_TRACK_NUMBER_ON_TITLE, checked).apply()
                    },
                    listFollowsNowPlaying = listFollowsNowPlaying,
                    onListFollowsNowPlayingChange = { checked ->
                        listFollowsNowPlaying = checked
                        prefs.edit().putBoolean(Constants.PREF_LIST_FOLLOW_NOW_PLAYING, checked).apply()
                    },
                    artistAwareSimilarSongs = artistAwareSimilarSongs,
                    onArtistAwareSimilarSongsChange = { checked ->
                        artistAwareSimilarSongs = checked
                        prefs.edit().putBoolean(Constants.PREF_ARTIST_AWARE_SIMILAR_SONGS, checked).apply()
                    },
                    replayGainMode = replayGainMode,
                    onReplayGainModeChange = { newMode ->
                        replayGainMode = newMode
                        Settings.setReplayGainMode(this@SettingsActivity, newMode)
                    },
                    replayGainPreamp = replayGainPreamp,
                    onReplayGainPreampChange = { newPreamp ->
                        replayGainPreamp = newPreamp
                        Settings.setReplayGainPreamp(this@SettingsActivity, newPreamp)
                    },
                    replayGainPreventClipping = replayGainPreventClipping,
                    onReplayGainPreventClippingChange = { checked ->
                        replayGainPreventClipping = checked
                        Settings.setReplayGainPreventClipping(this@SettingsActivity, checked)
                    },
                    tapActionMode = tapActionMode,
                    onTapActionModeChange = { newMode ->
                        tapActionMode = newMode
                        Settings.setTapActionMode(this@SettingsActivity, newMode)
                    },
                    studioKeepScreenOn = studioKeepScreenOn,
                    onStudioKeepScreenOnChange = { checked ->
                        studioKeepScreenOn = checked
                        Settings.setStudioKeepScreenOn(this@SettingsActivity, checked)
                    },
                    rendererVolumeLimit = rendererVolumeLimit,
                    onRendererVolumeLimitChange = { percent ->
                        rendererVolumeLimit = percent
                        Settings.setRendererVolumeLimit(this@SettingsActivity, percent)
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
}
