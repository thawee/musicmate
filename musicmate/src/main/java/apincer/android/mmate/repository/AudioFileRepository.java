package apincer.android.mmate.repository;

import android.content.Context;
import android.graphics.Bitmap;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.wav.WavTag;
import org.jetbrains.annotations.NotNull;
import org.justcodecs.dsd.DISOFormat;
import org.justcodecs.dsd.Scarletbook;
import org.justcodecs.dsd.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.cue.CueParse;
import apincer.android.mmate.cue.Track;
import apincer.android.mmate.fs.MusicMateArtwork;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;
import timber.log.Timber;

/**
 * Wrapper class for accessing media information via media store and jaudiotagger
 * Created by e1022387 on 5/10/2017.
 */
public class AudioFileRepository {
    private static final String MQA_ENCODER="MQAENCODER";
    private static final String MQA_ORIGINAL_SAMPLE_RATE="ORIGINALSAMPLERATE";
    private static final String MQA_ORIGINAL_SAMPLING_FREQUENCY = "ORFS";
    private static final String MQA_SAMPLE_RATE="MQASAMPLERATE";

    private static AudioFileRepository INSTANCE;

    private final Context context;
    private final MediaFileRepository fileRepos;
    private final AudioTagRepository tagRepos;
    private String STORAGE_PRIMARY = StorageId.PRIMARY;
    private String STORAGE_SECONDARY;

    public static final String ACTION = "com.apincer.mmate.provider.MediaItemProvider";

    public Context getContext() {
        return context;
    }

    private AudioFileRepository(Context context) {
        super();
        this.context = context;
        STORAGE_SECONDARY = getSecondaryId(context);
        this.fileRepos = new MediaFileRepository(context);
        this.tagRepos = AudioTagRepository.getInstance(); //new AudioTagRepository();
        INSTANCE = this;
    }

    public static String getSecondaryId(Context context) {
        List<String> sids = DocumentFileCompat.getStorageIds(context);
        for(String sid: sids) {
            if(!sid.equals(StorageId.PRIMARY)) {
                return sid;
                //break;
            }
        }
        return StorageId.PRIMARY;
    }

    public static AudioFileRepository getInstance(Context context) {
        if(INSTANCE==null) {
            INSTANCE = new AudioFileRepository(context);
        }
        return INSTANCE;
    }

    public static InputStream getArtworkAsStream(String mediaPath) {
        InputStream input = getEmbedArtworkAsStream(mediaPath);
        if (input == null) {
            input = getFolderArtworkAsStream(mediaPath);
        }
        return input;
    }

    // called by glide
    public static InputStream getArtworkAsStream(AudioTag item) {
        InputStream input = getEmbedArtworkAsStream(item);
        if (input == null) {
            input = getFolderArtworkAsStream(item);
        }
        return input;
    }

