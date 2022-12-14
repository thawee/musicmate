package apincer.android.mmate;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class Preferences {
    private static final String TAG = Preferences.class.getSimpleName();

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



    public static boolean isOpenNowPlaying(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_OPEN_NOW_PLAYING,false);
    }

    public static boolean isFollowNowPlaying(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_FOLLOW_NOW_PLAYING,false);
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
}