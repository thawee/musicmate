package apincer.android.mmate.dlna;

import org.jupnp.util.MimeType;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;

public class MimeDetector {
    public static MimeType getMimeType(MusicTag tag) {
            return new MimeType("audio", getMimeTypeString(tag));
    }

    public static String getMimeTypeString(MusicTag tag) {
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

}
