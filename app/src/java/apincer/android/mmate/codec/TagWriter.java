package apincer.android.mmate.codec;

import android.content.Context;

import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public abstract class TagWriter {
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
        private final String fileSuffix;

        /**
         * User Friendly Name
         */
        private final String displayName;

        /** Constructor for internal use by this enum.
         */
        SupportedFileFormat(String filesuffix, String displayName)
        {
            this.fileSuffix = filesuffix;
            this.displayName = displayName;
        }

        /**
         *  Returns the file suffix (lower case without initial .) associated with the format.
         */
        public String getFileSuffix()
        {
            return fileSuffix;
        }


        public String getDisplayName()
        {
            return displayName;
        }
    }

    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            TagReader.SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    public static boolean writeTagToFile(Context context, MusicTag tag) {
        return getTagWriter(context, tag).writeTag(tag);
    }

    protected abstract boolean writeTag(MusicTag tag);

    private static TagWriter getTagWriter(Context context, MusicTag tag) {
       // return new FFMpegWriter(context);
        return new JThinkWriter(context);
    }
}
