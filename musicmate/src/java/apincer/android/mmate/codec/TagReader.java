package apincer.android.mmate.codec;

import static org.jaudiotagger.audio.mp4.EncoderType.*;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;

import java.io.File;
import java.util.Locale;

import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public abstract class TagReader {

    public enum SupportedFileFormat {
        MP3,
        FLAC,
        M4A,
        WAV,
        AIF,
        AIFF,
        DSF;
       // DFF("dff", "Dff");

        /** Constructor for internal use by this enum.
         */
        SupportedFileFormat()  {
        }
    }

   protected static final String KEY_TAG_PUBLISHER = "PUBLISHER";

    protected static final String KEY_TAG_QUALITY = "QUALITY";
    protected static final String KEY_TAG_MQA_ENCODER = "MQAENCODER";
    protected static final String KEY_TAG_ORIGINALSAMPLERATE = "ORIGINALSAMPLERATE";

    protected static TagReader getReader(Context context, String path) {
        return new JThinkReader(context);
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

    protected String detectAudioEncoding(AudioFile read, AudioHeader header) {
        String encType = read.getExt();
        if(StringUtils.isEmpty(encType)) return "";

        if(APPLE_LOSSLESS.getDescription().equals(header.getEncodingType())) {
            encType = Constants.MEDIA_ENC_ALAC;
        }else if("m4a".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_AAC;
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
        tag.setFileType(FileUtils.getExtension(file).toLowerCase(Locale.US));

        tag.setSimpleName(DocumentFileCompat.getBasePath(context, tag.getPath()));
        tag.setStorageId(DocumentFileCompat.getStorageId(context, tag.getPath()));

        //set default, will be override by reader
        tag.setAudioEncoding(tag.getFileType());
        tag.setTitle(FileUtils.removeExtension(file.getName()));
    }

    protected static String extractField(String[] tags, int i) {
        if(tags.length>i) {
            return trimToEmpty(tags[i]);
        }
        return "";
    }

    public static MusicTag readBasicTag(Context context, String mediaPath) {
        return getReader(context, mediaPath).readBasicTag(mediaPath);
    }

    public static boolean readFullTag(Context context, MusicTag tag) {
        return getReader(context, tag.getPath()).readFullTag(tag);
    }

    public static boolean readExtras(Context context, MusicTag tag) {
        return getReader(context, tag.getPath()).readExtras(tag);
    }

    protected abstract MusicTag readBasicTag(String mediaPath);

    protected abstract boolean readFullTag(MusicTag tag);

    protected abstract boolean readExtras(MusicTag tag);
}
