package apincer.android.mmate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by e1022387 on 3/12/2018.
 */

public final class Constants {
   // public static final long QUALITY_SAMPLING_RATE_DSD256 = 2822400; // 64*44.1 kHz
   // public static final long QUALITY_SAMPLING_RATE_DSD128 = 2822400; // 64*44.1 kHz
    public static final long QUALITY_SAMPLING_RATE_DSD64 = 2822400; // 64*44.1 kHz
    public static final String AUDIOPHILE = "Audiophile";
   // public static final String KEY_FILTER_TYPE = "KEY_FILTER_TYPE";
   // public static final String KEY_FILTER_VALUE = "KEY_FILTER_VALUE";
    public static final String FILTER_TYPE_ARTIST = "Artist";
    public static final String FILTER_TYPE_ALBUM = "Album";
    public static final String FILTER_TYPE_GENRE = "Genre";
    public static final String FILTER_TYPE_GROUPING = "Grouping";
    public static final String FILTER_TYPE_PATH = "Folder";
    public static final String FILTER_TYPE_ALBUM_ARTIST = "AlbumArtist";
    public static final String DEFAULT_ALBUM_TEXT = "Single";


    public static int QUALITY_SAMPLING_RATE_384 = 384000;
    public static int QUALITY_SAMPLING_RATE_352 = 352000;
    public static int QUALITY_SAMPLING_RATE_192 = 192000;
    public static int QUALITY_SAMPLING_RATE_176 = 176000;
    public static int QUALITY_SAMPLING_RATE_96 = 96000;
    public static int QUALITY_SAMPLING_RATE_88 = 88200;
    public static int QUALITY_SAMPLING_RATE_48 = 48000;
    public static int QUALITY_SAMPLING_RATE_44 = 44100;
   // public static int QUALITY_SAMPLING_RATE_48_KHZ = 48;
    public static int QUALITY_BIT_DEPTH_HD = 24;
    public static int QUALITY_BIT_DEPTH_SD = 16;
    public static int QUALITY_BIT_DEPTH_DSD = 1;
    public static String FIELD_SEP = ";";
  //  public static final String HEADER_SEP = " :: ";
 //public static final String HEADER_CNT_PREFIX = " (";
 //public static final String HEADER_CNT_SUFFIX = ")";

 /* public static double MIN_TITLE_ONLY = 0.80;
    public static double MIN_TITLE = 0.70;
    public static double MIN_ARTIST = 0.60;
*/
   public static double MIN_TITLE_ONLY = 0.90;
   public static double MIN_TITLE = 0.80;
   public static double MIN_ARTIST = 0.70;

    public static final List<String> IMAGE_COVERS = new ArrayList();
    static {
        IMAGE_COVERS.add("front.png");
        IMAGE_COVERS.add("cover.png");
        IMAGE_COVERS.add("folder.png");
        IMAGE_COVERS.add("front.jpg");
        IMAGE_COVERS.add("cover.jpg");
        IMAGE_COVERS.add("folder.jpeg");
        IMAGE_COVERS.add("front.jpeg");
        IMAGE_COVERS.add("cover.jpeg");
        IMAGE_COVERS.add("folder.jpeg");
    }

    public static final List<String> COVER_IMAGE_TYPES = new ArrayList();
    static {
        COVER_IMAGE_TYPES.add("png");
        COVER_IMAGE_TYPES.add("jpg");
        COVER_IMAGE_TYPES.add("jpeg");
    }

    public static final List<String> RELATED_FILE_TYPES = new ArrayList();
    static {
        RELATED_FILE_TYPES.add("png");
        RELATED_FILE_TYPES.add("jpg");
        RELATED_FILE_TYPES.add("jpeg");
        RELATED_FILE_TYPES.add("cue");
        RELATED_FILE_TYPES.add("lrc");
        RELATED_FILE_TYPES.add("pdf");
        RELATED_FILE_TYPES.add("md5");
    }

    public static final String COMMAND_CLEAN_DB = "cleanDatabase";
    public static String COMMAND_DELETE="delete";
    public static String COMMAND_MOVE="move";
    public static String COMMAND_SAVE="save";
    public static String COMMAND_SCAN="scan";
    public static String COMMAND_SCAN_FULL="scanFull";
    public static String STATUS_SUCCESS="success";
    public static String STATUS_FAIL="fail";
    public static String STATUS_START="start";
    public static String KEY_SEARCH_TYPE="search_criteria_type";
    public static String KEY_SEARCH_KEYWORD="search_criteria_keyword";
    public static String KEY_FILTER_TYPE="search_filter_type";
    public static String KEY_FILTER_KEYWORD="search_filter_keyword";
   // public static String KEY_SEARCH_CRITERIA="search_criteria";
    public static String KEY_COMMAND="command";
    public static String KEY_STATUS="status";
    public static String KEY_MESSAGE="message";
    public static String KEY_SUCCESS_COUNT="successCount";
    public static String KEY_PENDING_TOTAL="pendingTotal";
    public static String KEY_ERROR_COUNT="errorCount";
    public static String KEY_RESULT_CODE="resultCode";
    public static final String KEY_MEDIA_TAG = "mediaTAG";
  //  public static final String KEY_MEDIA_PRV_TAG ="mediaPRVTAG";
  //  public static final String KEY_MEDIA_TAG_LIST = "mediaTAGLIST";
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

   // public static final String MEDIA_FMT_M4A="M4A";
   // public static final String MEDIA_FMT_MP3="MP3";

