package apincer.music.core.model;

import androidx.annotation.Nullable;

public class AudioTag implements Track {
    public static final String CONTAINER_TYPE = "FLD";

    protected long id;

    protected String uniqueKey;

    protected String albumArtFilename;

    protected String path = "";
    protected String fileType;
    protected long fileLastModified = 0;
    protected long fileSize;
    protected String storageId;
    protected String simpleName;
    protected String audioEncoding; //AAC,MP3, ALAC, FLAC, DSD

    protected String qualityInd = "SQ";
    protected long mqaSampleRate =-1;
    protected String audioChannels; // 2, 4
    protected int audioBitsDepth; // 16/24/32 bits
    protected long audioSampleRate; //44100,48000,88200,96000,192000
    protected long audioBitRate; //128, 256, 320 kbps
    protected double audioDuration;
    protected double audioStartTime; // for supporting cue sheet, iso sacd
    protected String title = "";
    protected String artist = "";
    protected String album = "";
    private String year = "";
    protected String genre = "";
    protected String mood = "";
    protected String style = "";
    protected String region = "";
    protected String track = "";
    protected String disc = "";
    protected String comment = "";
    protected String composer = "";
    protected String albumArtist = "";
    protected boolean isCompilation;
    protected String publisher = "";
    protected String description;

    protected double bpm = 0;
    protected double drScore = 0;
    protected double dynamicRange = 0;

    protected long childCount = 0;

    protected boolean isManaged;
    protected boolean isContainer;
    protected SearchCriteria.TYPE containerType;
    protected String rawOutput;

    public AudioTag(SearchCriteria.TYPE type, String codec) {
        this.isContainer = true;
        this.containerType = type;
        this.title = codec;
        this.id = generateId(type, codec);
    }

    public AudioTag() {
        // required for deserialize json
        this.id = System.nanoTime();
        this.isContainer = false;
    }

    public AudioTag(long id) {
        this.id = id;
        this.isContainer = false;
    }

    private long generateId(SearchCriteria.TYPE type, String codec) {
        String key = type.name() + "|" + codec;
        return key.hashCode() & 0xffffffffL; // make it positive long
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (obj instanceof Track tag) {
            return uniqueKey != null && tag.getUniqueKey() != null && uniqueKey.equals(tag.getUniqueKey());
        }
        return false;
    }

    @Override
    public Track copy() {
        return copy(this);
    }

