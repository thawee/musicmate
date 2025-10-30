package apincer.android.mmate;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import apincer.music.core.Constants;

public class Settings {
    private static final String TAG = Settings.class.getSimpleName();

    public static boolean isShowStorageSpace(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_STORAGE_SPACE,true);
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

    public static boolean isUseMediaButtons(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_NEXT_SONG_BY_MEDIA_BUTTONS,true);
    }

    public static boolean isAutoStartMediaServer(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_ENABLE_MEDIA_SERVER,false);
    }

    public static boolean isUseNettyLibrary(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_NETTY_MEDIA_SERVER,false);
    }

    public static boolean isExcludeArtistFromSimilarSongs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_EXCLUDE_ARTIST_FROM_SIMILAR_SONGS,false);
    }

    /*
    public static List<String> getDirectories(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        List<String> defaultDirs = FileRepository.newInstance(context).getDefaultMusicPaths();
        Set<String> defaultDirsSet = new HashSet<>(defaultDirs);
        Set<String> dirs = prefs.getStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, defaultDirsSet);
        return new ArrayList<>(dirs);
    } */

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

    /*
    public static long getLastScanTime(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(Constants.PREF_LAST_SCAN_TIME,0);
    }

    public static void setLastScanTime(Context context, long l) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(Constants.PREF_LAST_SCAN_TIME, l);
        edit.apply();
    } */

    public static SharedPreferences getPreferences(Context context) {
        // The default preference file name is constructed like this
        String defaultPrefsName = context.getPackageName() + "_preferences";

        return context.getSharedPreferences(defaultPrefsName, Context.MODE_PRIVATE);

       // return PreferenceManager.getDefaultSharedPreferences(applicationContext);
    }
}