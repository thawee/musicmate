package apincer.android.mmate;

import java.util.ArrayList;
import java.util.List;

public final class Constants {
    public static final String QUALITY_AUDIOPHILE = "Audiophile";
    public static final String QUALITY_RECOMMENDED = "Recommended";
    public static final String QUALITY_BAD = "Unsatisfactory";
    public static final String QUALITY_FAVORITE = "Favorite";
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

    // public static final long QUALITY_SAMPLING_RATE_192 = 192000;
   public static final long QUALITY_SAMPLING_RATE_96 = 96000;
   // public static final long QUALITY_SAMPLING_RATE_88 = 88200;
    public static final int QUALITY_SAMPLING_RATE_48 = 48000;
    public static final int QUALITY_SAMPLING_RATE_44 = 44100;
    public static final int QUALITY_BIT_DEPTH_HD = 24;
  //  public static final int QUALITY_BIT_CD = 16;
    public static final int QUALITY_BIT_DEPTH_DSD = 1;

   public static final String UNKNOWN = "Unknown";
    public static final String NONE = "Unknown";
    public static final String EMPTY = "";
    public static final String ARTIST_SEP = ",";
    public static final String ARTIST_SEP_SPACE = ", ";

    public static final String GROUPING_CLASSICAL = "Classical";
    public static final String GROUPING_TRADITIONAL = "Traditional";
    public static final String GROUPING_LOUNGE = "Lounge";
    public static final String GROUPING_CONTEMPORARY = "Contemporary";
    public static final String GROUPING_OLDEIS = "Oldies";

    public static final String TITLE_LIBRARY = "Library"; //""Collections";
    public static final String TITLE_PLAYLIST = "Playlists";
    public static final String TITLE_RESOLUTION = "Resolutions";
    public static final String TITLE_GROUPING = "Groupings";
    public static final String TITLE_GENRE = "Genres";
    public static final String TITLE_ARTIST = "Artists";
    public static final String TITLE_NO_COVERART = "No Embed Coverart";

    public static final String TITLE_DUPLICATE = "Similar Songs";
    //public static final String TITLE_BROKEN = "Quality Issues"; //	"Unsatisfactory"	"Needs Attention" or "Quality Issues"
    public static final String TITLE_TO_ANALYST_DR = "Pending Analysis";
    public static final String TITLE_INCOMING_SONGS = "Recently Added";
    public static final String TITLE_ALL_SONGS = "All Songs";
    public static final String TITLE_DSD = "DSD Audio"; //""Direct Stream Digital";
    public static final String TITLE_HIRES = "High Resolution"; //""Hi-Res Lossless";
    public static final String TITLE_HIFI_LOSSLESS = "Standard Quality"; //""Hi-Fi Lossless";
    public static final String TITLE_HIGH_QUALITY = "Lossy Codec"; //""High Quality";
    public static final String TITLE_MASTER_AUDIO = "Master Quality Authenticated"; //""Studio Masters";  // Industry standard term //"Master Recordings";
    //public static final String TITLE_MASTER_STUDIO_AUDIO = "Master Studio Recordings";

   // public static final String TITLE_PCM = "PCM";
   public static final String TITLE_HIRES_SHORT = "HR";
    public static final String TITLE_HIFI_LOSSLESS_SHORT = "SQ";
    public static final String TITLE_DSD_SHORT = "DSD";
    public static final String TITLE_MQA_SHORT = "MQA";
    public static final String TITLE_HIGH_QUALITY_SHORT = "LC"; //Lossy Codec

 //   public static final String FIELD_SEP = ";";
  //  public static final double MIN_TITLE = 0.80;
  //  public static final double MIN_ARTIST = 0.70;

    public static final List<String> IMAGE_COVERS = new ArrayList<>();
    public static final List<String> RELATED_FILE_TYPES = new ArrayList<>();
    public static final String PREF_LAST_SCAN_TIME = "LAST_SCAN_TIME_PREF";

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

   // public static final String STATUS_SUCCESS="success";
    //public static final String STATUS_FAIL="fail";
    public static final String KEY_SEARCH_TYPE="search_criteria_type";
    public static final String KEY_SEARCH_KEYWORD="search_criteria_keyword";
    public static final String KEY_FILTER_TYPE="search_filter_type";
    public static final String KEY_FILTER_KEYWORD="search_filter_keyword";
   // public static final String KEY_COVER_ART_PATH = "coverArtPath";

    public static final String MEDIA_ENC_AAC="AAC";
    public static final String MEDIA_ENC_MPEG="MPEG";
    public static final String MEDIA_ENC_FLAC="FLAC";
    public static final String MEDIA_ENC_ALAC="ALAC";
    public static final String MEDIA_ENC_WAVE = "WAVE";
    public static final String MEDIA_ENC_DSF = "DSF";
    public static final String MEDIA_ENC_DFF = "DFF";
    public static final String MEDIA_ENC_AIFF = "AIFF";
    public static final String MEDIA_ENC_AIFF_ALT = "AIF";
    public static final String MEDIA_ENC_SACD = "SACD";
    public static final String MEDIA_ENC_MQA = "MQA";

    public static final String PREF_NEXT_SONG_BY_MEDIA_BUTTONS = "preference_default_next_by_media_buttons";
   // public static final String PREF_VIBRATE_ON_NEXT_SONG = "preference_vibrate_on_next_song";
    public static final String PREF_PREFIX_TRACK_NUMBER_ON_TITLE = "preference_prefix_title_with_track_number";
    public static final String PREF_SHOW_STORAGE_SPACE = "preference_show_storage_space";
   // public static final String PREF_NIGHT_MODE_ONLY = "preference_night_mode_only";
    public static final String PREF_LIST_FOLLOW_NOW_PLAYING = "preference_list_follows_now_playing";
    public static final String PREF_MUSICMATE_DIRECTORIES = "preference_musicmate_directories";
    //public static final String PREF_MUSICMATE_NEXT_STEP = "preference_musicmate_next_step";
    public static final String PREF_ENABLE_MEDIA_SERVER = "preference_dlna_media_server";
    public static final String PREF_MEDIA_SERVER_UUID_KEY = "preference_dlna_media_server_uuid_key";
    public static final String PREF_NETTY_MEDIA_SERVER = "preference_netty_media_server";

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

    public static final String COVER_ARTS = "/Covers/";
    public static final String DEFAULT_COVERART_FILE = "default_coverart.png";
    public static final String DEFAULT_COVERART_DLNA_RES = "no_cover.png";
}
