package apincer.android.mmate;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by e1022387 on 3/12/2018.
 */

public final class Constants {
    public static final String QUALITY_AUDIOPHILE = "Audiophile";
    public static final String QUALITY_RECOMMENDED = "Recommended";
    public static final String QUALITY_POOR = "Poor";
    public static final String QUALITY_NORMAL = "Regular";
    public static final String FILTER_TYPE_ARTIST = "Artist";
    public static final String FILTER_TYPE_ALBUM = "Album";
    public static final String FILTER_TYPE_GENRE = "Genre";
    public static final String FILTER_TYPE_GROUPING = "Grouping";
    public static final String FILTER_TYPE_PUBLISHER = "Publisher";
    public static final String FILTER_TYPE_PATH = "Folder";
    public static final String FILTER_TYPE_ALBUM_ARTIST = "Album Artist";
    public static final String DEFAULT_ALBUM_TEXT = "Single";

    public static final long QUALITY_SAMPLING_RATE_192 = 192000;
    public static final long QUALITY_SAMPLING_RATE_88 = 88200;
    public static final int QUALITY_SAMPLING_RATE_48 = 48000;
    public static final int QUALITY_SAMPLING_RATE_44 = 44100;
    public static final int QUALITY_BIT_DEPTH_HD = 24;
    public static final int QUALITY_BIT_DEPTH_DSD = 1;
    public static final String MEDIA_FILE_FORMAT_WAVE = "wav";
    public static final String MEDIA_FILE_FORMAT_FLAC = "flac";
    public static final String MEDIA_FILE_FORMAT_AIF = "aif";
    public static final String MEDIA_FILE_FORMAT_AIFF = "aiff";
    public static final String MEDIA_FILE_FORMAT_M4A = "m4a";
    public static final String MEDIA_FILE_FORMAT_AAC = "aac";
    public static final String MEDIA_FILE_FORMAT_ALAC = "alac";
    public static final String MEDIA_FILE_FORMAT_DSF="dsf";
    public static final String MEDIA_FILE_FORMAT_MP3="mp3";
    public static final int MIN_FILE_SIZE_RATIO = 42;
    public static final String UNKNOWN_PUBLISHER = "Unknown Publisher";
    public static final String UNKNOWN_GENRE = "Unknown Genre";
    public static final String UNKNOWN_MEDIA_TYPE = "Unknown Media Type";
    public static final String PREF_NIGHT_MODE_ONLY = "preference_night_mode_only";
    public static final String GROUPING_LOUNGE = "Lounge";
    public static final String GROUPING_LIVE = "Live";
    public static String FIELD_SEP = ";";
   public static double MIN_TITLE = 0.80;
   public static double MIN_ARTIST = 0.70;

    public static final String REPLAYGAIN_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN";
    public static final String REPLAYGAIN_TRACK_RANGE = "REPLAYGAIN_TRACK_RANGE";
    public static final String REPLAYGAIN_REFERENCE_LOUDNESS = "REPLAYGAIN_REFERENCE_LOUDNESS";

    public static final List<String> IMAGE_COVERS = new ArrayList<>();
    static {
        IMAGE_COVERS.add("front.png");
        IMAGE_COVERS.add("cover.png");
        IMAGE_COVERS.add("folder.png"); // not supported by UAPP
        IMAGE_COVERS.add("artwork.png"); // supported by UAAP
        IMAGE_COVERS.add("front.jpg"); // support by neplayer
        IMAGE_COVERS.add("cover.jpg");
        IMAGE_COVERS.add("folder.jpg"); // not supported by UAPP
        IMAGE_COVERS.add("artwork.jpg"); // UAAP
     //   IMAGE_COVERS.add("front.jpeg");
     //   IMAGE_COVERS.add("cover.jpeg");
      //  IMAGE_COVERS.add("folder.jpeg"); // not supported by UAPP
    }

    public static final List<String> RELATED_FILE_TYPES = new ArrayList<>();
    static {
        RELATED_FILE_TYPES.add("png");
        RELATED_FILE_TYPES.add("jpg");
        RELATED_FILE_TYPES.add("jpeg");
       // RELATED_FILE_TYPES.add("cue");
        RELATED_FILE_TYPES.add("lrc");
        RELATED_FILE_TYPES.add("pdf");
        RELATED_FILE_TYPES.add("md5");
        RELATED_FILE_TYPES.add("txt");
        RELATED_FILE_TYPES.add("json"); // tags, override to embed iso
    }

