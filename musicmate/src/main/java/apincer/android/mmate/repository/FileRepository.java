package apincer.android.mmate.repository;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp4.Mp4AudioHeader;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.wav.WavInfoTag;
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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.fs.MusicMateArtwork;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mqaidentifier.NativeLib;
import apincer.android.utils.FileUtils;
import timber.log.Timber;

/**
 * Wrapper class for accessing media information via media store and jaudiotagger
 * Created by e1022387 on 5/10/2017.
 */
public class FileRepository {
    private final Context context;
    private final String STORAGE_PRIMARY = StorageId.PRIMARY;
    private final String STORAGE_SECONDARY;

    public static FileRepository newInstance(Context application) {
        return new FileRepository(application);
    }

    public Context getContext() {
        return context;
    }

    private FileRepository(Context context) {
        super();
        this.context = context;
        STORAGE_SECONDARY = getSecondaryId(context);
    }

    public static String getSecondaryId(Context context) {
        List<String> sids = DocumentFileCompat.getStorageIds(context);
        for(String sid: sids) {
            if(!sid.equals(StorageId.PRIMARY)) {
                return sid;
            }
        }
        return StorageId.PRIMARY;
    }

    private static InputStream getArtworkAsStream(MusicTag item) {
        InputStream input = getEmbedArtworkAsStream(item);
        if (input == null) {
            input = getFolderArtworkAsStream(item);
        }
        return input;
    }

