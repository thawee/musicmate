package apincer.android.mmate.objectbox;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import java.util.Objects;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;

import apincer.android.mmate.Constants;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.StringUtils;

@Entity
public class AudioTag implements Cloneable , Parcelable {
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    //
    @Transient
    private AudioTag originTag;
    @Transient private String musicBrainzId;
    @Transient private String artistId;
    @Transient private String albumId;

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public void setMusicBrainzId(String musicBrainzId) {
        this.musicBrainzId = musicBrainzId;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    // id auto assigned bny objectbox
    @Id
    protected long id;

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj==null) return false;
        if(obj instanceof  AudioTag) {
            return (id == ((AudioTag)obj).id)?true:false;
        }
        return false;
    }

    // file information
    protected String path = "";
    protected long lastModified = 0;
    @Transient
    protected String obsoletePath;
    protected long fileSize;
    protected String fileExtension;

    // Mate info
    private boolean managed;
    protected String storageId;
    protected String storageName;
    protected String simpleName;
    protected String source = "";
    protected boolean cueSheet;
    protected boolean audiophile;
    protected int rating; //0-10

    // audio information
    protected String audioEncoding; //AAC,MP3, ALAC, FLAC, DSD
    protected boolean lossless;
    protected boolean mqa;
    protected boolean mqaStudio;
    protected String mqaSampleRate;
    protected String audioChannels; // 2, 4

    public String getAudioChannels() {
        return audioChannels;
    }

    public void setAudioChannels(String audioChannels) {
        this.audioChannels = audioChannels;
    }

    protected int audioBitsPerSample; // 16/24/32 bits
    protected long audioSampleRate; //44100,48000,88200,96000,192000
    protected long audioBitRate; //128, 256, 320 kbps
    protected long audioDuration;

    public boolean isAudiophile() {
        return audiophile;
    }

    public void setAudiophile(boolean audiophile) {
        this.audiophile = audiophile;
    }

    // tags information
    protected String title= "";
    protected String artist = "";
    protected String album = "";
    private String year = "";
    private String genre = "";
    private String track = "";
    private String disc = "";
    private String comment = "";
    private String grouping = "";
    private String composer = "";
    protected String albumArtist="";


    public AudioTag() {
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public boolean isMqa() {
        return mqa;
    }

    public void setMqa(boolean mqa) {
        this.mqa = mqa;
    }

    public boolean isMQAStudio() {
        return mqaStudio;
    }

    public void setMQAStudio(boolean mqaStudio) {
        this.mqaStudio = mqaStudio;
    }

    public String getMQASampleRate() {
        return mqaSampleRate;
    }

    public void setMQASampleRate(String mqaSampleRate) {
        this.mqaSampleRate = mqaSampleRate;
    }

    protected AudioTag(Parcel in) {
        originTag = in.readParcelable(AudioTag.class.getClassLoader());
        id = in.readLong();
        path = in.readString();
        lastModified = in.readLong();
        obsoletePath = in.readString();
        fileSize = in.readLong();
        fileExtension = in.readString();
        audioEncoding = in.readString();
        lossless = in.readByte() != 0;
        mqa = in.readByte() != 0;
        mqaStudio = in.readByte() != 0;
        mqaSampleRate = in.readString();
        audioBitsPerSample = in.readInt();
        audioSampleRate = in.readLong();
        audioBitRate = in.readLong();
        audioDuration = in.readLong();
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
        managed = in.readByte() != 0;
        storageId = in.readString();
        simpleName = in.readString();
        source = in.readString();
        cueSheet = in.readByte() != 0;
        audiophile = in.readByte() != 0;
    }

    public static final Creator<AudioTag> CREATOR = new Creator<AudioTag>() {
        @Override
        public AudioTag createFromParcel(Parcel in) {
            return new AudioTag(in);
        }

        @Override
        public AudioTag[] newArray(int size) {
            return new AudioTag[size];
        }
    };

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public SearchCriteria.RESULT_TYPE getResultType() {
        return resultType;
    }

    public void setResultType(SearchCriteria.RESULT_TYPE resultType) {
        this.resultType = resultType;
    }

    @Transient
    protected SearchCriteria.RESULT_TYPE resultType = SearchCriteria.RESULT_TYPE.ALL;

    public void setManaged(boolean managed) {
        this.managed = managed;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isCueSheet() {
        return cueSheet;
    }

    public void setCueSheet(boolean cueSheet) {
        this.cueSheet = cueSheet;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setMQA(boolean mqa) {
        this.mqa = mqa;
    }

    public int getAudioBitsPerSample() {
        return audioBitsPerSample;
    }

    public void setAudioBitsPerSample(int audioBitsPerSample) {
        this.audioBitsPerSample = audioBitsPerSample;
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

    public String getObsoletePath() {
        return obsoletePath;
    }

    public void setObsoletePath(String obsoletePath) {
        this.obsoletePath = obsoletePath;
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

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public void setAudioDuration(long audioDuration) {
        this.audioDuration = audioDuration;
    }

    public void setPath(String mediaPath) {
        this.path = mediaPath;
    }

    public boolean isLossless() {
        return lossless;
    }

    public boolean isDSD() {
        return audioBitsPerSample==Constants.QUALITY_BIT_DEPTH_DSD;
    }
/*
    public boolean isDSD64() {
        return ( isDSD() && getAudioSampleRate() <= Constants.QUALITY_SAMPLING_RATE_DSD64);
    }

    public boolean isDSD128() {
        return ( isDSD() && getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_DSD64);
    }

    public boolean isPCM384() {
        return ( isLossless() && getAudioSampleRate() >= Constants.QUALITY_SAMPLING_RATE_352);
    }

    public boolean isPCM192() {
        return ( isLossless() && getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_176 && getAudioSampleRate() <= Constants.QUALITY_SAMPLING_RATE_192);
    }

    public boolean isPCM96PLUS() {
        return (isLossless() && getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_96);
    }

    public boolean isPCM96() {
        return (isLossless() && getAudioSampleRate() == Constants.QUALITY_SAMPLING_RATE_96);
    }

    public boolean isPCM88() {
        return (isLossless() && getAudioSampleRate() == Constants.QUALITY_SAMPLING_RATE_88);
    }

    public boolean isPCM48() {
        return (isLossless() && getAudioSampleRate() == Constants.QUALITY_SAMPLING_RATE_48);
    }

    public boolean isPCM44() {
        return (isLossless() && getAudioSampleRate() == Constants.QUALITY_SAMPLING_RATE_44);
    }

    public boolean isSDA() {
        return (isLossless() &&
         ((getAudioSampleRate() >= Constants.QUALITY_SAMPLING_RATE_44 && getAudioBitsPerSample() >= Constants.QUALITY_BIT_DEPTH_SD)));
    } */

    public boolean isMQA() {
        return mqa;
    }

    public void setLossless(boolean lossless) {
        this.lossless = lossless;
    }

    public String getPath() {
        return path;
    }

    public String getDSDRate() {
        //if(audioBitsPerSample==Constants.QUALITY_BIT_DEPTH_DSD) {
            //return "DSD"+(audioSampleRate/Constants.QUALITY_SAMPLING_RATE_44);
        return String.valueOf (audioSampleRate/Constants.QUALITY_SAMPLING_RATE_44);
        //}
        //return "";
    }

    public String getAudioBitCountAndSampleRate() {
        //PCM 16bit/44.1kHz,PCM 24bit/44.1kHz, PCM 32bit/192kHz, DSD 64, DSD 128, DSD 256, DSD 512
        String text = StringUtils.getFormatedBitsPerSample(audioBitsPerSample);
        text = text+" / "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);

        return text;
        /*
        //PCM 16bit/44.1kHz,PCM 24bit/44.1kHz, PCM 32bit/192kHz, DSD 64, DSD 128, DSD 256, DSD 512
        if(audioBitsPerSample==Constants.QUALITY_BIT_DEPTH_DSD) {
            //return "DSD"+(audioSampleRate/Constants.QUALITY_SAMPLING_RATE_44)+"x "+audioBitsPerSample+"bit/"+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
            //return "DSD"+(audioSampleRate/Constants.QUALITY_SAMPLING_RATE_44)+"x "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
            //return "x"+(audioSampleRate/Constants.QUALITY_SAMPLING_RATE_44)+" "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
            //return StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
            return audioBitsPerSample+" bit / "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
        } else if(isLossless()) {
           //return "PCM "+audioBitsPerSample+"bit/"+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
           //return "PCM "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true) +" / "+ audioBitsPerSample+"bit";
           // return getAudioEncoding()+" "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true) +" "+ audioBitsPerSample+" bit";
            //return StringUtils.getFormatedAudioSampleRate(audioSampleRate,true) +" "+ audioBitsPerSample+" bit";
            return audioBitsPerSample+" bit / "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
        }else {
            // MP3, AAC, stream quality
            //return getAudioEncoding() + " " +StringUtils.getFormatedAudioBitRate(getAudioBitRate())+" "+audioBitsPerSample+"bit/"+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
            //return getAudioEncoding() + " " +StringUtils.getFormatedAudioBitRate(getAudioBitRate())+" "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
          //  return StringUtils.getFormatedAudioBitRate(getAudioBitRate())+" "+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
           // return StringUtils.getFormatedAudioSampleRate(audioSampleRate,true)+" "+audioBitsPerSample+" bit";
            return audioBitsPerSample+" bit/"+StringUtils.getFormatedAudioSampleRate(audioSampleRate,true);
        } */
    }

    public String getAudioDurationAsString() {
            return StringUtils.formatDuration(audioDuration, false);
    }

    public long getAudioDuration() {
        return audioDuration;
    }

    @Override
    public AudioTag clone() {
        AudioTag tag = new AudioTag();
        tag.id = id;
        tag.path = path;
        tag.storageId = storageId;
        tag.simpleName = simpleName;
        tag.fileSize = fileSize;
        tag.fileExtension = fileExtension;
        tag.lastModified=lastModified;

        tag.audioBitsPerSample=audioBitsPerSample;
        tag.audioBitRate=audioBitRate;
        tag.audioDuration=audioDuration;
        tag.audioSampleRate=audioSampleRate;
        tag.audioEncoding=audioEncoding;
        tag.audioChannels=audioChannels;
        tag.lossless=lossless;

        tag.title=title;
        tag.album=album;
        tag.artist=artist;
        tag.albumArtist=albumArtist;
        tag.genre=genre;
        tag.year=year;
        tag.track=track;
        tag.disc=disc;
        tag.comment=comment;
        tag.grouping=grouping;
        tag.composer =composer;
        tag.source =source;

        tag.managed = managed;
        tag.audiophile =audiophile;
        tag.rating = rating;

        tag.mqa = mqa;
        tag.mqaStudio = mqaStudio;
        tag.mqaSampleRate = mqaSampleRate;
        return tag;
    }

    public boolean isManaged() {
        return managed;
    }

    public boolean isHDA() {
        return isLossless() && getAudioBitsPerSample()>= Constants.QUALITY_BIT_DEPTH_HD && getAudioSampleRate()>= Constants.QUALITY_SAMPLING_RATE_44;
    }

    public void cloneFrom(AudioTag tag) {
        this.id = tag.id;
        this.path = tag.path;
        this.fileSize = tag.fileSize;
        this.fileExtension = tag.fileExtension;
        this.lastModified=tag.lastModified;

        this.audioBitsPerSample=tag.audioBitsPerSample;
        this.audioBitRate=tag.audioBitRate;
        this.audioDuration=tag.audioDuration;
        this.audioSampleRate=tag.audioSampleRate;
        this.audioEncoding=tag.audioEncoding;
        this.audioChannels=tag.audioChannels;

        this.title=tag.title;
        this.album=tag.album;
        this.artist=tag.artist;
        this.albumArtist=tag.albumArtist;
        this.genre=tag.genre;
        this.year=tag.year;
        this.track=tag.track;
        this.disc=tag.disc;
        this.comment=tag.comment;
        this.grouping=tag.grouping;
        this.composer =tag.composer;
        this.source =tag.source;

        this.managed = tag.managed;
        this.audiophile = tag.audiophile;
        this.rating = tag.rating;

        this.storageId = tag.storageId;
        this.storageName = tag.storageName;
        this.simpleName = tag.simpleName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(originTag, i);
        parcel.writeLong(id);
        parcel.writeString(path);
        parcel.writeLong(lastModified);
        parcel.writeString(obsoletePath);
        parcel.writeLong(fileSize);
        parcel.writeString(fileExtension);
        parcel.writeString(audioEncoding);
        parcel.writeByte((byte) (lossless ? 1 : 0));
        parcel.writeByte((byte) (mqa ? 1 : 0));
        parcel.writeByte((byte) (mqaStudio ? 1 : 0));
        parcel.writeString(mqaSampleRate);
        parcel.writeInt(audioBitsPerSample);
        parcel.writeLong(audioSampleRate);
        parcel.writeLong(audioBitRate);
        parcel.writeLong(audioDuration);
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
        parcel.writeByte((byte) (managed ? 1 : 0));
        parcel.writeString(storageId);
        parcel.writeString(simpleName);
        parcel.writeString(source);
        parcel.writeByte((byte) (cueSheet ? 1 : 0));
        parcel.writeByte((byte) (audiophile ? 1 : 0));
    }

    public AudioTag getOriginTag() {
        return originTag;
    }

    public void setOriginTag(AudioTag originTag) {
        this.originTag = originTag;
    }
}