    public static final String MEDIA_PATH_DSD = "DSD"; // DSD
    public static final String MEDIA_PATH_MQA = "MQA"; //MQA
   // public static final String MEDIA_PATH_HR = "Hi-Res"; //Hi-Res
   // public static final String MEDIA_PATH_HRMS = "Hi-Res Master";
    public static final String MEDIA_PATH_ALAC = "ALAC"; //Lossless
    public static final String MEDIA_PATH_FLAC = "FLAC"; //Lossless
    public static final String MEDIA_PATH_WAVE = "WAVE";
    public static final String MEDIA_PATH_AIFF = "AIFF";
    public static final String MEDIA_PATH_ACC="AAC";
    public static final String MEDIA_PATH_MP3="MP3";
    public static final String MEDIA_PATH_OTHER ="Others";

    public static final String AUDIO_SQ_DSD = "DSD"; // DSD
    public static final String AUDIO_SQ_PCM_MQA = "MQA"; //MQA
   // public static final String AUDIO_SQ_HIRES = "Hi-Res Audio"; //Hi-Res
    //public static final String AUDIO_SQ_HIRES_LOSSLESS = "Hi-Res Lossless"; //Hi-Res
    //public static final String AUDIO_SQ_HIRES_MASTER = "Hi-Res Master";
//    public static final String AUDIO_SQ_HIFI = "Hi-Fi";
   // public static final String AUDIO_SQ_HIFI_LOSSLESS = "Lossless Audio";
   // public static final String AUDIO_SQ_HIFI_QUALITY = "High Quality";
    public static final String AUDIO_SQ_PCM = "PCM";

    public static final String TITLE_DUPLICATE = "Duplicate Songs";
    public static final String TITLE_AUDIOPHILE = "Audiophile";
    public static final String TITLE_INCOMING_SONGS = "My Download";
    public static final String TITLE_ALL_SONGS = "My Songs";
    public static final String TITLE_DSD_AUDIO = "Direct Stream Digital";
    //public static final String TITLE_HR_LOSSLESS = "High-Res Audio";
    //public static final String TITLE_HR_MASTER="High-Res Master";
    public static final String TITLE_MQA_AUDIO = "Master Quality Authenticated";
    public static final String TITLE_HIRES = "High-Res Audio";
    public static final String TITLE_HIFI_LOSSLESS = "Lossless Audio";
    public static final String TITLE_HIFI_QUALITY = "High Quality";

  //  public static final String PREF_TAG_ENCODING = "preference_matadata_encodings";
    public static final String PREF_NEXT_SONG_BY_MEDIA_BUTTONS = "preference_default_next_by_media_buttons";
    public static final String PREF_VIBRATE_ON_NEXT_SONG = "preference_vibrate_on_next_song";
   // public static final String PREF_SHOW_AUDIO_FORMAT_IN_COLLECTION = "preference_show_format_in_collection";
   // public static final String PREF_SHOW_SAMPLING_RATE_IN_COLLECTION = "preference_show_samplingrate_in_collection";
   // public static final String PREF_SHOW_SONG_GENRE_IN_COLLECTION = "preference_show_genre_in_collection";
    public static final String PREF_SHOW_GROUPINGS_IN_COLLECTION = "preference_show_groupings_in_collection";
    public static final String PREF_SHOW_NOTIFICATION = "preference_notification";
   // public static final String PREF_SIMILAR_ON_TITLE_AND_ARTIST = "preference_similar_title_artist";
  //  public static final String PREF_SHOW_AUDIO_QUALITY_IN_COLLECTION = "preference_show_sq_in_collection";
    public static final String PREF_PREFIX_TRACK_NUMBER_ON_TITLE = "preference_prefix_title_with_track_number";
    public static final String PREF_SHOW_STORAGE_SPACE = "preference_show_storage_space";
    public static final String PREF_FOLLOW_NOW_PLAYING = "preference_follow_now_playing";
    public static final String PREF_OPEN_NOW_PLAYING = "preference_open_now_playing";
   // public static final String PREF_SHOW_MQA_AUDIO_IN_COLLECTION = "preference_show_mqa_in_collection";
   // public static final String PREF_SHOW_PCM_AUDIO_IN_COLLECTION = "preference_show_pcm_in_collection";
   // public static final String PREF_SHOW_AUDIO_SAMPLE_RATE_IN_COLLECTION = "preference_show_sample_rate_in_collection";

    public static final int INFO_SAMPLE_RATE_WIDTH = 180; //164;
    public static final int INFO_HEIGHT = 32;

    // Source
    public static final String SRC_JOOX = "Joox";
 public static final String SRC_QOBUZ = "Qobuz";
 public static final String SRC_CD = "CD";
 public static final String SRC_SACD = "SACD";
  public static final String SRC_VINYL = "VINYL";
 public static final String SRC_APPLE = "Apple";
 public static final String SRC_SPOTIFY = "Spotify";
 public static final String SRC_TIDAL = "Tidal";
 public static final String SRC_YOUTUBE = "Youtube";
    public static final String SRC_2L = "2L";
    public static final String SRC_HD_TRACKS = "HDTracks";
    public static final String SRC_NATIVE_DSD = "NativeDSD";
 public static final String SRC_NONE = "-";

 public static List<String> getSourceList() {
    List list = new ArrayList<>();
     list.add(SRC_2L);
     list.add(SRC_APPLE);
     list.add(SRC_CD);
     list.add(SRC_HD_TRACKS);
     list.add(SRC_JOOX);
     list.add(SRC_NATIVE_DSD);
    list.add(SRC_QOBUZ);
     list.add(SRC_SACD);
     list.add(SRC_SPOTIFY);
    list.add(SRC_TIDAL);
   // list.add(SRC_YOUTUBE);
     list.add(SRC_VINYL);
     list.add(SRC_NONE);
    return list;
 }
}
