package apincer.music.core.playback.spi;

import apincer.music.core.database.MusicTag;

public interface MediaTrack {
    String getPath();

    String getTitle();

    String getQualityRating();

   // String getGrouping();

    String getGenre();

    String getArtist();

    String getAlbum();

    String getAudioEncoding();

    long getAudioSampleRate();

    String getQualityInd();

    String getSimpleName();

    int getAudioBitsDepth();

    long getId();

    double getAudioDuration();

    String getAlbumArtFilename();

    long getAudioBitRate();

    String getFileType();

    String getYear();

    boolean isMusicManaged();

    double getDynamicRangeScore();

    String getUniqueKey();

    void setUniqueKey(String uniqueKey);

    void setQualityInd(String ind);

    long getMqaSampleRate();

    long getFileSize();
}
