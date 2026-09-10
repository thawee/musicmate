package apincer.music.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        return prefs.getBoolean(Constants.PREF_LIST_FOLLOW_NOW_PLAYING,true);
    }

    public static boolean isShowTrackNumber(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_PREFIX_TRACK_NUMBER_ON_TITLE,false);
    }


    public static boolean isArtistAwareSimilarSongs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_ARTIST_AWARE_SIMILAR_SONGS,false);
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

    public static SharedPreferences getPreferences(Context context) {
        // The default preference file name is constructed like this
        String defaultPrefsName = context.getPackageName() + "_preferences";

        return context.getSharedPreferences(defaultPrefsName, Context.MODE_PRIVATE);

       // return PreferenceManager.getDefaultSharedPreferences(applicationContext);
    }

    public static void setLastPlayerTargetId(Context context, String targetId) {
        SharedPreferences prefs = getPreferences(context);
        prefs.edit().putString("PREF_LAST_PLAYER_TARGET_ID", targetId).apply();
    }

    public static String getLastPlayerTargetId(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getString("PREF_LAST_PLAYER_TARGET_ID", null);
    }

    public static String getReplayGainMode(Context context) {
        if (context == null) return "track";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(Constants.PREF_REPLAYGAIN_MODE, "track");
    }

    public static void setReplayGainMode(Context context, String mode) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(Constants.PREF_REPLAYGAIN_MODE, mode).apply();
    }

    public static float getReplayGainPreamp(Context context) {
        if (context == null) return 0.0f;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getFloat(Constants.PREF_REPLAYGAIN_PREAMP, 0.0f);
    }

    public static void setReplayGainPreamp(Context context, float preamp) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putFloat(Constants.PREF_REPLAYGAIN_PREAMP, preamp).apply();
    }

    public static boolean isReplayGainPreventClipping(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_REPLAYGAIN_PREVENT_CLIPPING, true);
    }

    public static void setReplayGainPreventClipping(Context context, boolean prevent) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(Constants.PREF_REPLAYGAIN_PREVENT_CLIPPING, prevent).apply();
    }

    public static String getTapActionMode(Context context) {
        if (context == null) return Constants.TAP_MODE_CURATE;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(Constants.PREF_TAP_ACTION_MODE, Constants.TAP_MODE_CURATE);
    }

    public static void setTapActionMode(Context context, String mode) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(Constants.PREF_TAP_ACTION_MODE, mode).apply();
    }
}