package apincer.android.mmate.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import apincer.android.mmate.service.MediaServerManager
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
            var serverEngine by remember {
                mutableStateOf(prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore") ?: "httpcore")
            }
            var showStorageSpace by remember {
                mutableStateOf(Settings.isShowStorageSpace(this@SettingsActivity))
            }
            var prefixTrackNumber by remember {
                mutableStateOf(Settings.isShowTrackNumber(this@SettingsActivity))
            }
            var listFollowsNowPlaying by remember {
                mutableStateOf(Settings.isListFollowNowPlaying(this@SettingsActivity))
            }
            var artistAwareSimilarSongs by remember {
                mutableStateOf(Settings.isArtistAwareSimilarSongs(this@SettingsActivity))
            }

            MusicMateTheme {
                SettingsScreen(
                    serverEngine = serverEngine,
                    onServerEngineChange = { newEngine ->
                        serverEngine = newEngine
                        prefs.edit().putString(Constants.PREF_SERVER_ENGINE, newEngine).apply()
                        val manager = MediaServerManager(applicationContext)
                        manager.doBindService()
                        manager.restartServer()
                    },
                    showStorageSpace = showStorageSpace,
                    onShowStorageSpaceChange = { checked ->
                        showStorageSpace = checked
                        prefs.edit().putBoolean(Constants.PREF_SHOW_STORAGE_SPACE, checked).apply()
                    },
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
                    onBackClick = { finish() }
                )
            }
        }
    }
}
