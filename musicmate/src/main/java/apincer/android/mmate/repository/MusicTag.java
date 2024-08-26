package apincer.android.mmate.repository;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import apincer.android.mmate.Constants;
import apincer.android.mmate.utils.StringUtils;
@DatabaseTable(tableName = "musictag")
public class MusicTag implements Cloneable, Parcelable {
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private MusicTag originTag;

    @DatabaseField(generatedId = true,allowGeneratedIdInsert = true)
    protected long id;

    @DatabaseField(uniqueIndex = true)
    protected String uniqueKey;

    public String getAlbumUniqueKey() {
        return albumUniqueKey;
    }

    public void setAlbumUniqueKey(String albumUniqueKey) {
        this.albumUniqueKey = albumUniqueKey;
    }

    @DatabaseField
    protected String albumUniqueKey;

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (obj instanceof MusicTag) {
           // return id == ((MusicTag) obj).getId();
            return uniqueKey.equals(((MusicTag) obj).uniqueKey);
        }
        return false;
    }

    // file information
    @DatabaseField
    protected String path = "";
   @DatabaseField
   protected String fileFormat;
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
    @DatabaseField
    protected String mediaType = "";
    @DatabaseField
    protected boolean mmReadError;

   @DatabaseField(index = true)
   protected String mediaQuality;
    @DatabaseField
    protected int rating; //0-10
    // audio information
    @DatabaseField
    protected String audioEncoding; //AAC,MP3, ALAC, FLAC, DSD
   // protected boolean audioLossless;
   @DatabaseField(index = true)
   protected String mqaInd;
    @DatabaseField
    protected long mqaSampleRate;
    @DatabaseField
    protected boolean mqaScanned = false;
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
    protected String artist = "";
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
    @DatabaseField
    protected String coverartMime = "";

    // loudness and gain
    @DatabaseField
    protected double gainTrackTP; // unit of dB
    @DatabaseField
    protected double gainTrackRG; // replay gain V2, references LI -18.00 LUFS
    @DatabaseField
    protected double dynamicRangeMeter; //gainTrackDR; // Dynamic Range
   @DatabaseField
   protected double dynamicRange;

    @DatabaseField
    protected boolean upscaled; // increasing the bit depth from original file

    @DatabaseField
    protected boolean upsampled; // increasing the sampling rate from original file

    public String getMqaInd() {
        return mqaInd;
    }

    public void setMqaInd(String mqaInd) {
        this.mqaInd = mqaInd;
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

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    protected MusicTag(Parcel in) {
        originTag = in.readParcelable(MusicTag.class.getClassLoader());
        id = in.readLong();
        uniqueKey = in.readString();
        albumUniqueKey = in.readString();
        path = in.readString();
        fileLastModified = in.readLong();
        fileSize = in.readLong();
        fileFormat = in.readString();
        audioEncoding = in.readString();
        mqaScanned = in.readByte() != 0;
        mqaInd = in.readString();
        mqaSampleRate = in.readLong();
        audioBitsDepth = in.readInt();
        audioSampleRate = in.readLong();
        audioBitRate = in.readLong();
        audioDuration = in.readDouble();
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
        rating = in.readInt();
        mmManaged = in.readByte() != 0;
        storageId = in.readString();
        simpleName = in.readString();
        mediaType = in.readString();
        mediaQuality = in.readString();
        gainTrackTP = in.readDouble();
        gainTrackRG = in.readDouble();
        compilation = in.readByte() != 0;
        publisher = in.readString();
        coverartMime = in.readString();
        audioStartTime = in.readDouble();
        mmReadError = in.readByte() != 0;
        dynamicRangeMeter = in.readDouble();
        upscaled = in.readByte() != 0;
        upsampled = in.readByte() != 0;
        dynamicRange = in.readDouble();
    }

    public static final Parcelable.Creator<MusicTag> CREATOR = new Creator<>() {
        @Override
        public MusicTag createFromParcel(Parcel in) {
            return new MusicTag(in);
        }

        @Override
        public MusicTag[] newArray(int size) {
            return new MusicTag[size];
        }
    };

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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
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

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileExtension) {
        this.fileFormat = fileExtension;
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

    public String getAudioDurationAsString() {
        return StringUtils.formatDuration(audioDuration, false);
    }

    public double getAudioDuration() {
        return audioDuration;
    }

    @NonNull
    @Override
    public MusicTag clone() {
        MusicTag tag = new MusicTag();
        tag.id = id;
        tag.uniqueKey = uniqueKey;
        tag.albumUniqueKey = albumUniqueKey;
        tag.path = path;
        tag.storageId = storageId;
        tag.simpleName = simpleName;
        tag.fileSize = fileSize;
        //tag.fileSizeRatio = fileSizeRatio;
        tag.fileFormat = fileFormat;
        tag.fileLastModified = fileLastModified;
        tag.mmReadError = mmReadError;

        tag.audioBitsDepth = audioBitsDepth;
        tag.audioBitRate = audioBitRate;
        tag.audioDuration = audioDuration;
        tag.audioSampleRate = audioSampleRate;
        tag.audioEncoding = audioEncoding;
        tag.audioChannels = audioChannels;
       // tag.audioLossless = audioLossless;

        tag.gainTrackRG = gainTrackRG;
        tag.gainTrackTP = gainTrackTP;
       // tag.gainTrackLoudness = gainTrackLoudness;
        tag.dynamicRangeMeter = dynamicRangeMeter;
       // tag.gainAlbumRG = gainAlbumRG;
       // tag.gainAlbumTruePeak=gainAlbumTruePeak;
        tag.upscaled = upscaled;
        tag.upsampled = upsampled;
        tag.dynamicRange = dynamicRange;
       // tag.measuredSamplingRate = measuredSamplingRate;
       // tag.measuredBitDept = measuredBitDept;

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
        tag.mediaType = mediaType;

        tag.mmManaged = mmManaged;
        tag.mediaQuality = mediaQuality;
        tag.rating = rating;

        tag.mqaScanned = mqaScanned;
        tag.mqaInd = mqaInd;
        tag.mqaSampleRate = mqaSampleRate;
       // tag.language = language;
        tag.audioStartTime = audioStartTime;
        tag.coverartMime = coverartMime;
        return tag;
    }

    public void cloneFrom(MusicTag tag) {
        this.id = tag.id;
        this.uniqueKey = tag.uniqueKey;
        this.albumUniqueKey = tag.albumUniqueKey;
        this.path = tag.path;
        this.fileSize = tag.fileSize;
        //this.fileSizeRatio = tag.fileSizeRatio;
        this.fileFormat = tag.fileFormat;
        this.fileLastModified = tag.fileLastModified;
        this.mmReadError = tag.mmReadError;

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
        this.mediaType = tag.mediaType;
        this.compilation = tag.compilation;

        this.mmManaged = tag.mmManaged;
        this.mediaQuality = tag.mediaQuality;
        this.rating = tag.rating;

        this.storageId = tag.storageId;
        this.simpleName = tag.simpleName;

        this.mqaScanned = tag.mqaScanned;
        this.mqaInd = tag.mqaInd;
        this.mqaSampleRate = tag.mqaSampleRate;

        this.gainTrackTP = tag.gainTrackTP;
        this.gainTrackRG = tag.gainTrackRG;
        //this.gainTrackLoudness = tag.gainTrackLoudness;
        this.dynamicRangeMeter = tag.dynamicRangeMeter;
        //this.gainAlbumRG = tag.gainAlbumRG;
       // this.gainAlbumTruePeak = tag.gainAlbumTruePeak;
        this.upscaled = tag.upscaled;
        this.upsampled = tag.upsampled;
        this.dynamicRange = tag.dynamicRange;
       // this.measuredSamplingRate=tag.measuredSamplingRate;
       // this.measuredBitDept=tag.measuredBitDept;

        this.publisher = tag.publisher;
        this.coverartMime = tag.coverartMime;
        this.audioStartTime = tag.audioStartTime;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(originTag, i);
        parcel.writeLong(id);
        parcel.writeString(uniqueKey);
        parcel.writeString(albumUniqueKey);
        parcel.writeString(path);
        parcel.writeLong(fileLastModified);
        parcel.writeLong(fileSize);
        //parcel.writeInt(fileSizeRatio);
        parcel.writeString(fileFormat);
        parcel.writeString(audioEncoding);
        //parcel.writeByte((byte) (audioLossless ? 1 : 0));
        parcel.writeByte((byte) (mqaScanned ? 1 : 0));
        parcel.writeString(mqaInd);
        parcel.writeLong(mqaSampleRate);
        parcel.writeInt(audioBitsDepth);
        parcel.writeLong(audioSampleRate);
        parcel.writeLong(audioBitRate);
        parcel.writeDouble(audioDuration);
        parcel.writeString(title);
        parcel.writeString(artist);
        parcel.writeString(album);
        parcel.writeString(year);
        parcel.writeString(genre);
        parcel.writeString(track);
        parcel.writeString(disc);
        parcel.writeString(comment);
        parcel.writeString(grouping);
        parcel.writeString(composer);
        parcel.writeString(albumArtist);
        parcel.writeInt(rating);
        parcel.writeByte((byte) (mmManaged ? 1 : 0));
        parcel.writeString(storageId);
        parcel.writeString(simpleName);
        parcel.writeString(mediaType);
        parcel.writeString(mediaQuality);
        parcel.writeDouble(gainTrackTP);
       // parcel.writeDouble(gainTrackLoudness);
        parcel.writeDouble(gainTrackRG);
       // parcel.writeDouble(gainAlbumTruePeak);
        //parcel.writeDouble(gainAlbumRG);
        parcel.writeByte((byte) (compilation ? 1 : 0));
        parcel.writeString(publisher);
       // parcel.writeString(language);
        parcel.writeString(coverartMime);
        parcel.writeDouble(audioStartTime);
        parcel.writeByte((byte) (mmReadError ? 1 : 0));
        parcel.writeDouble(dynamicRangeMeter);
        parcel.writeByte((byte) (upscaled ? 1 : 0));
        parcel.writeByte((byte) (upsampled ? 1 : 0));
        parcel.writeDouble(dynamicRange);
      //  parcel.writeInt(measuredBitDept);
       // parcel.writeLong(measuredSamplingRate);
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

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaQuality() {
        return mediaQuality;
    }

    public void setMediaQuality(String mediaQuality) {
        this.mediaQuality = mediaQuality;
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

    public String getCoverartMime() {
        return coverartMime;
    }

    public void setCoverartMime(String embedCoverArt) {
        this.coverartMime = embedCoverArt;
    }

    public double getTrackTP() {
        return gainTrackTP;
    }

    public void setTrackTP(double trackTruePeek) {
        this.gainTrackTP = trackTruePeek;
    }

    public double getTrackRG() {
        return gainTrackRG;
    }

    public void setTrackRG(double trackGain) {
        this.gainTrackRG = trackGain;
    }

    public boolean isMqaScanned() {
        return mqaScanned;
    }

    public void setMqaScanned(boolean mqaScanned) {
        this.mqaScanned = mqaScanned;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public double getDynamicRangeMeter() {
        return dynamicRangeMeter;
    }

    public void setDynamicRangeMeter(double trackDR) {
        this.dynamicRangeMeter = trackDR;
    }

    public void setReadError(boolean readError) {
        this.mmReadError = readError;
    }

    public double getDynamicRange() {
        return dynamicRange;
    }

    public void setDynamicRange(double measuredDR) {
        this.dynamicRange = measuredDR;
    }

    public boolean isUpscaled() {
        return upscaled;
    }

    public void setUpscaled(boolean upscaled) {
        this.upscaled = upscaled;
    }

    public boolean isUpsampled() {
        return upsampled;
    }

    public void setUpsampled(boolean upsampled) {
        this.upsampled = upsampled;
    }
}
