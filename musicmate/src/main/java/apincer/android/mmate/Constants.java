package apincer.android.mmate;

import android.content.Context;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.utils.ApplicationUtils;

/**
 * Created by e1022387 on 3/12/2018.
 */

public final class Constants {
    public static final String QUALITY_AUDIOPHILE = "Audiophile";
    public static final String QUALITY_RECOMMENDED = "Recommended";
    public static final String QUALITY_BAD = "Bad";
    public static final String QUALITY_GOOD = "Good";
    public static final String FILTER_TYPE_ARTIST = "Artist";
    public static final String FILTER_TYPE_ALBUM = "Album";
    public static final String FILTER_TYPE_GENRE = "Genre";
    public static final String FILTER_TYPE_GROUPING = "Grouping";
    public static final String FILTER_TYPE_PUBLISHER = "Publisher";
    public static final String FILTER_TYPE_PATH = "Folder";
    public static final String FILTER_TYPE_ALBUM_ARTIST = "Album Artist";
    public static final String DEFAULT_ALBUM_TEXT = "Single";

    public static final int FLAC_OPTIMAL_COMPRESS_LEVEL = 4;
    public static final int FLAC_NO_COMPRESS_LEVEL = 0;

  //  public static final double SPL_8BIT_IN_DB = 49.8;
    public static final double SPL_16BIT_IN_DB = 96.33;
    public static final double SPL_24BIT_IN_DB = 144.49;

    public static final double MIN_SPL_RATIO = 0.9; //0.72; //just magic number, it's should be 1.0 for actual results
    public static final double MIN_SPL_16BIT_IN_DB = SPL_16BIT_IN_DB*MIN_SPL_RATIO;
    public static final double MIN_SPL_24BIT_IN_DB = SPL_24BIT_IN_DB*MIN_SPL_RATIO;

   // public static final long QUALITY_SAMPLING_RATE_192 = 192000;
   public static final long QUALITY_SAMPLING_RATE_96 = 96000;
   // public static final long QUALITY_SAMPLING_RATE_88 = 88200;
    public static final int QUALITY_SAMPLING_RATE_48 = 48000;
    public static final int QUALITY_SAMPLING_RATE_44 = 44100;
    public static final int QUALITY_BIT_DEPTH_HD = 24;
    public static final int QUALITY_BIT_CD = 16;
    public static final int QUALITY_BIT_DEPTH_DSD = 1;

    public static final String FILE_EXT_WAVE = "wav";
    public static final String FILE_EXT_AIF = "aif";
    public static final String FILE_EXT_AIFF = "aiff";
    public static final String FILE_EXT_DSF="dsf";
    public static final String FILE_EXT_MP3="mp3";
   // public static final int MIN_FILE_SIZE_RATIO = 42;
    public static final String UNKNOWN_PUBLISHER = "Unknown Publisher";
    public static final String UNKNOWN_GENRE = "Unknown Genre";
  //  public static final String UNKNOWN_MEDIA_TYPE = "Unknown Media Type";
    public static final String UNKNOWN_GROUP = "Unknown";
    public static final String UNKNOWN = "Unknown";
    public static final String NONE = "None";
    public static final String EMPTY = "";

    public static final String GROUPING_LOUNGE = "Lounge";
    public static final String GROUPING_ACOUSTIC = "Acoustic";
    public static final String GROUPING_THAI_LOUNGE = "Thai Lounge";
    public static final String GROUPING_THAI_ACOUSTIC = "Thai Acoustic";

    public static final String TITLE_LIBRARY = "Collections";
    public static final String TITLE_RESOLUTION = "Resolutions";
    public static final String TITLE_GROUPING = "Grouping";
    public static final String TITLE_GENRE = "Genre";
    public static final String TITLE_PUBLISHER = "Publisher";
    public static final String TITLE_PCM = "PCM";
    public static final String TITLE_QUALITY = "File Quality";
    public static final String TITLE_DSD = "DSD";
    public static final String TITLE_NO_COVERART = "No Embed Coverart";

    public static final String FIELD_SEP = ";";
    public static final double MIN_TITLE = 0.80;
    public static final double MIN_ARTIST = 0.70;

    public static final List<String> IMAGE_COVERS = new ArrayList<>();
    public static final List<String> RELATED_FILE_TYPES = new ArrayList<>();
    static {
        IMAGE_COVERS.add("front.png");
        IMAGE_COVERS.add("cover.png");
        IMAGE_COVERS.add("folder.png"); // not supported by UAPP
        IMAGE_COVERS.add("front.jpg"); // support by neplayer
        IMAGE_COVERS.add("cover.jpg");
        IMAGE_COVERS.add("folder.jpg"); // not supported by UAPP

        RELATED_FILE_TYPES.add("png");
        RELATED_FILE_TYPES.add("jpg");
        RELATED_FILE_TYPES.add("lrc");
        RELATED_FILE_TYPES.add("pdf");
        RELATED_FILE_TYPES.add("md5");
        RELATED_FILE_TYPES.add("txt");
        RELATED_FILE_TYPES.add("json"); // tags, override to embed iso

    }

