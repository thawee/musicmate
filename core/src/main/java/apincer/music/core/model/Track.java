package apincer.music.core.model;

public interface Track {
    String getPath();

    String getTitle();

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

    boolean isManaged();

    double getDrScore();

    String getUniqueKey();

    void setUniqueKey(String uniqueKey);

    void setQualityInd(String ind);

    long getMqaSampleRate();

    long getFileSize();

    String getAudioChannels();
 

    void setBpm(double tagValue);

    void setMood(String value);

    void setStyle(String value);

  //  void setRegion(String value);

    double getAudioStartTime();

    boolean isContainer();

    void setId(long id);

    double getDynamicRange();

    void setDynamicRange(double dynamicRange);

    void setMqaSampleRate(long mqaSampleRate);

    void setGenre(String none);

    void setArtist(String empty);

    void setAlbumArtist(String artist);

    String getAlbumArtist();

    Track copy();

    Track copy(Track original);

    String getTrack();

   // String getDisc();

    String getComment();

    String getComposer();

    boolean isCompilation();

    String getStorageId();

    String getPublisher();

    void setAlbumArtFilename(String s);

    void setTitle(String s);

    void setAlbum(String commonStringValue);

    void setTrack(String commonStringValue);

    void setYear(String commonStringValue);

  //  void setDisc(String commonStringValue);

    void setPublisher(String commonStringValue);

    void setIsManaged(boolean managedInLibrary);

    SearchCriteria.TYPE getContainerType();

    void setPath(String newPath);

    void setSimpleName(String basePath);

    void setStorageId(String storageId);

    void setFileLastModified(long l);

    void setAudioStartTime(double i);

    String getDescription();

    long getChildCount();

    void setDrScore(double dynamicRangeScore);

    void setFileSize(long length);

    void setFileType(String lowerCase);

    void setAudioEncoding(String fileType);

    void setAudioDuration(double v);

    void increaseChildCount();

    void setAudioBitsDepth(int bitsPerSample);

    void setAudioBitRate(long l);

    void setAudioChannels(String channels);

    void setComposer(String tagValue);

    void setCompilation(boolean aBoolean);

    void setComment(String tagValue);

    long getFileLastModified();

    void setAudioSampleRate(long sampleRateAsNumber);

    String getStyle();

    String getMood();

  //  String getRegion();
}