    private static InputStream getFolderArtworkAsStream(MusicTag mediaItem) {
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

    private static InputStream getEmbedArtworkAsStream(MusicTag mediaItem) {
        try {
            if(isValidJAudioTagger(mediaItem.getPath())) {
                AudioFile audioFile = buildAudioFile(mediaItem.getPath(), "r");

                if (audioFile == null) {
                    return null;
                }
                Artwork artwork = audioFile.getTagOrCreateDefault().getFirstArtwork();
                if (null != artwork) {
                    byte[] artworkData = artwork.getBinaryData();
                    return new ByteArrayInputStream(artworkData);
                }
            }
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static byte[] getArtworkAsByte(MusicTag mediaItem) {
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
                if(isMediaFileExist(path)) {
                    setupTagOptionsForReading();
                    if(mode!=null && mode.contains("w")) {
                        setupTagOptionsForWriting();
                    }
                    return AudioFileIO.read(new File(path));
                }
            } catch (CannotReadException | IOException | TagException |ReadOnlyFileException |InvalidAudioFrameException| BufferUnderflowException e) {
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

    public MusicTag findMediaItem(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<MusicTag> list = MusicTagRepository.findMediaByTitle(currentTitle);

            double prvTitleScore = 0.0;
            double prvArtistScore = 0.0;
            double prvAlbumScore = 0.0;
            double titleScore;
            double artistScore;
            double albumScore;
            MusicTag matchedMeta = null;

            for (MusicTag metadata : list) {
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
        }catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    private double getSimilarScore(double titleScore, double artistScore, double albumScore) {
        return (titleScore*60)+(artistScore*20)+(albumScore*20);
    }

    private static boolean isValidJAudioTagger(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    /*
    public boolean saveArtworkToFile(MusicTag item, String filePath) {
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
    } */

    /*
    private boolean isValidTagValue(String newTag) {
        return !StringUtils.MULTI_VALUES.equalsIgnoreCase(newTag);
    } */

    private String getId3TagValue(Tag id3Tag, FieldKey key) {
        if(id3Tag!=null && id3Tag.hasField(key)) {
            return StringUtils.trimToEmpty(id3Tag.getFirst(key));
        }
        return "";
    }

    /*
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
    } */

    private void setMusicTag(MusicTag item) throws Exception{
        if (item == null || item.getPath() == null) {
            return;
        }

        if(item.getOriginTag()==null) {
            return;
        }

        item.setManaged(StringUtils.compare(item.getPath(),buildCollectionPath(item)));
        if(isValidJAudioTagger(item.getPath())) {
          //  File file = new File(item.getPath());
           // setupTagOptionsForWriting();
           // AudioFile audioFile = buildAudioFile(file.getAbsolutePath(), "rw");

            // save default tags
           // assert audioFile != null;
           // Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
            setJAudioTagger(item);
            item.setOriginTag(null); // reset pending tag
        }else if (isValidSACD(item.getPath())) {
            // write to somewhere else
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String json = gson.toJson(item);
            File f = new File(item.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+item.getTrack()+".json";
            org.apache.commons.io.FileUtils.write(new File(fileName), json);
        }

        MusicTagRepository.saveTag(item);
    }

    private void setJAudioTagger(MusicTag musicTag) {
        if (musicTag==null) {
            return;
        }
        // prepare new tag
        // reset tag
        if(MusicTagUtils.isWavFile(musicTag)) {
            // need to reset tag for wave file
            try {
                setWavComment(musicTag);
                resetWavFile(new File(musicTag.getPath()));
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }
        // write new tag
        try {
            AudioFile audioFile = buildAudioFile(musicTag.getPath(), "rw");
            assert audioFile != null;
            Tag tag = audioFile.getTagOrCreateDefault();
            setCommonTagFields(tag, musicTag);
            setExtendedTagFields(tag, musicTag);
            audioFile.commit();
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }

    private void setWavComment(MusicTag musicTag) {
        String comment = "/#"+StringUtils.trimToEmpty(musicTag.getSource());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getDisc());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getGrouping());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getSourceQuality());
        comment = comment+"#/ "+StringUtils.trimToEmpty(musicTag.getComment());
        musicTag.setComment(comment);
    }

    /*
    private void saveMusicTag(MusicTag item) throws Exception{
        if (item == null || item.getPath() == null) {
            return;
        }

        if(item.getOriginTag()==null) {
            return;
        }

        item.setManaged(StringUtils.compare(item.getPath(),buildCollectionPath(item)));
        if(isValidJAudioTagger(item.getPath())) {
            File file = new File(item.getPath());
            // setupTagOptionsForWriting();
            AudioFile audioFile = buildAudioFile(file.getAbsolutePath(), "rw");

            // save default tags
            assert audioFile != null;
            Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
            writeJAudioTagger2(audioFile, existingTags, item);
            item.setOriginTag(null); // reset pending tag
        }else if (isValidSACD(item.getPath())) {
            // write to somewhere else
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String json = gson.toJson(item);
            File f = new File(item.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+item.getTrack()+".json";
            org.apache.commons.io.FileUtils.write(new File(fileName), json);
        }

        tagRepos.saveTag(item);
    } */

    /*
    private void writeJAudioTagger(AudioFile audioFile, Tag tags, MusicTag pendingMetadata) {
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
        setTagField(audioFile,tags,FieldKey.RATING, String.valueOf(pendingMetadata.getRating()));
        if ("wav".equalsIgnoreCase(pendingMetadata.getFileExtension()) || "dsf".equalsIgnoreCase(pendingMetadata.getFileExtension())) {
            // wave, not support disk no, grouping, media, quality - write to comment
            String comment = "/#"+StringUtils.trimToEmpty(pendingMetadata.getSource());
            comment = comment+"#:"+StringUtils.trimToEmpty(pendingMetadata.getDisc());
            comment = comment+"#:"+StringUtils.trimToEmpty(pendingMetadata.getGrouping());
            comment = comment+"#:"+StringUtils.trimToEmpty(pendingMetadata.getSourceQuality());
            comment = comment+"#/"+StringUtils.trimToEmpty(pendingMetadata.getComment());
            setTagField(audioFile,tags,FieldKey.COMMENT, comment);
            // setTagField(audioFile,tags,FieldKey.MEDIA, pendingMetadata.getSource());
            // setTagField(audioFile,tags,FieldKey.DISC_NO, pendingMetadata.getDisc());
            // setTagField(audioFile,tags,FieldKey.GROUPING, pendingMetadata.getGrouping());
            // setTagField(audioFile,tags,FieldKey.QUALITY, pendingMetadata.isAudiophile()?Constants.AUDIOPHILE:"");
        }else {
            setTagField(audioFile,tags,FieldKey.DISC_NO, pendingMetadata.getDisc());
            setTagField(audioFile,tags,FieldKey.GROUPING, pendingMetadata.getGrouping());
            setTagField(audioFile,tags,FieldKey.MEDIA, pendingMetadata.getSource());
            setTagField(audioFile,tags,FieldKey.QUALITY, pendingMetadata.getSourceQuality());
            //setTagField(audioFile,tags,FieldKey, pendingMetadata.getGrouping());
            setTagField(audioFile,tags,FieldKey.COMMENT, pendingMetadata.getComment());
        }
    } */

    void setCommonTagFields(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        tag.setEncoding(StandardCharsets.UTF_8);
        setTagField(FieldKey.TITLE, musicTag.getTitle(), tag);
        setTagField(FieldKey.ALBUM, musicTag.getAlbum(), tag);
        setTagField(FieldKey.ALBUM_ARTIST, musicTag.getAlbumArtist(), tag);
        setTagField(FieldKey.ARTIST, musicTag.getArtist(), tag);
        setTagField(FieldKey.GENRE, musicTag.getGenre(), tag);
        setTagField(FieldKey.COMMENT, musicTag.getComment(), tag);
        setTagField(FieldKey.GROUPING, musicTag.getGrouping(), tag);
        setTagField(FieldKey.TRACK, musicTag.getTrack(), tag);
        setTagField(FieldKey.DISC_NO, musicTag.getDisc(), tag);
        setTagField(FieldKey.YEAR, musicTag.getYear(), tag);
        setTagField(FieldKey.COMPOSER, musicTag.getComposer(), tag);
        setTagField(FieldKey.RATING, Integer.toString(musicTag.getRating()), tag);
        setTagField(FieldKey.BPM, Integer.toString(musicTag.getBpm()), tag);
        setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isPartOfCompilation()), tag);
    }

    void setExtendedTagFields(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        setTagField(FieldKey.MEDIA, musicTag.getSource(),tag);
        setTagField(FieldKey.QUALITY, musicTag.getSourceQuality(),tag);
    }

    void resetMp3File(File mp3File) throws Exception {
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = new ID3v24Tag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetM4aFile(File m4aFile) throws Exception {
        AudioFile audio = AudioFileIO.read(m4aFile);
        Tag tag = new Mp4Tag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetWavFile(File waveFile) throws Exception {
        AudioFile audio = AudioFileIO.read(waveFile);
        WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
        wavTag.setID3Tag(new ID3v24Tag());
        wavTag.setInfoTag(new WavInfoTag());
        resetCommonTagFields(wavTag);
        audio.setTag(wavTag);
        audio.commit();
    }

    void resetFlacFile(File flacFile) throws Exception {
        AudioFile audio = AudioFileIO.read(flacFile);
        Tag tag = new FlacTag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetCommonTagFields(Tag tag) throws FieldDataInvalidException {
        setTagField(FieldKey.TITLE, "", tag);
        setTagField(FieldKey.ALBUM, "", tag);
        setTagField(FieldKey.ALBUM_ARTIST, "", tag);
        setTagField(FieldKey.ARTIST, "", tag);
        setTagField(FieldKey.GENRE, "", tag);
        setTagField(FieldKey.COMMENT, "", tag);
        setTagField(FieldKey.GROUPING, "", tag);
        setTagField(FieldKey.TRACK, Integer.toString(0), tag);
        setTagField(FieldKey.DISC_NO, Integer.toString(0), tag);
        setTagField(FieldKey.YEAR, Integer.toString(0), tag);
        setTagField(FieldKey.BPM, Integer.toString(0), tag);
        setTagField(FieldKey.RATING, Integer.toString(0), tag);
        setTagField(FieldKey.IS_COMPILATION, Boolean.toString(false), tag);
    }

    void setTagField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        tag.setField(fieldKey, value);
    }

    public void scanMusicFiles(File file) {
        try {
            String mediaPath = file.getAbsolutePath();
            if (isValidSACD(mediaPath)) {
                if (MusicTagRepository.checkSACDOutdated(mediaPath, file.lastModified())) {
                    MusicTag[] tags = readSACD(mediaPath);
                    if (tags == null) return;
                    for (MusicTag metadata : tags) {
                        String matePath = buildCollectionPath(metadata);
                        metadata.setManaged(StringUtils.equals(matePath, metadata.getPath()));
                        MusicTagRepository.saveTag(metadata);
                    }
                }
            } else if (isValidJAudioTagger(mediaPath)) {
                MusicTag tag = MusicTagRepository.getJAudioTaggerOutdated(mediaPath, file.lastModified());
                if (tag != null) {
                    MusicTag metadata = readJAudioTagger(tag, mediaPath);
                    if (metadata != null) {
                        String matePath = buildCollectionPath(metadata);
                        metadata.setManaged(StringUtils.equals(matePath, metadata.getPath()));
                        MusicTagRepository.saveTag(metadata);
                    }
                }
            }
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }

    /*
    @Deprecated
    private AudioTag readMetadata(AudioTag metadata) {
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
                return metadata;
            }
            return null;
        } catch (Exception |OutOfMemoryError oom) {
            Timber.e(oom);
        }
        return null;
    } */

    /*
    @Deprecated
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
    } */

    private MusicTag[] readSACD(String path) {
        if (isValidSACD(path)) {
            try {
                String fileExtension = FileUtils.getExtension(path).toUpperCase();
                String simpleName = DocumentFileCompat.getBasePath(getContext(), path);
                String storageId = DocumentFileCompat.getStorageId(getContext(), path);
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
                MusicTag[] mList;
                Scarletbook.TrackInfo[] tracks = (Scarletbook.TrackInfo[]) dsf.getMetadata("Tracks");
                if (tracks != null && tracks.length > 0) {
                    mList = new MusicTag[tracks.length];
                    for (int t = 0; t < tracks.length; t++) {
                        mList[t] = new MusicTag();
                        mList[t].setPath(path);
                        mList[t].setFileExtension(fileExtension);
                        mList[t].setSimpleName(simpleName);
                        mList[t].setStorageId(storageId);
                        mList[t].setLossless(true);
                        mList[t].setCueSheet(true);
                        mList[t].setAudioBitRate(dsf.getSampleCount());
                        mList[t].setAudioBitsPerSample(1);
                        mList[t].setAudioSampleRate(dsf.getSampleRate());
                        mList[t].setAudioEncoding(Constants.MEDIA_ENC_SACD);
                        mList[t].setLastModified(lastModified);
                        mList[t].setFileSize(length);
                        mList[t].setAlbum(album);
                        mList[t].setGenre(genre);
                        mList[t].setYear(year);
                        mList[t].setTrack(String.format(Locale.US, "%02d", t + 1));

                        mList[t].setTitle(String.format(Locale.US,"%s", Utils.nvl(StringUtils.normalizeName(tracks[t].get("title")), "NA")));
                        if (tracks[t].get("performer") != null) {
                            mList[t].setArtist(String.format(Locale.US,"%s", StringUtils.normalizeName(tracks[t].get("performer"))));
                        }

                        if (dsf.textDuration > 0) {
                            int start = (int) Math.round(dsf.getTimeAdjustment() * tracks[t].start);
                            mList[t].setAudioDuration(start);
                        } else {
                            mList[t].setAudioDuration(tracks[t].start + tracks[t].startFrame);
                        }
                        readJSON(mList[t]);
                    }
                } else {
                    mList = new MusicTag[1];
                    MusicTag metadata = new MusicTag();
                    readFileHeader(metadata, path);
                    metadata.setLossless(true);
                    metadata.setCueSheet(false);
                    metadata.setAlbum(album);
                    metadata.setTitle(album);
                    metadata.setGenre(genre);
                    metadata.setYear(year);
                    metadata.setAudioBitRate(dsf.getSampleCount());
                    metadata.setAudioBitsPerSample(1);
                    metadata.setAudioSampleRate(dsf.getSampleRate());
                    metadata.setAudioEncoding(Constants.MEDIA_ENC_SACD);
                    metadata.setLastModified(lastModified);
                    metadata.setFileSize(length);
                    metadata.setAudioDuration((dsf.atoc.minutes * 60) + dsf.atoc.seconds);
                    metadata.setTrack(String.format(Locale.US,"%02d", 1));
                    readJSON(metadata);
                    mList[0] = metadata;
                }
                dsf.close();

                return mList;

            } catch(Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }

    private void readJSON(MusicTag metadata) {
        try {
            File f = new File(metadata.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+metadata.getTrack()+".json";
            f = new File(fileName);
            if(f.exists()) {
                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                MusicTag tag = gson.fromJson(new FileReader(fileName), MusicTag.class);
                metadata.setSourceQuality(tag.getSourceQuality());
                metadata.setTitle(tag.getTitle());
                metadata.setArtist(tag.getArtist());
                metadata.setAlbum(tag.getAlbum());
                metadata.setAlbumArtist(tag.getAlbumArtist());
                metadata.setGenre(tag.getGenre());
                metadata.setGrouping(tag.getGrouping());
                metadata.setSource(tag.getSource());
                metadata.setRating(tag.getRating());
                metadata.setComposer(tag.getComposer());
                metadata.setYear(tag.getYear());
                metadata.setComment(tag.getComment());
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private boolean isValidSACD(String path) {
        return path.toLowerCase().endsWith(".iso");
    }

    private MusicTag readJAudioTagger(MusicTag metadata, String path) {
        if(isValidJAudioTagger(path)) {
            File file = new File(path);
            metadata.setLastModified(file.lastModified());
            metadata.setFileSize(file.length());
            readFileHeader(metadata, path);

            AudioFile audioFile = buildAudioFile(path, "r");
            if (audioFile == null) {
                // cannot read file, return as is
                return metadata;
            }
            readJAudioHeader(audioFile, metadata); //16/24/32 bit and 44.1/48/96/192 kHz

            Tag tag = audioFile.getTag();
            if (tag != null && !tag.isEmpty()) {
                String title = getId3TagValue(tag, FieldKey.TITLE);
                if(!StringUtils.isEmpty(title)) {
                    metadata.setTitle(title);
                }
                metadata.setAlbum(getId3TagValue(tag, FieldKey.ALBUM));
                metadata.setArtist(getId3TagValue(tag, FieldKey.ARTIST));
                metadata.setAlbumArtist(getId3TagValue(tag, FieldKey.ALBUM_ARTIST));
                metadata.setGenre(getId3TagValue(tag, FieldKey.GENRE));
                metadata.setYear(getId3TagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getId3TagValue(tag, FieldKey.TRACK));
                metadata.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
                metadata.setRating(toInt(getId3TagValue(tag, FieldKey.RATING)));
                metadata.setBpm(toInt(getId3TagValue(tag, FieldKey.BPM)));
                metadata.setEncoder(getId3TagValue(tag, FieldKey.ENCODER));
                metadata.setPartOfCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));

              //  if ("wav".equalsIgnoreCase(metadata.getFileExtension()) || "dsf".equalsIgnoreCase(metadata.getFileExtension())) {
                if(MusicTagUtils.isWavFile(metadata)) {
                    // wave, not support disk no, grouping, media, quality - write to comment
                    parseWaveCommentTag(metadata, getId3TagValue(tag, FieldKey.COMMENT));
                } else {
                    // WAV file not support these fields
                    metadata.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
                    metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
                    metadata.setSourceQuality(getId3TagValue(tag, FieldKey.QUALITY));
                    metadata.setSource(getId3TagValue(tag, FieldKey.MEDIA));
                    metadata.setComment(getId3TagValue(tag, FieldKey.COMMENT));
                }

                // check MQA Tag
               // detectMQA(metadata);

            }

            return metadata;
        }
        return null;
    }

    private boolean toBoolean(String val) {
        if("1".equals(val)) {
            return true;
        }else {
            return "true".equalsIgnoreCase(val);
        }
    }

    private int toInt(String val) {
        if(StringUtils.isDigitOnly(val)) {
            return Integer.parseInt(val);
        }
        return 0;
    }

    /*
    @Deprecated
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
            mediaTag.setRating(getId3TagIntValue(tag,FieldKey.RATING));

            // check MQA Tag
            detectMQA(mediaTag);
            //}
        }

        if(tag instanceof WavTag) {
            // wave, not support disk no, grouping, media, quality - write to comment
            readWaveCommentTag(mediaTag,getId3TagValue(tag, FieldKey.COMMENT));
        }else {
            // WAV file not support these fields
            mediaTag.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
            mediaTag.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
            mediaTag.setAudiophile(Constants.AUDIOPHILE.equals(getId3TagValue(tag, FieldKey.QUALITY)));
            mediaTag.setSource(getId3TagValue(tag, FieldKey.MEDIA));
            mediaTag.setComment(getId3TagValue(tag, FieldKey.COMMENT));
        }

        return true;
    } */

    private void detectMQA(MusicTag tag) {
        if((!MusicTagUtils.isFlacFile(tag)) || tag.isMqaDeepScan()) return; //prevent re check
        try {
            NativeLib lib = new NativeLib();
            String mqaInfo = StringUtils.trimToEmpty(lib.getMQAInfo(tag.getPath()));
            // MQA Studio|96000
            // MQA|96000
            if (mqaInfo.toLowerCase().contains("mqa")) {
                tag.setMQA(true);
                if (mqaInfo.toLowerCase().contains("studio")) {
                    tag.setMQAStudio(true);
                }
                tag.setMQASampleRate(mqaInfo.substring(mqaInfo.indexOf("|")+1));
            }
            tag.setMqaDeepScan(true);
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }

    public boolean detectLoudness(MusicTag tag) {
        if(tag.isDSD()) return false; // not support DSD
        String lint = StringUtils.trimToEmpty(tag.getLoudnessIntegrated());
        if(!StringUtils.isEmpty(lint)) {
            return false; // recheck if al
        }

        try {
/*
   -i "%a" -af ebur128 -f null --i "%a" -af ebur128 -f null -
  Integrated loudness:
    I:         -19.7 LUFS
    Threshold: -30.6 LUFS

  Loudness range:
    LRA:        13.0 LU
    Threshold: -40.6 LUFS
    LRA low:   -30.0 LUFS
    LRA high:  -17.0 LUFS

  True peak:
    Peak:        0.5 dBFS[Parsed_ebur128_0 @ 0x7b44c68950]

*/
            //String cmd = "-i \""+tag.getPath()+"\" -af ebur128= -f null -";
            String cmd = " -i \""+tag.getPath()+"\" -filter_complex ebur128=peak=true -f null -";
            //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
            // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
            FFmpegSession session = FFmpegKit.execute(cmd);
            String data = getFFmpegOutputData(session);
            String keyword = "Integrated loudness:";

            int startTag = data.lastIndexOf(keyword);
            if(startTag>0) {
                String integrated = data.substring(data.indexOf("I:")+3, data.indexOf("LUFS"));
                String range = data.substring(data.indexOf("LRA:")+5, data.indexOf("LU\n"));
                String peak = data.substring(data.indexOf("Peak:")+6, data.indexOf("dBFS"));
                tag.setLoudnessIntegrated(StringUtils.trimToEmpty(integrated));
                tag.setLoudnessRange(StringUtils.trimToEmpty(range));
                tag.setLoudnessTruePeek(StringUtils.trimToEmpty(peak));
            }
            return true;
        }catch (Exception ex) {
            Timber.e(ex);
        }
        return false;
    }

    private String getFFmpegOutputData(FFmpegSession session) {
        List<Log> logs = session.getLogs();
        StringBuilder buff = new StringBuilder();
        String keyword = "Integrated loudness:";
        String keyword2 = "-70.0 LUFS";
        boolean foundTag = false;
        for(Log log: logs) {
            String msg = StringUtils.trimToEmpty(log.getMessage());
            if(!foundTag) { // finding start keyword
                if (msg.contains(keyword) && !msg.contains(keyword2)) {
                    foundTag = true;
                }
            }
            if(!foundTag) continue;
            buff.append(msg);
        }

        return buff.toString();
    }

    /*
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
    } */

    private void parseWaveCommentTag(MusicTag mediaTag, String comment) {
        comment = StringUtils.trimToEmpty(comment);

        /*
         String comment = "/#"+StringUtils.trimToEmpty(pendingMetadata.getSource());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getDisc());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getGrouping());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.isAudiophile()?Constants.AUDIOPHILE:"");
            comment = comment+"#/"+StringUtils.trimToEmpty(pendingMetadata.getComment());
         */
        int start = comment.indexOf("/#");
        int end = comment.indexOf("#/");
        if(start >= 0 && end > start) {
            // found metadata comment
            String metadata = comment.substring(start+2, end);
            if(comment.length()>(end+2)) {
                comment = comment.substring(end+2);
            }else {
                comment = "";
            }
            String []text = metadata.split("#:");
            if(text.length >=1) {
                // Source
                mediaTag.setSource(StringUtils.trimToEmpty(text[0]));
            }
            if(text.length >=2) {
                // Disc
                mediaTag.setDisc(StringUtils.trimToEmpty(text[1]));
            }
            if(text.length >=3) {
                // Grouping
                mediaTag.setGrouping(StringUtils.trimToEmpty(text[2]));
            }
            if(text.length >=4) {
                // Audiophile
                mediaTag.setSourceQuality(StringUtils.trimToEmpty(text[3]));
            }
        }

        mediaTag.setComment(StringUtils.trimToEmpty(comment));

    }

    private void readJAudioHeader(AudioFile read, MusicTag metadata) {
        try {
            AudioHeader header = read.getAudioHeader();
            long sampleRate = header.getSampleRateAsNumber(); //44100/48000 Hz
            int bitPerSample = header.getBitsPerSample(); //16/24/32
            long bitRate = header.getBitRateAsNumber(); //128/256/320
            metadata.setLossless(header.isLossless());
            //metadata.setFileSize(read.getFile().length());
            metadata.setAudioEncoding(detectAudioEncoding(read,header.isLossless()));
            if (header instanceof MP3AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else if (header instanceof Mp4AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else {
                metadata.setAudioDuration(header.getTrackLength());
            }
            /*
            if(header.getTrackLength()>0) {
                int length = header.getTrackLength();
                metadata.setAudioDuration(length);
            }else {
                // calculate duration
                //duration = filesize in bytes / (samplerate * #of channels * (bitspersample / 8 ))
                long duration = metadata.getFileSize() / (sampleRate+ch) * (bitPerSample);
                metadata.setAudioDuration(duration);
            } */
            metadata.setAudioBitRate(bitRate);
            metadata.setAudioBitsPerSample(bitPerSample);
            metadata.setAudioSampleRate(sampleRate);
            metadata.setAudioChannels(header.getChannels());
            if (header.getChannels().toLowerCase().contains("stereo")) {
                metadata.setAudioChannels("2");
            }
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

    public String buildCollectionPath(@NotNull MusicTag metadata) {
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
            StringBuilder filename = new StringBuilder(musicPath);

            if(!StringUtils.isEmpty(metadata.getGrouping())) {
                filename.append(StringUtils.formatTitle(metadata.getGrouping())).append(File.separator);
            }

            if (metadata.isSACDISO()) {
                //encSuffix="-MQA";
                filename.append(Constants.MEDIA_PATH_SACD);
           // }else if (metadata.isMQA()) {
                //encSuffix="-MQA";
            //    filename.append(Constants.MEDIA_PATH_MQA);
            }else if (MusicTagUtils.isPCMHiRes(metadata)) {
                //encSuffix="-HRA";
                filename.append(Constants.MEDIA_PATH_HRA);
            }else if(MusicTagUtils.isDSD(metadata)) {
                filename.append(Constants.MEDIA_PATH_DSD);
                //filename.append("-");
                filename.append(MusicTagUtils.getDSDSampleRateModulation(metadata));
            //}else if (metadata.isMQA()) {
            //    filename.append(Constants.MEDIA_PATH_MQA);
//            }else if (AudioTagUtils.isPCMHiRes(metadata)) {
  //              filename.append(Constants.MEDIA_PATH_HR);
           // }else if (AudioTagUtils.isPCMLossless(metadata)) {
           //     filename.append(Constants.MEDIA_PATH_HR);
            }else if (Constants.MEDIA_ENC_ALAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_ALAC);
               // filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_FLAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_FLAC);
               // filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_WAVE.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_WAVE);
              //  filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_AIFF.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_AIFF);
               // filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_AAC.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_ACC);
              //  filename.append(encSuffix);
            }else if (Constants.MEDIA_ENC_MP3.equals(metadata.getAudioEncoding())) {
                filename.append(Constants.MEDIA_PATH_MP3);
              //  filename.append(encSuffix);
            }else {
                filename.append(Constants.MEDIA_PATH_OTHER);
              //  filename.append(encSuffix);
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

    public String getStorageIdFor(MusicTag metadata) {
        if(metadata.isDSD() || metadata.isSACDISO()) {
            // DSD and ISO SACD
            return STORAGE_PRIMARY;
        }else if(Constants.QUALITY_AUDIOPHILE.equals(metadata.getSourceQuality())) {
            // Audiophile
            return STORAGE_PRIMARY;
       // }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48) {
        }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48 && metadata.getAudioBitsPerSample() > 16) {
            // Lossless Hi-Res
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

    public static boolean isMediaFileExist(MusicTag item) {
        if(item == null || item.getPath()==null) {
            return false;
        }
        return isMediaFileExist(item.getPath());
    }

    public static boolean isMediaFileExist(String path) {
        if(StringUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        if(file.exists() && file.length() == 0) {
            return false;
        }
        return file.exists();
    }

    public boolean saveMediaArtwork(MusicTag item, Artwork artwork) {
        if (item == null || item.getPath() == null || artwork==null) {
            return false;
        }
        try {
           // boolean isCacheMode = false;
            File file = new File(item.getPath());

            AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");
            assert audioFile != null;
            Tag tag = audioFile.getTagOrCreateDefault();
            if (tag != null) {
                tag.deleteArtworkField();
                tag.addField(artwork);
                audioFile.commit();
            }
        } catch(Exception ex) {
            Timber.e(ex);
        }
        return true;
    }

    private boolean importAudioTag(MusicTag tag) {
            String newPath = buildCollectionPath(tag);
            if(newPath.equalsIgnoreCase(tag.getPath())) {
                return true;
            }
            if (FileSystem.moveFile(getContext(), tag.getPath(), newPath)) {
                copyRelatedFiles(tag, newPath);

                File file = new File(tag.getPath());
                cleanMediaDirectory(file.getParentFile());
               // tag.setObsoletePath(tag.getPath());
                tag.setPath(newPath);
                tag.setManaged(true);
                tag.setSimpleName(DocumentFileCompat.getBasePath(getContext(), newPath));
                tag.setStorageId(DocumentFileCompat.getStorageId(getContext(), newPath));
                tag.setLastModified(file.lastModified());
                MusicTagRepository.saveTag(tag);
                return true;
            }
        return false;
    }

    private void cleanMediaDirectory(File mediaDir) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
        if(mediaDir==null || (!mediaDir.exists())) return;

        if(mediaDir.isDirectory()) {
           // boolean toClean = true;
            List<File> toDelete = new ArrayList<>();
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
                        FileSystem.delete(getContext(), file);
                    }
                }
                // trying delete parent folder
                File parentFolder = mediaDir.getParentFile();
                FileSystem.delete(getContext(), mediaDir);
                cleanMediaDirectory(parentFolder);
            }
       // }
     }

    private void copyRelatedFiles(MusicTag item, String newPath) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
        File oldFile = new File(item.getPath());
        File oldDir = oldFile.getParentFile();
        File newFile = new File(newPath);
        File newDir = newFile.getParentFile();

        assert oldDir != null;
        File [] files = oldDir.listFiles(file -> Constants.RELATED_FILE_TYPES.contains(FileUtils.getExtension(file).toLowerCase()));
        assert files != null;
        for(File f : files) {
            File newRelated = new File(newDir, f.getName());
            FileSystem.copyFile(getContext(), f, newRelated);
        }
    }

    public boolean importAudioFile(MusicTag item) {
        boolean status;
        try {
            BroadcastHelper.playNextSongOnMatched(getContext(), item);
            status = importAudioTag(item);
        }catch(Exception|OutOfMemoryError ex) {
            Timber.e(ex);
            status = false;
        }

        return status;
    }

    public boolean deleteMediaItem(MusicTag item) {
        boolean status;
        try {
            BroadcastHelper.playNextSongOnMatched(getContext(), item);
            status = com.anggrayudi.storage.file.FileUtils.forceDelete(new File(item.getPath()));
            if(status) {
                MusicTagRepository.removeTag(item);
                File file = new File(item.getPath());
                cleanMediaDirectory(file.getParentFile());
            }
        } catch (Exception|OutOfMemoryError ex) {
            status = false;
        }
		return status;
    }

    public boolean setMusicTag(MusicTag item, String artworkPath) {
        boolean status;
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
            setMusicTag(item);
            saveMediaArtwork(item, artwork);
            status = true;
        }catch (Exception|OutOfMemoryError ex) {
            status = false;
        }

        return status;
    }

    private void readFileHeader(MusicTag metadata, String mediaPath) {
        metadata.setPath(mediaPath);
        metadata.setTitle(FileUtils.getFileName(mediaPath));
        metadata.setFileExtension(FileUtils.getExtension(mediaPath).toUpperCase());
        metadata.setAudioEncoding(metadata.getFileExtension());
        metadata.setSimpleName(DocumentFileCompat.getBasePath(getContext(), mediaPath));
        metadata.setStorageId(DocumentFileCompat.getStorageId(getContext(), mediaPath));
    }

/*
    public void readAudioTagFromFile(MusicTag tag) {
        // re-load from file
        if(isValidJAudioTagger(tag.getPath())) {
            // ISO SACD is read only, no need to re load
            long id = tag.getId();
            MusicTag newTag = readJAudioTagger(tag, tag.getPath());
            tag.cloneFrom(newTag);
            tag.setId(id);
        }
    } */

    public void setJAudioTagger(String targetPath, MusicTag tag) {
        if (targetPath == null || tag == null) {
            return;
        }

       // File file = new File(targetPath);

       // setupTagOptionsForWriting();
       // AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");

        // save default tags
        //Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
        MusicTag newTag = tag.clone();
        newTag.setPath(targetPath);
        setJAudioTagger(tag);
    }

    public boolean deepScanMediaItem(MusicTag tag) {
        if(tag.isDSD() || tag.isSACDISO()) return false;
        // not support DSD and SACD ISO

        tag = MusicTagRepository.getAudioTagById(tag); // re-read tag from db
        if(detectLoudness(tag)) {
            detectMQA(tag);
            MusicTagRepository.saveTag(tag);
            return true;
        }
        return false;
    }
}