package apincer.music.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import apincer.music.core.Constants;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.utils.StringUtils;

@DatabaseTable(tableName = "musictag")
public class MusicTag implements MediaTrack {
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private MusicTag originTag;

    @DatabaseField(generatedId = true, allowGeneratedIdInsert = true, canBeNull = false)
    protected long id;

    @DatabaseField(uniqueIndex = true)
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
    protected transient String data;

    // Mate info
    @DatabaseField
    protected boolean mmManaged;
    @DatabaseField
    protected String storageId;
    @DatabaseField
    protected String simpleName;

    @DatabaseField(index = true)
   protected String qualityRating;
    @DatabaseField
    protected String audioEncoding; //AAC,MP3, ALAC, FLAC, DSD

    @DatabaseField(index = true)
   protected String qualityInd = "SQ";
    @DatabaseField
    protected long mqaSampleRate =-1;
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
    @DatabaseField
    protected String track = "";
    @DatabaseField
    protected String disc = "";
    @DatabaseField
    protected String comment = "";
   // @Index
   @DatabaseField(index = true)
   protected String grouping = "";
    @DatabaseField
    protected String composer = "";
    @DatabaseField
    protected String albumArtist = "";
    @DatabaseField
    protected boolean compilation;
  //  @Index
  @DatabaseField(index = true)
  protected String publisher = "";

    // loudness and gain
    @DatabaseField
    protected double drScore = 0;
   @DatabaseField
   protected double dynamicRange = 0;


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

    // Use the custom persister for this field
   // @DatabaseField(persisterClass = FloatArrayPersister.class)
   // private float[] waveformData;

   // public float[] getWaveformData() {
   //     return waveformData;
   // }

    //public void setWaveformData(float[] waveformData) {
    //    this.waveformData = waveformData;
   // }

    public String getQualityInd() {
        return qualityInd;
    }

    public void setQualityInd(String qualityInd) {
        this.qualityInd = qualityInd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (obj instanceof MusicTag tag) {
            return uniqueKey != null && tag.uniqueKey != null && uniqueKey.equals(tag.uniqueKey);
        }
        return false;
    }

    public String getAudioChannels() {
        return audioChannels;
    }

    public void setAudioChannels(String audioChannels) {
        this.audioChannels = audioChannels;
    }

    public long getMqaSampleRate() {
        return mqaSampleRate;
    }