    public static String STATUS_SUCCESS="success";
    public static String STATUS_FAIL="fail";
    public static String KEY_SEARCH_TYPE="search_criteria_type";
    public static String KEY_SEARCH_KEYWORD="search_criteria_keyword";
    public static String KEY_FILTER_TYPE="search_filter_type";
    public static String KEY_FILTER_KEYWORD="search_filter_keyword";
    public static final String KEY_MEDIA_TAG = "mediaTAG";
    public static final String KEY_COVER_ART_PATH = "coverArtPath";
    public static final String MEDIA_ENC_AAC="AAC";
    public static final String MEDIA_ENC_MP3="MPEG";
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
   // public static final String MEDIA_PATH_HR = "Hi-Res"; //Hi-Res
   // public static final String MEDIA_PATH_HRMS = "Hi-Res Master";
   // public static final String MEDIA_PATH_ALAC = "ALAC"; //Lossless
   // public static final String MEDIA_PATH_FLAC = "FLAC"; //Lossless
   // public static final String MEDIA_PATH_WAVE = "WAVE";
   // public static final String MEDIA_PATH_AIFF = "AIFF";
   // public static final String MEDIA_PATH_ACC="AAC";
   // public static final String MEDIA_PATH_MP3="MP3";
   // public static final String MEDIA_PATH_OTHER ="Others";

    public static final String AUDIO_SQ_DSD = "DSD"; // DSD
    public static final String AUDIO_SQ_PCM_MQA = "MQA"; //MQA
    public static final String AUDIO_SQ_PCM = "PCM";

    //public static final String TITLE_RECORDING_QUALITY = "Recording Quality";
    public static final String TITLE_DUPLICATE = "Duplicate";
    public static final String TITLE_BROKEN = "Broken";
    //public static final String TITLE_AUDIOPHILE = "Audiophile";
    public static final String TITLE_INCOMING_SONGS = "My Download";
    public static final String TITLE_ALL_SONGS = "My Songs";
    public static final String TITLE_DSD_AUDIO = "Direct Stream Digital";
    public static final String TITLE_HIRES = "Hi-Res Lossless";
    public static final String TITLE_HIFI_LOSSLESS = "Lossless";
    public static final String TITLE_HI_QUALITY = "High Quality";
    public static final String TITLE_MASTER_AUDIO = "Master Recordings";
    public static final String TITLE_MASTER_STUDIO_AUDIO = "Master Studio Recordings";

    public static final String PREF_NEXT_SONG_BY_MEDIA_BUTTONS = "preference_default_next_by_media_buttons";
    public static final String PREF_VIBRATE_ON_NEXT_SONG = "preference_vibrate_on_next_song";
   // public static final String PREF_SHOW_GROUPINGS_IN_COLLECTION = "preference_show_groupings_in_collection";
   // public static final String PREF_SHOW_NOTIFICATION = "preference_notification";
    public static final String PREF_PREFIX_TRACK_NUMBER_ON_TITLE = "preference_prefix_title_with_track_number";
    public static final String PREF_SHOW_STORAGE_SPACE = "preference_show_storage_space";
    public static final String PREF_FOLLOW_NOW_PLAYING = "preference_follow_now_playing";
    public static final String PREF_OPEN_NOW_PLAYING = "preference_open_now_playing";

   // public static final int INFO_RESOLUTIONS_WIDTH = 280;
   // public static final int INFO_RESOLUTIONS_HEIGHT = 28;
   // public static final int INFO_SAMPLE_RATE_WIDTH = 180; //164;
   // public static final int INFO_HEIGHT = 32;

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
    public static final String MEDIA_TYPE_NONE = "-";

    public static List<String> getSourceList(Context context) {
    List list = new ArrayList<>();
     String[] srcs =  context.getResources().getStringArray(R.array.default_mediaType);
     list.addAll(Arrays.asList(srcs));
     Collections.sort(list);

    return list;
 }
}
