package apincer.music.ormlite.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.Track;
import apincer.music.core.utils.StringUtils;

@DatabaseTable(tableName = "musictag")
public class TrackEntity implements Track {

    @Override
    public int hashCode() {
        return Objects.hash(uniqueKey);
    }

    @DatabaseField(generatedId = true, canBeNull = false)
    protected long id;

    @DatabaseField(uniqueIndex = true, canBeNull = false)
    protected String uniqueKey;

    @DatabaseField
    protected String albumArtFilename;

    // file information
    @DatabaseField
    protected String path = "";
    @DatabaseField
    protected String fileType;
    @DatabaseField
    protected long fileLastModified = 0;
    @DatabaseField
    protected long fileSize;

    // Mate info
    @DatabaseField
    protected boolean isManaged;
    @DatabaseField
    protected String storageId;
    @DatabaseField
    protected String simpleName;

    @DatabaseField
    protected String audioEncoding; //AAC,MP3, ALAC, FLAC, DSD

    @DatabaseField(index = true)
    protected String qualityInd = "SQ";
    @DatabaseField
    protected long mqaSampleRate = -1;
    @DatabaseField
    protected String audioChannels; // 2, 4

    @DatabaseField(index = true)
    protected int audioBitsDepth; // 16/24/32 bits
    @DatabaseField
    protected long audioSampleRate; //44100,48000,88200,96000,192000
    @DatabaseField
    protected long audioBitRate; //128, 256, 320 kbps
    @DatabaseField
    protected double audioDuration;
    @DatabaseField
    protected double audioStartTime; // for supporting cue sheet, iso sacd

    // tags information
    @DatabaseField(index = true)
    protected String title = "";

    @DatabaseField(index = true)
    protected String normalizedTitle = "";

    @DatabaseField(index = true)
    protected String artist = "";
    @DatabaseField(index = true)
    protected String normalizedArtist = "";

    @DatabaseField
    protected String album = "";
    @DatabaseField
    private String year = "";
    @DatabaseField(index = true)
    protected String genre = "";
    @DatabaseField()
    protected String mood = "";
    @DatabaseField()
    protected String style = "";
   // @DatabaseField()
  //  protected String region = "";
    @DatabaseField
    protected String track = "";
   // @DatabaseField
   // protected String disc = "";
    @DatabaseField
    protected String comment = "";
    @DatabaseField
    protected String composer = "";
    @DatabaseField
    protected String albumArtist = "";
    @DatabaseField
    protected boolean compilation;
    @DatabaseField(index = true)
    protected String publisher = "";

    @DatabaseField
    protected double drScore = 0;
    @DatabaseField
    protected double dynamicRange = 0;

    @DatabaseField
    protected double bpm = 0;

    public String getAlbumArtFilename() {
        return albumArtFilename;
    }

    public void setAlbumArtFilename(String getAlbumArtFilename) {
        this.albumArtFilename = getAlbumArtFilename;
    }

    public String getNormalizedArtist() {
        return normalizedArtist;
    }

    public String getNormalizedTitle() {
        return normalizedTitle;
    }

    public String getQualityInd() {
        return qualityInd;
    }

    public void setQualityInd(String qualityInd) {
        this.qualityInd = qualityInd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (obj instanceof TrackEntity tag) {
            return uniqueKey != null && tag.uniqueKey != null && uniqueKey.equals(tag.uniqueKey);
        }
        return false;
    }

    public String getAudioChannels() {
        return audioChannels;
    }

    @Override
    public void setBpm(double tagValue) {
        bpm = tagValue;
    }

    @Override
    public void setMood(String value) {
        mood = value;
    }

    @Override
    public void setStyle(String value) {
        style = value;
    }

   // @Override
    //public void setRegion(String value) {
    //    region = value;
    //}

    public void setAudioChannels(String audioChannels) {
        this.audioChannels = audioChannels;
    }

    public long getMqaSampleRate() {
        return mqaSampleRate;
    }

    public void setMqaSampleRate(long mqaSampleRate) {
        this.mqaSampleRate = mqaSampleRate;
    }