    @Override
    public Track copy(Track original) {
        this.id = original.getId();
        this.uniqueKey = original.getUniqueKey();
        this.albumArtFilename = original.getAlbumArtFilename();
        this.path = original.getPath();
        this.fileSize = original.getFileSize();
        this.fileType = original.getFileType();

        this.audioBitsDepth = original.getAudioBitsDepth();
        this.audioBitRate = original.getAudioBitRate();
        this.audioDuration = original.getAudioDuration();
        this.audioSampleRate = original.getAudioSampleRate();
        this.audioEncoding = original.getAudioEncoding();
        this.audioChannels = original.getAudioChannels();

        this.title = original.getTitle();
        this.album = original.getAlbum();
        this.artist = original.getArtist();
        this.albumArtist = original.getAlbumArtist();
        this.genre = original.getGenre();
        this.year = original.getYear();
        this.track = original.getTrack();
       // this.disc = original.getDisc();
        this.comment = original.getComment();
        this.composer = original.getComposer();
        this.isCompilation = original.isCompilation();

        this.isManaged = original.isManaged();
        this.storageId = original.getStorageId();
        this.simpleName = original.getSimpleName();

        this.qualityInd = original.getQualityInd();
        this.mqaSampleRate = original.getMqaSampleRate();

        this.drScore = original.getDrScore();
        this.dynamicRange = original.getDynamicRange();

        this.publisher = original.getPublisher();
        this.audioStartTime = original.getAudioStartTime();

        return this;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String getUniqueKey() {
        return uniqueKey;
    }

    @Override
    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    @Override
    public String getAlbumArtFilename() {
        return albumArtFilename;
    }

    public void setAlbumArtFilename(String albumArtFilename) {
        this.albumArtFilename = albumArtFilename;
    }

    @Override
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileLastModified() {
        return fileLastModified;
    }

    public void setFileLastModified(long fileLastModified) {
        this.fileLastModified = fileLastModified;
    }

    @Override
    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isManaged() {
        return isManaged;
    }

    public void setIsManaged(boolean mmManaged) {
        this.isManaged = mmManaged;
    }

    public String getStorageId() {
        return storageId;
    }

    public void setStorageId(String storageId) {
        this.storageId = storageId;
    }

    @Override
    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    @Override
    public String getAudioEncoding() {
        return audioEncoding;
    }

    public void setAudioEncoding(String audioEncoding) {
        this.audioEncoding = audioEncoding;
    }

    @Override
    public String getQualityInd() {
        return qualityInd;
    }

    @Override
    public void setQualityInd(String qualityInd) {
        this.qualityInd = qualityInd;
    }

    @Override
    public long getMqaSampleRate() {
        return mqaSampleRate;
    }

    @Override
    public void setMqaSampleRate(long mqaSampleRate) {
        this.mqaSampleRate = mqaSampleRate;
    }

    @Override
    public String getAudioChannels() {
        return audioChannels;
    }

    public void setAudioChannels(String audioChannels) {
        this.audioChannels = audioChannels;
    }

    @Override
    public int getAudioBitsDepth() {
        return audioBitsDepth;
    }

    public void setAudioBitsDepth(int audioBitsDepth) {
        this.audioBitsDepth = audioBitsDepth;
    }

    @Override
    public long getAudioSampleRate() {
        return audioSampleRate;
    }

    public void setAudioSampleRate(long audioSampleRate) {
        this.audioSampleRate = audioSampleRate;
    }

    @Override
    public long getAudioBitRate() {
        return audioBitRate;
    }

    public void setAudioBitRate(long audioBitRate) {
        this.audioBitRate = audioBitRate;
    }

    @Override
    public double getAudioDuration() {
        return audioDuration;
    }

    public void setAudioDuration(double audioDuration) {
        this.audioDuration = audioDuration;
    }

    @Override
    public double getAudioStartTime() {
        return audioStartTime;
    }

    public void setAudioStartTime(double audioStartTime) {
        this.audioStartTime = audioStartTime;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public void setArtist(String artist) {
        this.artist = artist;
    }

    @Override
    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    @Override
    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    @Override
    public String getGenre() {
        return genre;
    }

    @Override
    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public String getDisc() {
        return disc;
    }

    public void setDisc(String disc) {
        this.disc = disc;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComposer() {
        return composer;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    @Override
    public String getAlbumArtist() {
        return albumArtist;
    }

    @Override
    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }

    public boolean isCompilation() {
        return isCompilation;
    }

    public void setCompilation(boolean compilation) {
        this.isCompilation = compilation;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public double getDrScore() {
        return drScore;
    }

    public void setDrScore(double drScore) {
        this.drScore = drScore;
    }

    @Override
    public double getDynamicRange() {
        return dynamicRange;
    }

    public void setDynamicRange(double dynamicRange) {
        this.dynamicRange = dynamicRange;
    }

    public long getChildCount() {
        return childCount;
    }

    public void setChildCount(long childCount) {
        this.childCount = childCount;
    }

    @Override
    public boolean isContainer() {
        return isContainer;
    }

    public void setContainer(boolean container) {
        isContainer = container;
    }

    public String getMood() {
        return mood;
    }

    @Override
    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getStyle() {
        return style;
    }

    @Override
    public void setStyle(String style) {
        this.style = style;
    }

    public void setManaged(boolean managed) {
        isManaged = managed;
    }

    public double getBpm() {
        return bpm;
    }

    public void setBpm(double bpm) {
        this.bpm = bpm;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public SearchCriteria.TYPE getContainerType() {
        return containerType;
    }

    public void setContainerType(SearchCriteria.TYPE containerType) {
        this.containerType = containerType;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }

    public void increaseChildCount() {
        childCount++;
    }

}
