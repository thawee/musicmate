package apincer.music.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.Track;
import apincer.music.core.utils.StringUtils;

@Entity(
    tableName = "musictag",
    indices = {
        @Index(value = {"uniqueKey"}, unique = true),
        @Index(value = {"qualityInd"}),
        @Index(value = {"audioBitsDepth"}),
        @Index(value = {"title"}),
        @Index(value = {"artist"}),
        @Index(value = {"normalizedTitle"}),
        @Index(value = {"normalizedArtist"}),
        @Index(value = {"album"}),
        @Index(value = {"genre"}),
        @Index(value = {"publisher"})
    }
)
public class TrackEntity implements Track {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "uniqueKey")
    private String uniqueKey = "";

    @ColumnInfo(name = "albumArtFilename")
    private String albumArtFilename;

    @ColumnInfo(name = "path")
    private String path = "";

    @ColumnInfo(name = "fileType")
    private String fileType;

    @ColumnInfo(name = "fileLastModified")
    private long fileLastModified = 0;

    @ColumnInfo(name = "fileSize")
    private long fileSize;

    @ColumnInfo(name = "isManaged")
    private boolean isManaged;

    @ColumnInfo(name = "storageId")
    private String storageId;

    @ColumnInfo(name = "simpleName")
    private String simpleName;

    @ColumnInfo(name = "audioEncoding")
    private String audioEncoding;

    @ColumnInfo(name = "qualityInd")
    private String qualityInd = "SQ";

    @ColumnInfo(name = "mqaSampleRate")
    private long mqaSampleRate = -1;

    @ColumnInfo(name = "audioChannels")
    private String audioChannels;

    @ColumnInfo(name = "audioBitsDepth")
    private int audioBitsDepth;

    @ColumnInfo(name = "audioSampleRate")
    private long audioSampleRate;

    @ColumnInfo(name = "audioBitRate")
    private long audioBitRate;

    @ColumnInfo(name = "audioDuration")
    private double audioDuration;

    @ColumnInfo(name = "audioStartTime")
    private double audioStartTime;

    @ColumnInfo(name = "title")
    private String title = "";

    @ColumnInfo(name = "normalizedTitle")
    private String normalizedTitle = "";

    @ColumnInfo(name = "artist")
    private String artist = "";

    @ColumnInfo(name = "normalizedArtist")
    private String normalizedArtist = "";

    @ColumnInfo(name = "album")
    private String album = "";

    @ColumnInfo(name = "year")
    private String year = "";

    @ColumnInfo(name = "genre")
    private String genre = "";

    @ColumnInfo(name = "mood")
    private String mood = "";

    @ColumnInfo(name = "style")
    private String style = "";

    @ColumnInfo(name = "origin")
    private String origin = "";

    @ColumnInfo(name = "track")
    private String track = "";

    @ColumnInfo(name = "comment")
    private String comment = "";

    @ColumnInfo(name = "composer")
    private String composer = "";

    @ColumnInfo(name = "albumArtist")
    private String albumArtist = "";

    @ColumnInfo(name = "compilation")
    private boolean compilation;

    @ColumnInfo(name = "publisher")
    private String publisher = "";

    @ColumnInfo(name = "drScore")
    private double drScore = 0;

    @ColumnInfo(name = "dynamicRange")
    private double dynamicRange = 0;

    @ColumnInfo(name = "bpm")
    private double bpm = 0;

    public TrackEntity() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getUniqueKey() { return uniqueKey; }
    public void setUniqueKey(@NonNull String uniqueKey) { this.uniqueKey = uniqueKey; }

    public String getAlbumArtFilename() { return albumArtFilename; }
    public void setAlbumArtFilename(String albumArtFilename) { this.albumArtFilename = albumArtFilename; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileLastModified() { return fileLastModified; }
    public void setFileLastModified(long fileLastModified) { this.fileLastModified = fileLastModified; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public boolean isManaged() { return isManaged; }
    public void setManaged(boolean managed) { isManaged = managed; }
    @androidx.room.Ignore
    public void setIsManaged(boolean managed) { isManaged = managed; }

    public String getStorageId() { return storageId; }
    public void setStorageId(String storageId) { this.storageId = storageId; }

    public String getSimpleName() { return simpleName; }
    public void setSimpleName(String simpleName) { this.simpleName = simpleName; }

    public String getAudioEncoding() { return audioEncoding; }
    public void setAudioEncoding(String audioEncoding) { this.audioEncoding = audioEncoding; }

    public String getQualityInd() { return qualityInd; }
    public void setQualityInd(String qualityInd) { this.qualityInd = qualityInd; }

    public long getMqaSampleRate() { return mqaSampleRate; }
    public void setMqaSampleRate(long mqaSampleRate) { this.mqaSampleRate = mqaSampleRate; }

    public String getAudioChannels() { return audioChannels; }
    public void setAudioChannels(String audioChannels) { this.audioChannels = audioChannels; }

    public int getAudioBitsDepth() { return audioBitsDepth; }
    public void setAudioBitsDepth(int audioBitsDepth) { this.audioBitsDepth = audioBitsDepth; }

    public long getAudioSampleRate() { return audioSampleRate; }
    public void setAudioSampleRate(long audioSampleRate) { this.audioSampleRate = audioSampleRate; }

    public long getAudioBitRate() { return audioBitRate; }
    public void setAudioBitRate(long audioBitRate) { this.audioBitRate = audioBitRate; }

    public double getAudioDuration() { return audioDuration; }
    public void setAudioDuration(double audioDuration) { this.audioDuration = audioDuration; }

    public double getAudioStartTime() { return audioStartTime; }
    public void setAudioStartTime(double audioStartTime) { this.audioStartTime = audioStartTime; }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = title;
        this.normalizedTitle = StringUtils.trimToEmpty(title).toLowerCase();
    }

    public String getNormalizedTitle() { return normalizedTitle; }
    public void setNormalizedTitle(String normalizedTitle) { this.normalizedTitle = normalizedTitle; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) {
        this.artist = artist;
        this.normalizedArtist = StringUtils.trimToEmpty(artist).toLowerCase();
    }

    public String getNormalizedArtist() { return normalizedArtist; }
    public void setNormalizedArtist(String normalizedArtist) { this.normalizedArtist = normalizedArtist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getComposer() { return composer; }
    public void setComposer(String composer) { this.composer = composer; }

    public String getAlbumArtist() { return albumArtist; }
    public void setAlbumArtist(String albumArtist) { this.albumArtist = albumArtist; }

    public boolean isCompilation() { return compilation; }
    public void setCompilation(boolean compilation) { this.compilation = compilation; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public double getDrScore() { return drScore; }
    public void setDrScore(double drScore) { this.drScore = drScore; }

    public double getDynamicRange() { return dynamicRange; }
    public void setDynamicRange(double dynamicRange) { this.dynamicRange = dynamicRange; }

    public double getBpm() { return bpm; }
    public void setBpm(double bpm) { this.bpm = bpm; }

    @Override
    public boolean isContainer() { return false; }

    @Override
    public SearchCriteria.TYPE getContainerType() { return null; }

    @Override
    public String getDescription() { return ""; }

    @Override
    public long getChildCount() { return 0; }

    @Override
    public void increaseChildCount() {}

    @Override
    public Track copy() {
        TrackEntity copy = new TrackEntity();
        copy.copy(this);
        return copy;
    }

    @Override
    public Track copy(Track original) {
        if (original == null) return this;
        this.uniqueKey = original.getUniqueKey() != null ? original.getUniqueKey() : "";
        this.path = original.getPath();
        this.title = original.getTitle();
        this.artist = original.getArtist();
        this.album = original.getAlbum();
        this.year = original.getYear();
        this.genre = original.getGenre();
        this.mood = original.getMood();
        this.style = original.getStyle();
        this.origin = original.getOrigin();
        this.track = original.getTrack();
        this.comment = original.getComment();
        this.composer = original.getComposer();
        this.albumArtist = original.getAlbumArtist();
        this.compilation = original.isCompilation();
        this.publisher = original.getPublisher();
        this.qualityInd = original.getQualityInd();
        this.audioBitsDepth = original.getAudioBitsDepth();
        this.audioSampleRate = original.getAudioSampleRate();
        this.audioBitRate = original.getAudioBitRate();
        this.audioChannels = original.getAudioChannels();
        this.audioDuration = original.getAudioDuration();
        this.audioEncoding = original.getAudioEncoding();
        this.fileSize = original.getFileSize();
        this.fileLastModified = original.getFileLastModified();
        this.fileType = original.getFileType();
        this.albumArtFilename = original.getAlbumArtFilename();
        this.drScore = original.getDrScore();
        this.dynamicRange = original.getDynamicRange();
        this.bpm = original.getBpm();
        this.isManaged = original.isManaged();
        this.storageId = original.getStorageId();
        this.simpleName = original.getSimpleName();
        this.normalizedTitle = StringUtils.trimToEmpty(title).toLowerCase();
        this.normalizedArtist = StringUtils.trimToEmpty(artist).toLowerCase();
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TrackEntity tag = (TrackEntity) obj;
        return Objects.equals(uniqueKey, tag.uniqueKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueKey);
    }
}
