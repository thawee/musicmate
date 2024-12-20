package apincer.android.mmate.utils;

import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.repository.MusicTag;

public class MediaTypeDetector {
    private static final String defaultType = "application/octet-stream";

    public static String getContentType(MusicTag tag) {
        if(StreamServerImpl.isTransCoded(tag)) {
            return "audio/mpeg";
        }else if(MusicTagUtils.isAIFFile(tag)) {
            return "audio/x-aiff";
        }else  if(MusicTagUtils.isMPegFile(tag)) {
            return "audio/mpeg";
        }else if(MusicTagUtils.isFLACFile(tag)) {
            return "audio/x-flac";
        }else if(MusicTagUtils.isALACFile(tag)) {
            return "audio/x-mp4";
        }else if(MusicTagUtils.isMp4File(tag)) {
            return "audio/x-mp4";
        }else  if(MusicTagUtils.isWavFile(tag)) {
            return "audio/x-wav";
        }else {
            return "audio/*"; //tag.getAudioEncoding();
        }
    }
}
