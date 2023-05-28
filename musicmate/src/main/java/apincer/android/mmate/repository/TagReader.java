package apincer.android.mmate.repository;

import android.content.Context;

import java.util.List;

import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public abstract class TagReader {
    public static TagReader getReader(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            if("flac".equalsIgnoreCase(ext)) {
                return new JustFLACReader();
            }else {
                return new FFMPeg();
            }
        }catch(Exception ex) {
            return null;
        }
    }

    public abstract List<MusicTag> readMusicTag(Context context, String mediaPath);
}