    public static final String STATUS_SUCCESS="success";
    public static final String STATUS_FAIL="fail";
    public static final String KEY_SEARCH_TYPE="search_criteria_type";
    public static final String KEY_SEARCH_KEYWORD="search_criteria_keyword";
    public static final String KEY_FILTER_TYPE="search_filter_type";
    public static final String KEY_FILTER_KEYWORD="search_filter_keyword";
    public static final String KEY_COVER_ART_PATH = "coverArtPath";

    public static final String MEDIA_ENC_AAC="AAC";
    public static final String MEDIA_ENC_MPEG="MPEG";
    public static final String MEDIA_ENC_FLAC="FLAC";
    public static final String MEDIA_ENC_ALAC="ALAC";
    public static final String MEDIA_ENC_WAVE = "WAVE";
    public static final String MEDIA_ENC_DSF = "DSF";
    public static final String MEDIA_ENC_DFF = "DFF";
    public static final String MEDIA_ENC_AIFF = "AIFF";
    public static final String MEDIA_ENC_SACD = "SACD";

    public static final String MEDIA_PATH_DSD = "DSD"; // DSD
    public static final String MEDIA_PATH_MQA = "MQA"; //MQA
    public static final String MEDIA_PATH_HRA = "Hi-Res"; //HRA
    public static final String MEDIA_PATH_SACD = "SACD";
    public static final String MEDIA_PATH_HIFI = "Hi-Fi";
    public static final String MEDIA_PATH_HIGH_QUALITY = "HQ";

    public static final String AUDIO_SQ_DSD = "DSD"; // DSD
    public static final String AUDIO_SQ_PCM_MQA = "MQA"; //MQA
  //  public static final String AUDIO_SQ_PCM = "PCM";
    public static final String AUDIO_SQ_RESOLUTION = "Resolutions";

    public static final String TITLE_DUPLICATE = "Duplicate Song";
    public static final String TITLE_BROKEN = "Bad Upscale File";
    public static final String TITLE_NO_DR_METER = "No DR Meter";
    public static final String TITLE_INCOMING_SONGS = "My Download";
    public static final String TITLE_ALL_SONGS = "My Songs";
    public static final String TITLE_DSD_AUDIO = "Direct Stream Digital";
    public static final String TITLE_HIRES = "Hi-Res Lossless";
    public static final String TITLE_HIFI_LOSSLESS = "Hi-Fi Lossless";
    public static final String TITLE_HIGH_QUALITY = "High Quality";
    public static final String TITLE_MASTER_AUDIO = "Master Recordings";
    public static final String TITLE_MASTER_STUDIO_AUDIO = "Master Studio Recordings";

    public static final String PREF_NEXT_SONG_BY_MEDIA_BUTTONS = "preference_default_next_by_media_buttons";
    public static final String PREF_VIBRATE_ON_NEXT_SONG = "preference_vibrate_on_next_song";
    public static final String PREF_PREFIX_TRACK_NUMBER_ON_TITLE = "preference_prefix_title_with_track_number";
    public static final String PREF_SHOW_STORAGE_SPACE = "preference_show_storage_space";
    public static final String PREF_NIGHT_MODE_ONLY = "preference_night_mode_only";
    public static final String PREF_LIST_FOLLOW_NOW_PLAYING = "preference_list_follows_now_playing";
    public static final String PREF_MUSICMATE_DIRECTORIES = "preference_musicmate_directories";
    //public static final String PREF_MUSICMATE_NEXT_STEP = "preference_musicmate_next_step";
    public static final String PREF_ENABLE_MEDIA_SERVER = "preference_dlna_media_server";
    public static final String PREF_MEDIA_SERVER_UUID_KEY = "preference_dlna_media_server_uuid_key";

    // Source
    public static final String PUBLISHER_JOOX = "Joox";
    public static final String PUBLISHER_QOBUZ = "Qobuz";
    public static final String MEDIA_TYPE_CD = "CD";
    public static final String MEDIA_TYPE_SACD = "SACD";
    public static final String MEDIA_TYPE_VINYL = "VINYL";
    public static final String PUBLISHER_APPLE = "Apple";
    public static final String PUBLISHER_SPOTIFY = "Spotify";
    public static final String PUBLISHER_TIDAL = "Tidal";
    public static final String PUBLISHER_YOUTUBE = "Youtube";

    public static List<String> getSourceList(Context context) {
        String[] srcs =  context.getResources().getStringArray(R.array.default_mediaType);
        List<String> list = new ArrayList<>(Arrays.asList(srcs));
        Collections.sort(list);

    return list;
 }
}
