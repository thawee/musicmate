package apincer.music.core.server;

import static apincer.music.core.utils.StringUtils.isEmpty;

import apincer.music.core.model.Track;

public class ContentHolder {
    private final String contentType;
    //private final String resId;
    private Track track;
    private final String filePath;

    public ContentHolder(String contentType, Track track, String filePath) {
        //this.resId = resId;
        this.track = track;
        this.filePath = filePath;
        this.contentType = contentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContentType() {
        return contentType;
    }

    public Track getTrack() {
        return track;
    }

    public boolean isMedia() {
        if(isEmpty(contentType)) return false;
        return contentType.startsWith("audio/") || contentType.startsWith("video/");
    }

    public boolean isImage() {
        if(isEmpty(contentType)) return false;
        return contentType.startsWith("image/");
    }

    public boolean exists() {
        return (filePath!=null);
    }
}