package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.AudioFile;

import java.io.File;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public abstract class TagReader {
    public enum SupportedFileFormat
    {
        // OGG("ogg", "Ogg"),
        // OGA("oga", "Oga"),
        MP3("mp3", "Mp3"),
        FLAC("flac", "Flac"),
        //MP4("mp4", "Mp4"),
        M4A("m4a", "Mp4"),
        // M4P("m4p", "M4p"),
        // WMA("wma", "Wma"),
        WAV("wav", "Wav"),
        //  RA("ra", "Ra"),
        //  RM("rm", "Rm"),
        //  M4B("m4b", "Mp4"),
        AIF("aif", "Aif"),

        // APE("ape", "Ape"),
        AIFF("aiff", "Aif"),
        //  AIFC("aifc", "Aif Compressed"),
        DSF("dsf", "Dsf");
       // DFF("dff", "Dff");

        /**
         * File Suffix
         */
        private final String filesuffix;

        /**
         * User Friendly Name
         */
        private final String displayName;

        /** Constructor for internal use by this enum.
         */
        SupportedFileFormat(String filesuffix, String displayName)
        {
            this.filesuffix = filesuffix;
            this.displayName = displayName;
        }

        /**
         *  Returns the file suffix (lower case without initial .) associated with the format.
         */
        public String getFilesuffix()
        {
            return filesuffix;
        }


        public String getDisplayName()
        {
            return displayName;
        }
    }

    protected static final String KEY_MM_TRACK_DR_SCORE = "ZDRS";
    protected static final String KEY_MM_TRACK_DR = "ZDR";
    protected static final String KEY_MM_TRACK_UPSCALED = "ZUSC";
    protected static final String KEY_MM_TRACK_RESAMPLED = "ZRSP";
    protected static final String KEY_TAG_PUBLISHER = "PUBLISHER";
    protected static final String KEY_TAG_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN";
    protected static final String KEY_TAG_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"; // added by thawee
    protected static final String KEY_TAG_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN";
    protected static final String KEY_TAG_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"; // added by thawee
    protected static final String KEY_TAG_TRACK_LOUDNESS = "REPLAYGAIN_REFERENCE_LOUDNESS"; // for reset existing value

    protected static final String KEY_TAG_QUALITY = "QUALITY";
    protected static final String KEY_TAG_MEDIA = "MEDIA";

    protected static TagReader getReader(Context context, String path) {
       /* try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            if("flac".equalsIgnoreCase(ext)) {
                return new JustFLACReader();
            }else {
                return new FFMPeg();
            }
        }catch(Exception ex) {
            return null;
        } */
        return new JAudioTaggerReader(context);
    }

    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    protected String detectAudioEncoding(AudioFile read, boolean isLossless) {
        String encType = read.getExt();
        if(StringUtils.isEmpty(encType)) return "";

        if("m4a".equalsIgnoreCase(encType)) {
            if(isLossless) {
                encType = Constants.MEDIA_ENC_ALAC;
            }else {
                encType = Constants.MEDIA_ENC_AAC;
            }
        }else if("wav".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_WAVE;
        }else if("aif".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_AIFF;
        }else if("flac".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_FLAC;
        }else if("mp3".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_MPEG;
        }else if("dsf".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DSF;
        }else if("dff".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DFF;
        }else if("iso".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_SACD;
        }
        return  encType.toLowerCase(Locale.US);
    }


    protected static void readFileInfo(Context context, MusicTag tag) {
        File file = new File(tag.getPath());
        tag.setFileLastModified(file.lastModified());
        tag.setFileSize(file.length());
        tag.setFileFormat(FileUtils.getExtension(file).toLowerCase(Locale.US));
        tag.setSimpleName(DocumentFileCompat.getBasePath(context, tag.getPath()));
        tag.setStorageId(DocumentFileCompat.getStorageId(context, tag.getPath()));
    }

    protected static String extractField(String[] tags, int i) {
        if(tags.length>i) {
            return trimToEmpty(tags[i]);
        }
        return "";
    }

    public static List<MusicTag> readTag(Context context, String mediaPath) {
        return getReader(context, mediaPath).readTagsFromFile(mediaPath);
    }

    public static List<MusicTag> readTagFull(Context context, String mediaPath) {
        return getReader(context, mediaPath).readFullTagsFromFile(mediaPath);
    }

    protected abstract List<MusicTag> readTagsFromFile(String mediaPath);

    protected abstract List<MusicTag> readFullTagsFromFile(String mediaPath);
}