    public TrackEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String displayName) {
        this.simpleName = displayName;
    }

    public String getStorageId() {
        return storageId;
    }

    public void setStorageId(String storageId) {
        this.storageId = storageId;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getAudioBitsDepth() {
        return audioBitsDepth;
    }

    public void setAudioBitsDepth(int audioBitsDepth) {
        this.audioBitsDepth = audioBitsDepth;
    }

    public long getAudioSampleRate() {
        return audioSampleRate;
    }

    public long getAudioBitRate() {
        return audioBitRate;
    }

    public void setAudioBitRate(long audioBitRate) {
        this.audioBitRate = audioBitRate;
    }

    public String getAudioEncoding() {
        return audioEncoding;
    }

    public void setAudioEncoding(String audioEncoding) {
        this.audioEncoding = audioEncoding;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

   // public String getDisc() {
   //     return disc;
   // }

    //public void setDisc(String disc) {
    //    this.disc = disc;
    //}

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.normalizedTitle = StringUtils.normalizeName(title);
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
        this.normalizedArtist = StringUtils.normalizeName(artist);
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }

    public long getFileLastModified() {
        return fileLastModified;
    }

    @Override
    public void setAudioSampleRate(long sampleRateAsNumber) {
        this.audioSampleRate = sampleRateAsNumber;
    }

    @Override
    public String getStyle() {
        return style;
    }

    @Override
    public String getMood() {
        return mood;
    }

   // @Override
   // public String getRegion() {
   //     return region;
   // }

    public void setFileLastModified(long lastModified) {
        this.fileLastModified = lastModified;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileExtension) {
        this.fileType = fileExtension;
    }

    public void setAudioDuration(double audioDuration) {
        this.audioDuration = audioDuration;
    }

    @Override
    public void increaseChildCount() {

    }

    public void setPath(String mediaPath) {
        this.path = mediaPath;
    }

    public String getPath() {
        return path;
    }

    public double getAudioDuration() {
        return audioDuration;
    }

    @NonNull
    public TrackEntity copy() {
        TrackEntity tag = new TrackEntity();
        tag.id = id;
        tag.uniqueKey = uniqueKey;
        tag.albumArtFilename = albumArtFilename;
        tag.path = path;
        tag.storageId = storageId;
        tag.simpleName = simpleName;
        tag.fileSize = fileSize;
        tag.fileType = fileType;
        tag.fileLastModified = fileLastModified;

        tag.audioBitsDepth = audioBitsDepth;
        tag.audioBitRate = audioBitRate;
        tag.audioDuration = audioDuration;
        tag.audioSampleRate = audioSampleRate;
        tag.audioEncoding = audioEncoding;
        tag.audioChannels = audioChannels;

        tag.drScore = drScore;
        tag.dynamicRange = dynamicRange;

        tag.title = title;
        tag.album = album;
        tag.artist = artist;
        tag.albumArtist = albumArtist;
        tag.genre = genre;
        tag.mood = mood;
        tag.style = style;
       // tag.region = region;
        tag.year = year;
        tag.track = track;
       // tag.disc = disc;
        tag.comment = comment;
        //   tag.grouping = grouping;
        tag.composer = composer;
        tag.publisher = publisher;
        tag.compilation = compilation;
        // tag.mediaType = mediaType;

        tag.isManaged = isManaged;
        //tag.rating = rating;

        tag.qualityInd = qualityInd;
        tag.mqaSampleRate = mqaSampleRate;
        // tag.language = language;
        tag.audioStartTime = audioStartTime;

        tag.normalizedTitle = StringUtils.normalizeName(title);
        tag.normalizedArtist = StringUtils.normalizeName(artist);

        return tag;
    }

    @Override
    public Track copy(Track tag) {
        this.uniqueKey = tag.getUniqueKey();
        this.albumArtFilename = tag.getAlbumArtFilename();
        this.path = tag.getPath();
        this.fileSize = tag.getFileSize();
        this.fileType = tag.getFileType();
        this.fileLastModified = tag.getFileLastModified();

        this.audioBitsDepth = tag.getAudioBitsDepth();
        this.audioBitRate = tag.getAudioBitRate();
        this.audioDuration = tag.getAudioDuration();
        this.audioSampleRate = tag.getAudioSampleRate();
        this.audioEncoding = tag.getAudioEncoding();
        this.audioChannels = tag.getAudioChannels();

        this.title = tag.getTitle();
        this.album = tag.getAlbum();
        this.artist = tag.getArtist();
        this.albumArtist = tag.getAlbumArtist();
        this.genre = tag.getGenre();
        this.mood = tag.getMood();
        this.style = tag.getStyle();
        //this.region = tag.getRegion();
        this.year = tag.getYear();
        this.track = tag.getTrack();
       // this.disc = tag.getDisc();
        this.comment = tag.getComment();
        //  this.grouping = tag.grouping;
        this.composer = tag.getComposer();
        // this.mediaType = tag.mediaType;
        this.compilation = tag.isCompilation();

        this.isManaged = tag.isManaged();
        // this.rating = tag.rating;

        this.storageId = tag.getStorageId();
        this.simpleName = tag.getSimpleName();

        this.qualityInd = tag.getQualityInd();
        this.mqaSampleRate = tag.getMqaSampleRate();

        this.drScore = tag.getDrScore();
        this.dynamicRange = tag.getDynamicRange();

        this.publisher = tag.getPublisher();
        //this.coverartMime = tag.coverartMime;
        this.audioStartTime = tag.getAudioStartTime();
        //this.waveformData = tag.waveformData;

        this.normalizedTitle = StringUtils.normalizeName(title);
        this.normalizedArtist = StringUtils.normalizeName(artist);

        return this;
    }

    public TrackEntity(Track tag) {
        this.id = tag.getId();
        copy(tag);
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public boolean isManaged() {
        return isManaged;
    }

    public void setIsManaged(boolean musicManaged) {
        this.isManaged = musicManaged;
    }

    @Override
    public SearchCriteria.TYPE getContainerType() {
        return null;
    }

    public double getAudioStartTime() {
        return audioStartTime;
    }

    @Override
    public boolean isContainer() {
        return false;
    }

    public void setAudioStartTime(double audioStartTime) {
        this.audioStartTime = audioStartTime;
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public long getChildCount() {
        return 0;
    }

    public boolean isCompilation() {
        return compilation;
    }

    public void setCompilation(boolean compilation) {
        this.compilation = compilation;
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

    public void setDrScore(double trackDR) {
        this.drScore = trackDR;
    }

    public double getDynamicRange() {
        return dynamicRange;
    }

    public void setDynamicRange(double measuredDR) {
        this.dynamicRange = measuredDR;
    }

}