    public static InputStream getFolderArtworkAsStream(AudioTag mediaItem) {
        // try loading from folder
        // front.png, front.jpg
        // cover.png, cover.jpg

            File coverFile = new File(mediaItem.getPath());
            File coverDir = coverFile.getParentFile();

            for (String f : Constants.IMAGE_COVERS) {
                coverFile = new File(coverDir, f);
                if(coverFile.exists()) break;
            }
            if(coverFile!=null && coverFile.exists()) {
                try {
                    return new FileInputStream(coverFile);
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
            return null;
    }


    public static InputStream getFolderArtworkAsStream(String mediaPath) {
        // try loading from folder
        // front.png, front.jpg
        // cover.png, cover.jpg

        File coverFile = null;
        File mediaFile = new File(mediaPath);
        File coverDir = mediaFile.getParentFile();

        for (String f : Constants.IMAGE_COVERS) {
            File cFile = new File(coverDir, f);
            if(cFile.exists()) {
                coverFile = cFile;
                break;
            }
        }
        if(coverFile==null) {
            // check *.png, *.jpg
            File [] files = coverDir.listFiles();
            for(File f : files) {
                if(Constants.COVER_IMAGE_TYPES.contains(FileUtils.getExtension(f).toLowerCase())) {
                    coverFile = f;
                    break;
                }
            }
        }
        if(coverFile!=null && coverFile.exists()) {
            try {
                return new FileInputStream(coverFile);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    public static InputStream getEmbedArtworkAsStream(String mediaPath) {
        try {
            AudioFile audioFile = buildAudioFile(mediaPath, "r");

            if (audioFile == null) {
                return null;
            }
            Artwork artwork = audioFile.getTagOrCreateDefault().getFirstArtwork();
            if (null != artwork) {
                byte[] artworkData = artwork.getBinaryData();
                return new ByteArrayInputStream(artworkData);
            }
        }catch(Exception ex) {
            Timber.e(ex);
        }
        return null;
    }

    public static InputStream getEmbedArtworkAsStream(AudioTag mediaItem) {
        try {
            AudioFile audioFile = buildAudioFile(mediaItem.getPath(), "r");

            if (audioFile == null) {
                return null;
            }
            Artwork artwork = audioFile.getTagOrCreateDefault().getFirstArtwork();
            if (null != artwork) {
                byte[] artworkData = artwork.getBinaryData();
                return new ByteArrayInputStream(artworkData);
            }
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public byte[] getArtworkAsByte(AudioTag mediaItem) {
        InputStream ins = getArtworkAsStream(mediaItem);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int n;
            byte[] buffer = new byte[1024];
            while ((n = ins.read(buffer)) > 0) {
                outputStream.write(buffer, 0, n);
            }
            return outputStream.toByteArray();
        }catch (Exception ex) {}
        return null;
    }

    public static AudioFile buildAudioFile(String path, String mode) {
            try {
                if(isMediaFileExist(path) && isValidJAudioTagger(path)) {
                    setupTagOptionsForReading();
                    if(mode!=null && mode.indexOf("w")>=0) {
                        setupTagOptionsForWriting();
                    }
                    AudioFile audioFile = AudioFileIO.read(new File(path));
                    return audioFile;
                }
            } catch (CannotReadException | IOException | TagException |ReadOnlyFileException |InvalidAudioFrameException e) {
                Timber.i(e);
            }
        return null;
    }

    private static void setupTagOptionsForReading() {
        TagOptionSingleton.getInstance().setAndroid(true);
        TagOptionSingleton.getInstance().setId3v23DefaultTextEncoding(TextEncoding.ISO_8859_1); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24DefaultTextEncoding(TextEncoding.UTF_8); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24UnicodeTextEncoding(TextEncoding.UTF_16); // default UTF-16

    }

    private static void setupTagOptionsForWriting() {
        TagOptionSingleton.getInstance().setAndroid(true);
        TagOptionSingleton.getInstance().setResetTextEncodingForExistingFrames(true);
        TagOptionSingleton.getInstance().setID3V2Version(ID3V2Version.ID3_V24);
        TagOptionSingleton.getInstance().setWriteMp3GenresAsText(true);
        TagOptionSingleton.getInstance().setWriteMp4GenresAsText(true);
        TagOptionSingleton.getInstance().setPadNumbers(true);
        TagOptionSingleton.getInstance().setRemoveTrailingTerminatorOnWrite(true);
       // TagOptionSingleton.getInstance().setRemoveID3FromFlacOnSave(true);
        TagOptionSingleton.getInstance().setLyrics3Save(true);
        TagOptionSingleton.getInstance().setVorbisAlbumArtistSaveOptions(VorbisAlbumArtistSaveOptions.WRITE_ALBUMARTIST_AND_DELETE_JRIVER_ALBUMARTIST);
    }

    public AudioTag findMediaItem(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<AudioTag> list = tagRepos.findMediaByTitle(currentTitle);

            double prvTitleScore = 0.0;
            double prvArtistScore = 0.0;
            double prvAlbumScore = 0.0;
            double titleScore = 0.0;
            double artistScore = 0.0;
            double albumScore = 0.0;
            AudioTag matchedMeta = null;

            for (AudioTag metadata : list) {
                titleScore = StringUtils.similarity(currentTitle, metadata.getTitle());
                artistScore = StringUtils.similarity(currentArtist, metadata.getArtist());
                albumScore = StringUtils.similarity(currentAlbum, metadata.getAlbum());

                if (getSimilarScore(titleScore, artistScore, albumScore) > getSimilarScore(prvTitleScore, prvArtistScore, prvAlbumScore)) {
                    matchedMeta = metadata;
                    prvTitleScore = titleScore;
                    prvArtistScore = artistScore;
                    prvAlbumScore = albumScore;
                }
            }
            if (matchedMeta != null) {
                return matchedMeta.clone();
            }
        }catch (SQLException sqle) {
            Timber.e(sqle);
        }
        return null;
    }

    private double getSimilarScore(double titleScore, double artistScore, double albumScore) {
        return (titleScore*60)+(artistScore*20)+(albumScore*20);
    }

    private static boolean isValidJAudioTagger(String path) {
        String ext = FileUtils.getExtension(path);
        if(StringUtils.isEmpty(ext)) {
            return false;
        }
        try {
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    public boolean saveArtworkToFile(AudioTag item, String filePath) {
        boolean isFileSaved = false;
        try {
            byte[] artwork = getArtworkAsByte(item);
            if(artwork!=null) {
                File f = new File(filePath);
                if (f.exists()) {
                    f.delete();
                }
                f.createNewFile();
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(artwork);
                fos.flush();
                fos.close();
                isFileSaved = true;
            }
            // File Saved
        } catch (FileNotFoundException e) {
            Timber.e(e);
        } catch (IOException e) {
            Timber.e(e);
        }
        return isFileSaved;
    }

    private boolean isValidTagValue(String newTag) {
        // && !StringUtils.trimToEmpty(oldTag).equals(newTag)) {
        return !StringUtils.MULTI_VALUES.equalsIgnoreCase(newTag);
    }

    private String getId3TagValue(Tag id3Tag, FieldKey key) {
        if(id3Tag == null) {
            return "";
        }
        try {
            return StringUtils.trimToEmpty(id3Tag.getFirst(key));
        }catch (Exception ex) {
            Timber.e(ex);
        }
        return "";
    }

    private void setTagField(AudioFile audioFile, Tag id3Tag,FieldKey key, String text) {
        try {
            if(isValidTagValue(text)) {
                if (StringUtils.isEmpty(text)) {
                    id3Tag.deleteField(key);
                } else {
                    id3Tag.setField(key, text);
                }
                audioFile.commit();
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public boolean saveTag(AudioTag item) throws Exception{
        if (item == null || item.getPath() == null) {
            return false;
        }

        if(item.getOriginTag()==null) {
            return false;
        }

        item.setManaged(StringUtils.compare(item.getPath(),buildCollectionPath(item)));
        File file = new File(item.getPath());
        setupTagOptionsForWriting();
        AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");

        // save default tags
        Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
        saveTagsToFile(audioFile, existingTags, item);
        item.setOriginTag(null); // reset pending tag

        tagRepos.saveTag(item);
        return true;
    }

    private void saveTagsToFile(AudioFile audioFile, Tag tags, AudioTag pendingMetadata) {
        if (tags == null || pendingMetadata==null) {
            return;
        }
        setTagField(audioFile,tags,FieldKey.TITLE, pendingMetadata.getTitle());
        setTagField(audioFile,tags,FieldKey.ALBUM, pendingMetadata.getAlbum());
        setTagField(audioFile,tags,FieldKey.ARTIST, pendingMetadata.getArtist());
        setTagField(audioFile,tags,FieldKey.ALBUM_ARTIST, pendingMetadata.getAlbumArtist());
        setTagField(audioFile,tags,FieldKey.GENRE, pendingMetadata.getGenre());
        setTagField(audioFile,tags,FieldKey.YEAR, pendingMetadata.getYear());
        setTagField(audioFile,tags,FieldKey.TRACK, pendingMetadata.getTrack());
        setTagField(audioFile,tags,FieldKey.COMPOSER, pendingMetadata.getComposer());
        if(!(tags instanceof WavTag)) {
            setTagField(audioFile,tags,FieldKey.DISC_NO, pendingMetadata.getDisc());
            setTagField(audioFile,tags,FieldKey.GROUPING, pendingMetadata.getGrouping());
//            setTagField(audioFile,tags,FieldKey.COMMENT, pendingMetadata.getComment());
        }
        setTagField(audioFile,tags,FieldKey.MEDIA, pendingMetadata.getSource());
        setTagField(audioFile,tags,FieldKey.QUALITY, pendingMetadata.isAudiophile()?Constants.AUDIOPHILE:"");
        setTagField(audioFile,tags,FieldKey.RATING, String.valueOf(pendingMetadata.getRating()));
        saveCommentTag(audioFile, tags, pendingMetadata);
    }

    public void scanFileAndSaveTag(File file) {
        String mediaPath = file.getAbsolutePath();
        if (tagRepos.isMediaOutdated(mediaPath, file.lastModified())) {
                AudioTag[] metadataList = readMediaTag(mediaPath);
                if (metadataList != null && metadataList.length > 0) {
                    for (AudioTag mdata : metadataList) {
                        if(mdata != null) {
                            String matePath = buildCollectionPath(mdata);
                            mdata.setManaged(StringUtils.equals(matePath, mdata.getPath()));
                            tagRepos.saveTag(mdata);
                        }
                    }
                }
        }
    }

    private AudioTag[] readMetadata(AudioTag metadata) {
        try {
            String path = metadata.getPath();
            if(isValidJAudioTagger(path)) {
                AudioFile audioFile = buildAudioFile(path, "r");

                if (audioFile == null) {
                    return null;
                }

                readJAudioHeader(audioFile, metadata); //16/24/32 bit and 44.1/48/96/192 kHz
                if (readJAudioTagger(audioFile, metadata)) {
                    metadata.setLastModified(audioFile.getFile().lastModified());
                }
                return readCueSheet(metadata);
            }else if(isValidJustDSD(path)){
                // sacd iso
                return readJustDSD(path, metadata);
            }
            return null;
        } catch (Exception |OutOfMemoryError oom) {
            Timber.e(oom);
        }
        return null;
    }

    private AudioTag[] readCueSheet(AudioTag metadata) {
        AudioTag[] mList = null;
        int validTitle = 0;
        try {
            String path = metadata.getPath();
            File dirFile = new File(path);
            dirFile = dirFile.getParentFile();

            if(CueParse.isFolderValid(dirFile)) {
                // cue
                CueParse parser = new CueParse();
                ArrayList<File> cueList = CueParse.getCueFile(dirFile);
                for (File cueFile: cueList) {
                    parser.parseCueFile(cueFile);
                   // ArrayList<String> paths = parser.getTrackPaths(cueFile);
                    //if(paths.size()==1 && FileUtils.isExisted(paths.get(CueParse.FLAC_PATH_POS))) {
                    //    parser.parseCueFile(cueFile);
                    //}
                }
                List<Track> tracks = parser.getTracks();
                if(tracks != null && tracks.size()>0) {
                    mList = new AudioTag[tracks.size()];
                    for (int t = 0; t < tracks.size(); t++) {
                        Track track = tracks.get(t);
                        File file = new File(track.getFile());
                        if(!file.exists()) continue;

                        mList[t] = metadata.clone();
                        mList[t].setCueSheet(true);
                        mList[t].setFileSize(metadata.getFileSize());
                        mList[t].setTitle(track.getTitle());
                        mList[t].setArtist(track.getArtist());
                        mList[t].setAlbum(track.getAlbum());
                        mList[t].setAudioDuration(track.getDuration());
                        mList[t].setTrack(String.format("%02d", t + 1));
                        mList[t].setPath(path);
                        mList[t].setSimpleName(metadata.getSimpleName());
                        mList[t].setId(tagRepos.getAudioTagId(mList[t]));
                        validTitle++;
                    }
                }
            }else {
                // single file, no cue
                mList = new AudioTag[1];
                mList[0] = metadata;
                metadata.setId(tagRepos.getAudioTagId(metadata));
            }
        } catch (Exception |OutOfMemoryError oom) {
            Timber.e(oom);
            mList = new AudioTag[1];
            mList[0] = metadata;
            metadata.setId(tagRepos.getAudioTagId(metadata));
        }
        if(validTitle == 0) {
            // found invalid cue lib file, use yag from audiofile
            Timber.i("found invalid cue lib file, use yag from audio file, %s",metadata.getPath());
            mList = new AudioTag[1];
            mList[0] = metadata;
        }
        return mList;
    }

    private AudioTag[] readJustDSD(String path, AudioTag metadata) {
        // FIXME:
        try {
            if(isMediaFileExist(path) && isValidJustDSD(path)) {
                File iso = new File(path);
                long lastModified = iso.lastModified();
                long length = iso.length();
                DISOFormat dsf = new DISOFormat();
                dsf.init(new Utils.RandomDSDStream(iso));
                String album = (String) dsf.getMetadata("Album");
                if (album == null)
                    album = (String) dsf.getMetadata("Title");
                if (album == null) {
                    album = iso.getName();
                    album = album.substring(0, album.length() - 4);
                }
                String genre = String.format("%s", Utils.nvl(dsf.getMetadata("Genre"), ""));
                String year = String.format("%s", dsf.getMetadata("Year"));
                //String genre = String.format("REM TOTAL %02d:%02d%n", dsf.atoc.minutes, dsf.atoc.seconds));
                AudioTag[] mList = null;
                Scarletbook.TrackInfo[] tracks = (Scarletbook.TrackInfo[]) dsf.getMetadata("Tracks");
                if(tracks != null && tracks.length >0) {
                    mList = new AudioTag[tracks.length];
                    for (int t = 0; t < tracks.length; t++) {
                        mList[t] = new AudioTag();
                        mList[t].setLossless(false);
                        mList[t].setCueSheet(true);
                        mList[t].setAudioBitRate(dsf.getSampleCount());
                        mList[t].setAudioBitsPerSample(1);
                        mList[t].setAudioSampleRate(dsf.getSampleRate());
                        mList[t].setAudioEncoding(Constants.MEDIA_ENC_SACD);
                        mList[t].setFileExtension("ISO");
                        mList[t].setLastModified(lastModified);
                        mList[t].setFileSize(length);
                        mList[t].setAlbum(album);
                        mList[t].setGenre(genre);
                        mList[t].setYear(year);
                        mList[t].setTrack(String.format("%02d", t + 1));
                        mList[t].setPath(path);
                        mList[t].setSimpleName(metadata.getSimpleName());
                      //  mList[t].setMediaId(path+"#"+mList[t].getTrack());

                        mList[t].setTitle(String.format("%s",Utils.nvl(StringUtils.normalizeName(tracks[t].get("title")), "NA")));
                        if (tracks[t].get("performer") != null) {
                            mList[t].setArtist(String.format("%s", StringUtils.normalizeName(tracks[t].get("performer"))));
                        }

                        if (dsf.textDuration > 0) {
                            int start = (int) Math.round(dsf.getTimeAdjustment() * tracks[t].start);
                            mList[t].setAudioDuration(start);
                            //cuew.write(String.format("    INDEX 01 %02d:%02d:%02d%n", start / 60, start % 60, 0));
                        } else {
                            mList[t].setAudioDuration(tracks[t].start+tracks[t].startFrame);
                            //cuew.write(String.format("    INDEX 01 %02d:%02d:%02d%n", tracks[t].start / 60,
                            //        tracks[t].start % 60, tracks[t].startFrame));
                        }
                    }
                }else {
                    mList = new AudioTag[1];
                  //  metadata.setMediaId(path);
                    metadata.setCueSheet(false);
                    metadata.setPath(path);
                    metadata.setAlbum(album);
                    metadata.setTitle(album);
                    metadata.setGenre(genre);
                    metadata.setYear(year);
                    metadata.setAudioBitRate(dsf.getSampleCount());
                    metadata.setAudioBitsPerSample(1);
                    metadata.setAudioSampleRate(dsf.getSampleRate());
                    metadata.setAudioEncoding(Constants.MEDIA_ENC_SACD);
                    metadata.setFileExtension("ISO");
                    metadata.setLastModified(lastModified);
                    metadata.setFileSize(length);
                    metadata.setAudioDuration((dsf.atoc.minutes*60)+dsf.atoc.seconds);
                    mList[0] = metadata;
                }
                dsf.close();
                return mList;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isValidJustDSD(String path) {
        return path.toLowerCase().endsWith(".iso");
    }

    private boolean readJAudioTagger(AudioFile audioFile, AudioTag mediaTag) {
        Tag tag = audioFile.getTag(); //TagOrCreateDefault();
        if (tag != null && !tag.isEmpty()) {
            mediaTag.setTitle(getId3TagValue(tag,FieldKey.TITLE));
            if(StringUtils.isEmpty(mediaTag.getTitle())) {
                //default to file name
                mediaTag.setTitle(FileUtils.removeExtension(audioFile.getFile()));
            }
            mediaTag.setAlbum(getId3TagValue(tag, FieldKey.ALBUM));
            mediaTag.setArtist(getId3TagValue(tag, FieldKey.ARTIST));
            mediaTag.setAlbumArtist(getId3TagValue(tag, FieldKey.ALBUM_ARTIST));
            mediaTag.setGenre(getId3TagValue(tag, FieldKey.GENRE));
            mediaTag.setYear(getId3TagValue(tag, FieldKey.YEAR));
            mediaTag.setTrack(getId3TagValue(tag, FieldKey.TRACK));
            mediaTag.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
            if(tag instanceof FlacTag) {
                // check MQA Tag
                if(tag.hasField(MQA_ENCODER) ||
                        tag.hasField(MQA_ORIGINAL_SAMPLING_FREQUENCY) ||
                        tag.hasField(MQA_ORIGINAL_SAMPLE_RATE) ||
                        tag.hasField(MQA_SAMPLE_RATE)
                ) {
                    mediaTag.setMQA(true);
                    if(tag.hasField(MQA_ORIGINAL_SAMPLING_FREQUENCY)) {
                        mediaTag.setMQASampleRate(tag.getFirst(MQA_ORIGINAL_SAMPLING_FREQUENCY));
                    }else if(tag.hasField(MQA_ORIGINAL_SAMPLE_RATE)) {
                        mediaTag.setMQASampleRate(tag.getFirst(MQA_ORIGINAL_SAMPLE_RATE));
                    }else if(tag.hasField(MQA_SAMPLE_RATE)) {
                        mediaTag.setMQASampleRate(tag.getFirst(MQA_SAMPLE_RATE));
                    }else {
                        mediaTag.setMQASampleRate(String.valueOf(mediaTag.getAudioSampleRate()));
                    }
                }
            }
            if(!(tag instanceof WavTag)) {
                // WAV file not support these fields
                mediaTag.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
                mediaTag.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
               // mediaTag.setComment(getId3TagValue(tag, FieldKey.COMMENT));
            }//else {
                // save grouping in comment for wave file
               // mediaTag.setGrouping(getId3TagValue(tag, FieldKey.COMMENT));
           // }
            mediaTag.setAudiophile(Constants.AUDIOPHILE.equals(getId3TagValue(tag,FieldKey.QUALITY)));
            mediaTag.setSource(getId3TagValue(tag,FieldKey.MEDIA));
            mediaTag.setRating(getId3TagIntValue(tag,FieldKey.RATING));
            readCommentTag(mediaTag,getId3TagValue(tag, FieldKey.COMMENT));
            return true;
        }
        return false;
    }

    private int getId3TagIntValue(Tag tag, FieldKey key) {
        if(tag == null) {
            return 0;
        }
        try {
            String val = StringUtils.trimToEmpty(tag.getFirst(key));
            if(!StringUtils.isEmpty(val)) {
                return Integer.parseInt(val);
            }
        }catch (Exception ex) {
            Timber.e(ex);
        }
        return 0;
    }

    private void saveCommentTag(AudioFile audioFile,Tag tags, AudioTag pendingMetadata) {
        String comment = StringUtils.trimToEmpty(pendingMetadata.getComment());
            // add grouping
            if(!StringUtils.isEmpty(pendingMetadata.getGrouping())) {
                if(!StringUtils.isEmpty(comment)) {
                    comment = "~~"+comment;
                }
                comment = "GRP::"+StringUtils.trimToEmpty(pendingMetadata.getGrouping())+comment;
            }
            //add source
            if(!StringUtils.isEmpty(pendingMetadata.getSource())) {
                if(!StringUtils.isEmpty(comment)) {
                    comment = "~~"+comment;
                }
                comment = "SRC::"+StringUtils.trimToEmpty(pendingMetadata.getSource())+comment;
            }
        setTagField(audioFile,tags,FieldKey.COMMENT, comment);
    }

    private void readCommentTag(AudioTag mediaTag, String comment) {
        comment = StringUtils.trimToEmpty(comment);

        // grouping
        int start = comment.indexOf("GRP::");
        if(start>=0) {
            int end = comment.indexOf("~~", start);
            int offset =0;
            if(end <=start) {
                end = comment.length();
            }else {
                offset = 2;
            }
            String grouping = comment.substring((start+"GRP::".length()),end);
            if(start>0) {
                comment = comment.substring(0, start);
            }else if(comment.length()>= end) {
                comment = comment.substring(end+offset);
            }
            mediaTag.setGrouping(grouping);
        }
        //source
        start = comment.indexOf("SRC::");
        if(start>=0) {
            int end = comment.indexOf("~~", start);
            int offset =0;
            if(end <=start) {
                end = comment.length();
            }else {
                offset = 2;
            }
            String source = comment.substring((start+"SRC::".length()),end);
            if(start>0) {
                comment = comment.substring(0, start);
            }else if(comment.length()>= end) {
                comment = comment.substring(end+offset);
            }
            mediaTag.setSource(source);
        }

        // comment
        mediaTag.setComment(comment);
    }

    public static Bitmap getArtwork(AudioTag item, boolean bigscale) {
        try {
            InputStream input = getArtworkAsStream(item);
            if(input!=null) {
                int size = bigscale ? 1024 : 320;
                return BitmapHelper.decodeBitmapFromStream(input, size, size);
            }
        } catch (Exception ex) {
                ex.printStackTrace();
        }
        return null;
    }
/*
    private static Bitmap getFolderArtwork(AudioTag item) {
        File coverFile = new File(item.getPath());
        File coverDir = coverFile.getParentFile();
        for (String f : Constants.IMAGE_COVERS) {
                coverFile = new File(coverDir, f);
                if(coverFile.exists()) break;
        }
        if(coverFile.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    return BitmapFactory.decodeFile(coverFile.getAbsolutePath(), options);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
        }
        return null;
    } */

    private void readJAudioHeader(AudioFile read, AudioTag metadata) {
        try {
            AudioHeader header = read.getAudioHeader();
            long sampleRate = header.getSampleRateAsNumber(); //44100/48000 Hz
            int bitPerSample = header.getBitsPerSample(); //16/24/32
            long bitRate = header.getBitRateAsNumber(); //128/256/320
            metadata.setLossless(header.isLossless());
            metadata.setFileSize(read.getFile().length());

            int ch = 2;
            metadata.setAudioEncoding(detectAudioEncoding(read,header.isLossless()));
            if(header.getTrackLength()>0) {
                int length = header.getTrackLength();
                metadata.setAudioDuration(length);
            }else {
                // calculate duration
                //duration = filesize in bytes / (samplerate * #of channels * (bitspersample / 8 ))
                long duration = metadata.getFileSize() / (sampleRate+ch) * (bitPerSample);
                metadata.setAudioDuration(duration);
            }
            metadata.setAudioBitRate(bitRate);
            metadata.setAudioBitsPerSample(bitPerSample);
            metadata.setAudioSampleRate(sampleRate);
            metadata.setAudioChannels(header.getChannels());
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }

    private String detectAudioEncoding(AudioFile read, boolean isLossless) {
        String encType = read.getExt();
        if(StringUtils.isEmpty(encType)) return "";

        if("m4a".equalsIgnoreCase(encType)) {
            if(isLossless) {
                encType = Constants.MEDIA_ENC_ALAC;
            }else {
                encType = Constants.MEDIA_ENC_AAC;
            }
        }else if("wav".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_WAVE;
        }else if("aif".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_AIFF;
        }else if("flac".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_FLAC;
        }else if("mp3".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_MP3;
        }else if("dsf".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DSF;
        }else if("dff".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DFF;
        }else if("iso".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_SACD;
        }
        return  encType.toUpperCase();
    }

    public String buildCollectionPath(@NotNull AudioTag metadata) {
        // hierarchy directory
        // 1. Collection (Jazz Collection, Isan Collection, Thai Collection, World Collection, Classic Collection, etc)
        // 2. hires, lossless, mqa, etc
        // 3. artist|albumArtist
        // 4. album
        // 5. file name <track no>-<artist>-<title>
        // [Hi-Res|Lossless|Compress]/<album|albumartist|artist>/<track no>-<artist>-<title>
        // /format/<album|albumartist|artist>/<track no> <artist>-<title>
        final String ReservedChars = "?|\\*<\":>[]~#%^@.";
        try {
            String musicPath ="Music/";
            String storageId = getStorageIdFor(metadata);
            String ext = FileUtils.getExtension(metadata.getPath());
            StringBuffer filename = new StringBuffer(musicPath);

            if(!StringUtils.isEmpty(metadata.getGrouping())) {
                filename.append(StringUtils.formatTitle(metadata.getGrouping())).append(File.separator);
            }

            String encSuffix = "";
            if (metadata.isMQA()) {
                encSuffix="-MQA";
            }else if (AudioTagUtils.isPCMHiRes(metadata)) {
                encSuffix="-HRA";
            }

            if(AudioTagUtils.isDSD(metadata)) {
                filename.append(Constants.MEDIA_PATH_DSD);
                filename.append("-");
                filename.append(AudioTagUtils.getDSDSampleRateModulation(metadata));
            //}else if (metadata.isMQA()) {
            //    filename.append(Constants.MEDIA_PATH_MQA);
//            }else if (AudioTagUtils.isPCMHiRes(metadata)) {
  //              filename.append(Constants.MEDIA_PATH_HR);
           // }else if (AudioTagUtils.isPCMLossless(metadata)) {
           //     filename.append(Constants.MEDIA_PATH_HR);
            }else if (Constants.MEDIA_ENC_ALAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_ALAC);
                filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_FLAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_FLAC);
                filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_WAVE.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_WAVE);
                filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_AIFF.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_AIFF);
                filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_AAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_ACC);
                filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_MP3.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_MP3);
                filename.append(encSuffix);
            }else {
                filename.append(Constants.MEDIA_PATH_OTHER);
                filename.append(encSuffix);
            }
            filename.append(File.separator);

            // albumArtist or artist

            String artist = StringUtils.trimTitle(metadata.getArtist());
            String albumArtist = StringUtils.trimTitle(metadata.getAlbumArtist());

            String pathArtist = getAlbumArtistOrArtist(artist, albumArtist);

            if(StringUtils.isEmpty(pathArtist)) {
                pathArtist = StringUtils.UNKNOWN_ARTIST;
            }
            if(!StringUtils.isEmpty(pathArtist)) {
                filename.append(StringUtils.formatTitle(pathArtist)).append(File.separator);
            }

            // album
            String album = StringUtils.trimTitle(metadata.getAlbum());
            if(!StringUtils.isEmpty(album)) {
                if(!album.equalsIgnoreCase(albumArtist)) {
                    filename.append(StringUtils.formatTitle(album)).append(File.separator);
                }
            }

            // file name
            String title = StringUtils.trimTitle(metadata.getTitle());
            if(!metadata.isCueSheet()) {
                // track
                boolean hasTrackOrArtist = false;
                String track = StringUtils.trimToEmpty(metadata.getTrack());
                if (!StringUtils.isEmpty(track)) {
                    int indx = track.indexOf("/");
                    if (indx > 0) {
                        filename.append(StringUtils.trimToEmpty(track.substring(0, indx)));
                    } else {
                        filename.append(StringUtils.trimToEmpty(track));
                    }
                    //  filename.append(" - ");
                    hasTrackOrArtist = true;
                }

                // artist, if albumartist and arttist != albumartist
                if(!hasTrackOrArtist) {
                    if ((!StringUtils.isEmpty(artist)) && !artist.equalsIgnoreCase(albumArtist)) {
                        // add artist to file name only have albumArtist
                        filename.append(StringUtils.formatTitle(artist));
                        //  filename.append(" - ");
                        hasTrackOrArtist = true;
                    }
                }

                // artist
                if (hasTrackOrArtist) {
                    filename.append(" - ");
                }

                // /Music/[DSD|SACD|MQA|PCM Hi-Res|PCM SD|MPEG]/[artist|album artist]/album/[track|artist] -

                // title
                if (!StringUtils.isEmpty(title)) {
                    filename.append(StringUtils.formatTitle(title));
                } else {
                    filename.append(StringUtils.formatTitle(FileUtils.removeExtension(metadata.getPath())));
                }

                // /Music/[DSD|SACD|MQA|PCM Hi-Res|PCM SD|MPEG]/[artist|album artist]/album/[track|artist] - [title| file name]
            }else {
                // title
                if (!StringUtils.isEmpty(album)) {
                    filename.append(StringUtils.formatTitle(album));
                } else {
                    filename.append(StringUtils.formatTitle(FileUtils.removeExtension(metadata.getPath())));
                }
            }

            String newPath =  filename.toString();
            for(int i=0;i<ReservedChars.length();i++) {
                newPath = newPath.replace(String.valueOf(ReservedChars.charAt(i)),"");
            }

            newPath = newPath+"."+ext;
            return DocumentFileCompat.buildAbsolutePath(getContext(), storageId, newPath);

            //  return newPath;
        } catch (Exception e) {
            Timber.e(e);
        }
        return metadata.getPath();
    }

    private String getStorageIdFor(AudioTag metadata) {
        // Thai, Laos, World
        // English, Favorite, Lounge, Classical
       // if("Audiophile".equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }
       // if("Jazz".equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }
       // if("Favorite".equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }
        //if("Classical".equalsIgnoreCase(metadata.getGrouping())) {
        //    return STORAGE_SECONDARY;
       // }
        if("Thai".equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
        }
        if("Laos".equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
        }
       // if("Classical".equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }
        //if("English".equalsIgnoreCase(metadata.getGrouping())) {
        //    return STORAGE_SECONDARY;
        //}
        if("World".equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
        }
        return STORAGE_SECONDARY;
    }

    private String getAlbumArtistOrArtist(String artist, String albumArtist) {
        if(StringUtils.isEmpty(albumArtist)) {
            if((!StringUtils.isEmpty(artist)) && artist.indexOf(Constants.FIELD_SEP)>0) {
                albumArtist = artist.substring(0, artist.indexOf(Constants.FIELD_SEP));
            } else {
                albumArtist = artist;
            }
        }

        return StringUtils.trimToEmpty(albumArtist);
    }

    private String getMainArtist(String artist, String albumArtist) {
        if(!StringUtils.isEmpty(artist)) {
            if (artist.indexOf(Constants.FIELD_SEP)>0) {
                artist = artist.substring(0, artist.indexOf(Constants.FIELD_SEP));
            }
        }

        return StringUtils.trimToEmpty(artist);
    }

    public static boolean isMediaFileExist(AudioTag item) {
        if(item == null || item.getPath()==null) {
            return false;
        }
        return isMediaFileExist(item.getPath());
    }

    public static boolean isMediaFileExist(String path) {
        if(StringUtils.isEmpty(path)) {
            return false;
        }
        if(path.indexOf("#") >0) {
            path = path.substring(0, path.indexOf("#"));
        }

        File file = new File(path);
        if(file.exists() && file.length() == 0) {
            return false;
        }
        return file.exists();
    }

    public boolean saveMediaArtwork(AudioTag item, Artwork artwork) {
        if (item == null || item.getPath() == null || artwork==null) {
            return false;
        }
        try {
           // boolean isCacheMode = false;
            File file = new File(item.getPath());
/*
            if(!MediaFileRepository.isWritable(file)) {
                isCacheMode = true;
                file = fileRepos.safToCache(file);
            } */

            AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            if (tag != null) {
                tag.deleteArtworkField();
                tag.addField(artwork);
                audioFile.commit();
            }
/*
            if(isCacheMode) {
                fileRepos.safFromCache(file, item.getPath());
            }*/
        } catch(Exception ex) {
            Timber.e(ex);
        }
        return true;
    }

    private boolean importAudioTag(AudioTag tag) {
            String newPath = buildCollectionPath(tag);
            if(newPath.equalsIgnoreCase(tag.getPath())) {
                return true;
            }
            if (fileRepos.moveFile(tag.getPath(), newPath)) {
                copyRelatedFiles(tag, newPath);

                File file = new File(tag.getPath());
                cleanMediaDirectory(file.getParentFile());
                tag.setObsoletePath(tag.getPath());
                tag.setPath(newPath);
                tag.setManaged(true);
                tag.setSimpleName(DocumentFileCompat.getBasePath(getContext(), newPath));
                tag.setStorageId(DocumentFileCompat.getStorageId(getContext(), newPath));
                tag.setLastModified(file.lastModified());
                tagRepos.saveTag(tag);
                return true;
            }
        return false;
    }

    private void cleanMediaDirectory(File mediaDir) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
        if(mediaDir==null || (!mediaDir.exists())) return;

        if(mediaDir.isDirectory()) {
           // boolean toClean = true;
            List<File> toDelete = new ArrayList();
            File[] files = mediaDir.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    if(f.isDirectory()) {
                        // if contains folder quit
                        return;
                    }
                    String ext = FileUtils.getExtension(f).toLowerCase();
                    if(Constants.RELATED_FILE_TYPES.contains(ext)) {
                        toDelete.add(f);
                    }else {
                       // toClean = false;
                        // if contains music or any others files quit
                        return;
                    }
                }
            }
          //  if(toClean) {
                // directory is empty or no others media files
                if(toDelete.size()>0) {
                    for (File file: toDelete) {
                        fileRepos.delete(file);
                    }
                }
                // trying delete parent folder
                File parentFolder = mediaDir.getParentFile();
                fileRepos.delete(mediaDir);
                cleanMediaDirectory(parentFolder);
            }
       // }
     }

    private void copyRelatedFiles(AudioTag item, String newPath) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
        File oldFile = new File(item.getPath());
        File oldDir = oldFile.getParentFile();
        File newFile = new File(newPath);
        File newDir = newFile.getParentFile();

       // if(getEmbedArtworkAsStream(item) == null) {
            // if not embed artwork, copy folder images
            /*
            for (String cv : Constants.IMAGE_COVERS) {
                File cvFile = new File(oldDir, cv);
                if(cvFile.exists()) {
                    fileRepos.copyFile(cvFile, new File(newDir, cv));
                }
            } */
        //}
        File [] files = oldDir.listFiles();
        for(File f : files) {
            if(Constants.RELATED_FILE_TYPES.contains(FileUtils.getExtension(f).toLowerCase())) {
                File newRelated = new File(newDir, f.getName());
                fileRepos.copyFile(f, newRelated);
            }
        }

       // fileRepos.copyFile(getRelatedFilename(oldFile, Constants.RELATED_CUE_FILE), getRelatedFilename(newFile,Constants.RELATED_CUE_FILE));
       // fileRepos.copyFile(getRelatedFilename(oldFile, Constants.RELATED_LYRIC_FILE), getRelatedFilename(newFile,Constants.RELATED_LYRIC_FILE));
    }

    private String getRelatedFilename(File oldFile, String relatedFileExt) {
        String fname = oldFile.getAbsolutePath();
        fname = fname.substring(0, fname.lastIndexOf("."));
        return fname +relatedFileExt;
    }

    /*
    @Deprecated
    public boolean scanFromMediaSources(boolean fullScan) {
      //  MediaScanner scanner = new MediaScanner();
        if(scanning) return false;

        scanning = true;
       // if(fullScan) {
            // clean all data and cache
       //     MusicMateDatabase.getDatabase(getContext()).clearTables();
       // }

        List<File> sourcePath = new ArrayList<>();
        Collection<MediaFileRepository.StorageInfo> infos = fileRepos.getStorageInfos().values();
        for (MediaFileRepository.StorageInfo info: infos) {
            sourcePath.add(new File(info.path, "Music"));
            sourcePath.add(new File(info.path, "Download"));
            sourcePath.add(new File(info.path, "IDMP"));
            sourcePath.add(new File(info.path, "Android/data/com.apple.android.music/files"));

        }

        //List<File> mediasFiles = scanner.scan(sourcePath);
        AppExecutors executors = new AppExecutors();
        executors.networkIO().execute(new Runnable() {
            @Override
            public void run() {
                List<Object> mediasFiles = fileRepos.scan(sourcePath);

                for (Object obj: mediasFiles) {
                    if(obj instanceof File) {
                        File file = (File)obj;
                        String mediaPath = file.getAbsolutePath();
                        if (fullScan || tagRepos.isMediaOutdated(mediaPath, file.lastModified())) {
                            fileRepos.readMediaFiles(() -> {
                                AudioTag[] metadataList = readMediaTag(mediaPath);
                                if (metadataList != null && metadataList.length > 0) {
                                    for (AudioTag mdata : metadataList) {
                                        String shouldBePath = buildMatePath(mdata);
                                        mdata.setManaged(StringUtils.compare(shouldBePath, mdata.getPath()));
                                        tagRepos.insertMedia(mdata);
                                    }
                                }
                            });
                        }
                    }
            }
        }
        });

        scanning = false;

        return true;
    } */
    public boolean importAudioFile(AudioTag item) {
        boolean status = false;
        try {
            //MusicListeningService.getInstance().playNextSongOnMatched(item);
            BroadcastHelper.playNextSongOnMatched(getContext(), item);
            status = importAudioTag(item);
        }catch(Exception|OutOfMemoryError ex) {
            Timber.e(ex);
            status = false;
        }

        return status;
    }

    public boolean deleteMediaItem(AudioTag item) {
        boolean status = false;
        try {
            //MusicListeningService.getInstance().playNextSongOnMatched(item);
            BroadcastHelper.playNextSongOnMatched(getContext(), item);
            status = com.anggrayudi.storage.file.FileUtils.forceDelete(new File(item.getPath()));
            if(status) {
                tagRepos.removeTag(item);
                File file = new File(item.getPath());
                cleanMediaDirectory(file.getParentFile());
            }
        } catch (Exception|OutOfMemoryError ex) {
            status = false;
        }
		return status;
    }

    public boolean saveAudioFile(AudioTag item, String artworkPath) {
        boolean status = false;
        try {
            Artwork artwork = null;
            if(!StringUtils.isEmpty(artworkPath)) {
                File artworkFile = new File(artworkPath);
                if(artworkFile.exists()) {
                    try {
                        artwork = MusicMateArtwork.createArtworkFromFile(artworkFile);
                    } catch (IOException e) {
                        Timber.e(e);
                    }
                }
            }
            // call new API
            saveTag(item);
            //readMetadata(item.getMetadata());
            saveMediaArtwork(item, artwork);
            status = true;
        }catch (Exception|OutOfMemoryError ex) {
            status = false;
        }

        return status;
    }

    protected AudioTag[] readMediaTag(String mediaPath) {
            AudioTag metadata = new AudioTag();
            metadata.setPath(mediaPath);
            metadata.setFileExtension(FileUtils.getExtension(mediaPath).toUpperCase());
            metadata.setSimpleName(DocumentFileCompat.getBasePath(getContext(), mediaPath));
            metadata.setStorageId(DocumentFileCompat.getStorageId(getContext(), mediaPath));
            return readMetadata(metadata);
    }

    public void reloadMediaItem(AudioTag tag) {
        AudioTag[] metList = readMetadata(tag);
        if(metList!=null && metList.length>1) {
            for (AudioTag rmet:metList) {
                if(tag.getId() == rmet.getId()) {
                    tag.cloneFrom(rmet);
                    break;
                }
            }
        }
    }
}