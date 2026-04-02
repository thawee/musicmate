package apincer.music.core.codec;

import android.content.Context;

import apincer.music.core.model.Track;
import apincer.music.core.utils.StringUtils;
import apincer.android.utils.FileUtils;

public abstract class TagWriter {

    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            TagReader.SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    public static void writeTagToFile(Context context, Track tag) {
        getTagWriter(context, tag).writeTag(tag);
    }

    protected abstract void writeTag(Track tag);

    private static TagWriter getTagWriter(Context context, Track tag) {
       // return new FFMpegWriter(context);
        return new JThinkWriter(context);
    }
}
