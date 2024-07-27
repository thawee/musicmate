package apincer.android.mmate;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import apincer.android.mmate.repository.FileRepository;

public class Settings {
    private static final String TAG = Settings.class.getSimpleName();

    public static boolean isShowStorageSpace(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_STORAGE_SPACE,true);
    }

    public static boolean isOnNightModeOnly(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_NIGHT_MODE_ONLY,true);
    }

    public static boolean isListFollowNowPlaying(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_LIST_FOLLOW_NOW_PLAYING,false);
    }

    public static boolean isShowTrackNumber(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_PREFIX_TRACK_NUMBER_ON_TITLE,false);
    }

    public static boolean isVibrateOnNextSong(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_VIBRATE_ON_NEXT_SONG,false);
    }

    public static boolean isUseMediaButtons(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_NEXT_SONG_BY_MEDIA_BUTTONS,true);
    }

    public static boolean isEnableMediaServer(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_ENABLE_MEDIA_SERVER,false);
    }

    public static List<String> getDirectories(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        List<String> defaultDirs = FileRepository.newInstance(context).getDefaultMusicPaths();
        Set<String> defaultDirsSet = new HashSet<>(defaultDirs);
        Set<String> dirs = prefs.getStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, defaultDirsSet);
        return new ArrayList<>(dirs);
    }

    public static void setDirectories(Context context, List<String> dirs) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> dirsSet = new HashSet<>(dirs);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, dirsSet);
        edit.apply();
    }

    public static boolean checkDirectoriesSet(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> dirsSet = new HashSet<>();
        Set<String> dirs = prefs.getStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, dirsSet);
        return !dirs.isEmpty();
    }
}