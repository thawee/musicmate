package apincer.android.mmate.dlna;

import org.jupnp.util.MimeType;

import java.util.HashMap;
import java.util.Map;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.utils.FileUtils;

public class MediaTypeDetector {
    private static final String defaultType = "application/octet-stream";
    private static final Map<String, String> contentTypes = new HashMap<>();
    static  {
        contentTypes.put("aif", "audio/x-aiff");
        contentTypes.put("aifc", "audio/x-aiff");
        contentTypes.put("aiff", "audio/x-aiff");
        contentTypes.put("aac", "audio/aac");
        contentTypes.put("flac", "audio/x-flac");
        contentTypes.put("mp3", "audio/x-mpeg");
        contentTypes.put("wav", "audio/x-wav");
        contentTypes.put("wma", "audio/x-ms-wma");
        contentTypes.put("m4a", "audio/x-mp4");
        contentTypes.put("wave", "audio/x-wav");
        contentTypes.put("jpg", "image/jpeg");
        contentTypes.put("jpeg", "image/jpeg");
        contentTypes.put("png", "image/png");
    }
    public static MimeType getMimeType(MusicTag tag) {
            return new MimeType("audio", getContentType(tag));
    }

    public static String getContentType(MusicTag tag) {
        if(MusicTagUtils.isAIFFile(tag)) {
            return "x-aiff";
        }else  if(MusicTagUtils.isMPegFile(tag)) {
            return "mpeg";
        }else  if(MusicTagUtils.isFLACFile(tag)) {
            return "flac";
        }else  if(MusicTagUtils.isALACFile(tag)) {
            return "mp4";
            //  }else  if(MusicTagUtils.isMp4File(tag)) {
            //      return new MimeType("audio", "aac");
        }else  if(MusicTagUtils.isWavFile(tag)) {
            return "wave";
        }else {
            return tag.getAudioEncoding();
        }
    }

    public static String getContentType(String filename) {
        String ext = FileUtils.getExtension(filename);
        if(contentTypes.containsKey(ext)) {
            return contentTypes.get(ext);
        }
        return defaultType;
    }
}
