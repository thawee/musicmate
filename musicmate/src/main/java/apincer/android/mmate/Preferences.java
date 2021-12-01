package apincer.android.mmate;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.utils.AudioTagUtils;
import timber.log.Timber;

public class Preferences {
    private static final String TAG = Preferences.class.getSimpleName();
    public static boolean isShowNotification(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_NOTIFICATION,true);
    }
    public static boolean isShowStorageSpace(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_STORAGE_SPACE,true);
    }

    public static boolean isShowTrackNumber(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_PREFIX_TRACK_NUMBER_ON_TITLE,false);
    }

    public static boolean isShowGroupings(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_GROUPINGS_IN_COLLECTION,true);
    }

    public static boolean isSimilarOnTitleAndArtist(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SIMILAR_ON_TITLE_AND_ARTIST,true);
    }

    public static boolean isVibrateOnNextSong(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_VIBRATE_ON_NEXT_SONG,true);
    }

    public static boolean isUseMediaButtons(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_NEXT_SONG_BY_MEDIA_BUTTONS,true);
    }

    public static boolean isSaveOnInternalStorage(Context context, AudioTag metadata) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        boolean audiophile =prefs.getBoolean("preference_save_audiophile_int_card",true);
        if(audiophile && metadata.isAudiophile()) {
            return true;
        }
        boolean ms =prefs.getBoolean("preference_save_hires_master_int_card",true);
        if(ms && AudioTagUtils.isHiResMaster(metadata) & !metadata.isMQA()) {
            return true;
        }
        boolean lsac =prefs.getBoolean("preference_save_hires_int_card",false);
        if(lsac && AudioTagUtils.isHiResLossless(metadata) & !metadata.isMQA()) {
            return true;
        }
        boolean mqa =prefs.getBoolean("preference_save_mqa_int_card",false);
        if(mqa && metadata.isMQA()) {
            return true;
        }
        boolean dxd =prefs.getBoolean("preference_save_dxd_int_card",true);
        if(dxd && (AudioTagUtils.isDSD(metadata))) {
            return true;
        }
        /*
        Set<String> defaults = new HashSet<>();
        String []vals =context.getResources().getStringArray(R.array.AudioFormatINTDefaultValues);
        defaults.addAll(Arrays.asList(vals));
        Set<String> formats = prefs.getStringSet("preference_save_format_on_int_card", defaults);
        for (String format: formats) {
            if(format.equalsIgnoreCase(metadata.getAudioFormat())) {
                return true;
            }
        } */

        Set<String> defaults = new HashSet<>();
        String []vals =context.getResources().getStringArray(R.array.MusicGroupINTDefaultValues);
        defaults.addAll(Arrays.asList(vals));
        Set<String> groups = prefs.getStringSet("preference_save_grouping_on_int_card", defaults);
        for (String group: groups) {
            if(group.equalsIgnoreCase(metadata.getGrouping())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isShowPCMAudio(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_PCM_AUDIO_IN_COLLECTION,false);
    }

    public static boolean setPersistableUriPermission(Context context, String rootPath, Uri uri) {
        DocumentFile file = DocumentFile.fromTreeUri(context, uri);
        if (file != null && file.canWrite()) {
            SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);
            String pathMap =perf.getString("PersistableUriPermissions", "{}");
            try {
                JSONObject json = new JSONObject(pathMap);
                json.put(rootPath, uri.getPath());
                perf.edit().putString("PersistableUriPermissions", json.toString()).apply();

                /*
                List<UriPermission> perms = context.getContentResolver().getPersistedUriPermissions();
                int i =0;
                for (UriPermission perm:perms) {
                    if(perm.getUri().equals(uri)) {
                        JSONObject json = new JSONObject(pathMap);
                        json.put(rootPath, i);
                        perf.edit().putString("PersistableUriPermissions", json.toString()).apply();
                        break;
                    }
                    i++;
                }*/
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            Timber.e(TAG, "no write permission: %s", rootPath);
        }
        return false;
    }

    public static Uri getPersistableUriPermission(Context context, String path) {
        if (path != null) {
            SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);
            String pathMap =perf.getString("PersistableUriPermissions", "{}");
            try {
                List<UriPermission> perms = context.getContentResolver().getPersistedUriPermissions();
                JSONObject json = new JSONObject(pathMap);
                JSONArray array = json.names();
                for (int i=0; i< array.length();i++) {
                    String name = array.getString(i);
                   // String uriPath =
                    if(path.startsWith(name)) {
                        String storagePath = json.getString(name);
                       /// if(storagePath.endsWith(":")) {
                       //     storagePath = storagePath.substring(0, storagePath.length()-1);
                       // }
                        for (UriPermission perm:perms) {
                            String permPath = perm.getUri().getPath();
                            if(permPath.equals(storagePath)) {
                                return perm.getUri();
                            }
                        }
                        //return perms.get(index).getUri();
                    }
                }
            } catch (JSONException e) {
                Timber.e(TAG, "no write permission: %s", path);
                e.printStackTrace();
            }
        } else {
            Timber.e(TAG, "no write permission: %s", path);
        }
        return null;
    }
/*
    public static boolean isShowDSDAudio(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_DSD_AUDIO_IN_COLLECTION,true);
    }

    public static boolean isShowMQAAudio(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_MQA_AUDIO_IN_COLLECTION,true);
    }

    public static boolean isShowAudioSampleRate(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_SHOW_AUDIO_SAMPLE_RATE_IN_COLLECTION,true);
    }*/
}