    public void setMqaSampleRate(long mqaSampleRate) {
        this.mqaSampleRate = mqaSampleRate;
    }


    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public MusicTag() {
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

    public void setAudioSampleRate(long audioSampleRate) {
        this.audioSampleRate = audioSampleRate;
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

    public String getDisc() {
        return disc;
    }

    public void setDisc(String disc) {
        this.disc = disc;
    }

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

    public String getGrouping() {
        return grouping;
    }

    public void setGrouping(String grouping) {
        this.grouping = grouping;
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

    @Override
    public String getRating() {
        return getQualityRating();
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

    public void setPath(String mediaPath) {
        this.path = mediaPath;
    }

    public boolean isDSD() {
        return audioBitsDepth == Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public boolean isSACDISO() {
        return Constants.MEDIA_ENC_SACD.equalsIgnoreCase(getAudioEncoding());
    }

    public String getPath() {
        return path;
    }

    public android.net.Uri getUri() {
        return android.net.Uri.parse(path);
    }

    public double getAudioDuration() {
        return audioDuration;
    }

    @NonNull
    public MusicTag copy() {
        MusicTag tag = new MusicTag();
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
        tag.year = year;
        tag.track = track;
        tag.disc = disc;
        tag.comment = comment;
        tag.grouping = grouping;
        tag.composer = composer;
        tag.publisher = publisher;
        tag.compilation = compilation;
       // tag.mediaType = mediaType;

        tag.mmManaged = mmManaged;
        tag.qualityRating = qualityRating;
        //tag.rating = rating;

        tag.qualityInd = qualityInd;
        tag.mqaSampleRate = mqaSampleRate;
       // tag.language = language;
        tag.audioStartTime = audioStartTime;
        //tag.coverartMime = coverartMime;
        //tag.waveformData = waveformData;
        return tag;
    }

    public void cloneFrom(MusicTag tag) {
        this.id = tag.id;
        this.uniqueKey = tag.uniqueKey;
        this.albumArtFilename = tag.albumArtFilename;
        this.path = tag.path;
        this.fileSize = tag.fileSize;
        this.fileType = tag.fileType;
        this.fileLastModified = tag.fileLastModified;

        this.audioBitsDepth = tag.audioBitsDepth;
        this.audioBitRate = tag.audioBitRate;
        this.audioDuration = tag.audioDuration;
        this.audioSampleRate = tag.audioSampleRate;
        this.audioEncoding = tag.audioEncoding;
        this.audioChannels = tag.audioChannels;

        this.title = tag.title;
        this.album = tag.album;
        this.artist = tag.artist;
        this.albumArtist = tag.albumArtist;
        this.genre = tag.genre;
        this.year = tag.year;
        this.track = tag.track;
        this.disc = tag.disc;
        this.comment = tag.comment;
        this.grouping = tag.grouping;
        this.composer = tag.composer;
       // this.mediaType = tag.mediaType;
        this.compilation = tag.compilation;

        this.mmManaged = tag.mmManaged;
        this.qualityRating = tag.qualityRating;
       // this.rating = tag.rating;

        this.storageId = tag.storageId;
        this.simpleName = tag.simpleName;

        this.qualityInd = tag.qualityInd;
        this.mqaSampleRate = tag.mqaSampleRate;

        this.drScore = tag.drScore;
        this.dynamicRange = tag.dynamicRange;

        this.publisher = tag.publisher;
        //this.coverartMime = tag.coverartMime;
        this.audioStartTime = tag.audioStartTime;
        //this.waveformData = tag.waveformData;
    }

    public MusicTag getOriginTag() {
        return originTag;
    }

    public void setOriginTag(MusicTag originTag) {
        this.originTag = originTag;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public boolean isMusicManaged() {
        return mmManaged;
    }

    public void setMusicManaged(boolean musicManaged) {
        this.mmManaged = musicManaged;
    }

    public String getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(String rating) {
        this.qualityRating = rating;
    }

    public double getAudioStartTime() {
        return audioStartTime;
    }

    public void setAudioStartTime(double audioStartTime) {
        this.audioStartTime = audioStartTime;
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

    public double getDynamicRangeScore() {
        return drScore;
    }

    public void setDynamicRangeScore(double trackDR) {
        this.drScore = trackDR;
    }

    public double getDynamicRange() {
        return dynamicRange;
    }

    public void setDynamicRange(double measuredDR) {
        this.dynamicRange = measuredDR;
    }

    /*
    protected MusicTag(Parcel in) {
        id = in.readLong();
        uniqueKey = in.readString();
        albumCoverUniqueKey = in.readString();
        path = in.readString();
        fileType = in.readString();
        fileLastModified = in.readLong();
        fileSize = in.readLong();
        mmManaged = in.readByte() != 0;
        storageId = in.readString();
        simpleName = in.readString();
        qualityRating = in.readString();
        audioEncoding = in.readString();
        qualityInd = in.readString();
        mqaSampleRate = in.readLong();
        audioChannels = in.readString();
        audioBitsDepth = in.readInt();
        audioSampleRate = in.readLong();
        audioBitRate = in.readLong();
        audioDuration = in.readDouble();
        audioStartTime = in.readDouble();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        year = in.readString();
        genre = in.readString();
        track = in.readString();
        disc = in.readString();
        comment = in.readString();
        grouping = in.readString();
        composer = in.readString();
        albumArtist = in.readString();
        compilation = in.readByte() != 0;
        publisher = in.readString();
        drScore = in.readDouble();
        dynamicRange = in.readDouble();
        in.readFloatArray(waveformData);
    } */

    /*
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(uniqueKey);
        dest.writeString(albumCoverUniqueKey);
        dest.writeString(path);
        dest.writeString(fileType);
        dest.writeLong(fileLastModified);
        dest.writeLong(fileSize);
        dest.writeByte((byte) (mmManaged ? 1 : 0));
        dest.writeString(storageId);
        dest.writeString(simpleName);
        dest.writeString(qualityRating);
        dest.writeString(audioEncoding);
        dest.writeString(qualityInd);
        dest.writeLong(mqaSampleRate);
        dest.writeString(audioChannels);
        dest.writeInt(audioBitsDepth);
        dest.writeLong(audioSampleRate);
        dest.writeLong(audioBitRate);
        dest.writeDouble(audioDuration);
        dest.writeDouble(audioStartTime);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(year);
        dest.writeString(genre);
        dest.writeString(track);
        dest.writeString(disc);
        dest.writeString(comment);
        dest.writeString(grouping);
        dest.writeString(composer);
        dest.writeString(albumArtist);
        dest.writeByte((byte) (compilation ? 1 : 0));
        dest.writeString(publisher);
        dest.writeDouble(drScore);
        dest.writeDouble(dynamicRange);
        dest.writeFloatArray(waveformData);
    } */

    /*
    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MusicTag> CREATOR = new Creator<>() {
        @Override
        public MusicTag createFromParcel(Parcel in) {
            return new MusicTag(in);
        }

        @Override
        public MusicTag[] newArray(int size) {
            return new MusicTag[size];
        }
    }; */
}