package apincer.music.core.playback.spi;

public interface MediaTrack {
    String getPath();

    String getTitle();

    String getRating();

    String getGrouping();

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
}
