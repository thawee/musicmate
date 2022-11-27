package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.FFMPeg.writeTrackFields;
import static apincer.android.mmate.repository.FFMPeg.writeTrackGain;
import static apincer.android.mmate.utils.StringUtils.formatTitle;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;
import org.justcodecs.dsd.DISOFormat;
import org.justcodecs.dsd.Scarletbook;
import org.justcodecs.dsd.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
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

    public static void extractCoverArt(MusicTag tag, File pathFile) {
        try {
            File dir = pathFile.getParentFile();
            dir.mkdirs();
            File coverArtFile = getFolderCoverArt(tag);
            if(coverArtFile!=null) {
                // check directory images
                FileSystem.copy(coverArtFile, pathFile);
            }else {
                // extract from media file
                FFMPeg.extractCoverArt(tag, pathFile);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
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

    private static File getFolderCoverArt(MusicTag mediaItem) {
        // try loading from folder
        // front.png, front.jpg
        // cover.png, cover.jpg

        File mediaFile = new File(mediaItem.getPath());
        File coverFile = null;
        File coverDir = mediaFile.getParentFile();

        for (String f : Constants.IMAGE_COVERS) {
            File cover = new File(coverDir, f);
            if(cover.exists())  {
                coverFile = cover;
                break;
            }
        }
        return coverFile;
    }

    static InputStream getFolderArtworkAsStream(MusicTag mediaItem) {
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

    public boolean setMusicTag(MusicTag item) throws Exception{
        if (item == null || item.getPath() == null) {
            return false;
        }

        if(item.getOriginTag()==null) {
            return false;
        }

        item.setMusicManaged(StringUtils.compare(item.getPath(),buildCollectionPath(item)));
        //if(isValidJAudioTagger(item.getPath())) {
        if(FFMPeg.isSupportedFileFormat(item.getPath())) {
          //  File file = new File(item.getPath());
           // setupTagOptionsForWriting();
           // AudioFile audioFile = buildAudioFile(file.getAbsolutePath(), "rw");

            // save default tags
           // assert audioFile != null;
           // Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
           // setJAudioTagger(item);
            writeTrackFields(getContext(), item);
            item.setOriginTag(null); // reset pending tag
            MusicTagRepository.saveTag(item);
            return true;
        }else if (isValidSACD(item.getPath())) {
            // write to somewhere else
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String json = gson.toJson(item);
            File f = new File(item.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+item.getTrack()+".json";
            org.apache.commons.io.FileUtils.write(new File(fileName), json);
            MusicTagRepository.saveTag(item);
            return true;
        }

        return false;

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

    public void scanMusicFile(File file, boolean forceRead) {
        try {
            String mediaPath = file.getAbsolutePath();
            Timber.d("Scanning::%s", mediaPath);
            long lastModified = file.lastModified();
            if(forceRead) {
                lastModified = System.currentTimeMillis()+2000;
            }
            if (isValidSACD(mediaPath)) {
                if (MusicTagRepository.checkSACDOutdated(mediaPath, lastModified)) {
                    MusicTag[] tags = readSACD(mediaPath);
                    if (tags == null) return;
                    for (MusicTag metadata : tags) {
                        String matePath = buildCollectionPath(metadata);
                        metadata.setMusicManaged(StringUtils.equals(matePath, metadata.getPath()));
                        MusicTagRepository.saveTag(metadata);
                    }
                }
            //} else if (isValidJAudioTagger(mediaPath)) {
            } else if (FFMPeg.isSupportedFileFormat(mediaPath)) {
                MusicTag tag = MusicTagRepository.getOutdatedMusicTag(mediaPath, lastModified);
                if (tag != null) {
                   // MusicTag metadata = readJAudioTagger(tag, mediaPath);
                    MusicTag metadata = FFMPeg.readMusicTag(getContext(), mediaPath);
                    if(metadata==null) {
                        metadata = tag;
                    }
                    if(metadata!=null) {
                        //readFileHeader(file, metadata, mediaPath);
                        metadata.setId(tag.getId());
                        String matePath = buildCollectionPath(metadata);
                        metadata.setMusicManaged(StringUtils.equals(matePath, metadata.getPath()));
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
                        mList[t].setFileFormat(fileExtension);
                        mList[t].setSimpleName(simpleName);
                        mList[t].setStorageId(storageId);
                        mList[t].setLossless(true);
                        mList[t].setAudioStartTime(tracks[t].startFrame);
                        mList[t].setAudioBitRate(dsf.getSampleCount());
                        mList[t].setAudioBitsDepth(1);
                        mList[t].setAudioSampleRate(dsf.getSampleRate());
                        mList[t].setAudioEncoding(Constants.MEDIA_ENC_SACD);
                        mList[t].setFileLastModified(lastModified);
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

                    metadata.setFileFormat(fileExtension);
                    metadata.setSimpleName(simpleName);
                    metadata.setStorageId(storageId);
                    metadata.setFileLastModified(lastModified);
                    metadata.setLossless(true);
                    metadata.setAlbum(album);
                    metadata.setTitle(album);
                    metadata.setGenre(genre);
                    metadata.setYear(year);
                    metadata.setAudioStartTime(0);
                    metadata.setAudioBitRate(dsf.getSampleCount());
                    metadata.setAudioBitsDepth(1);
                    metadata.setAudioSampleRate(dsf.getSampleRate());
                    metadata.setAudioEncoding(Constants.MEDIA_ENC_SACD);
                    metadata.setFileLastModified(lastModified);
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
                metadata.setMediaQuality(tag.getMediaQuality());
                metadata.setTitle(tag.getTitle());
                metadata.setArtist(tag.getArtist());
                metadata.setAlbum(tag.getAlbum());
                metadata.setAlbumArtist(tag.getAlbumArtist());
                metadata.setGenre(tag.getGenre());
                metadata.setGrouping(tag.getGrouping());
                metadata.setMediaType(tag.getMediaType());
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
/*
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
                tag.setMqaSampleRate(toLong(mqaInfo.substring(mqaInfo.indexOf("|")+1)));
            }
            tag.setMqaDeepScan(true);
        }catch (Exception ex) {
            Timber.e(ex);
        }
    } */

    @Deprecated
    public boolean detectLoudness(MusicTag tag) {
        if(tag.isDSD()) return false; // not support DSD
       // if(tag.isTrackScanned()) {
       //     return false; // prevent re scan
      //  }
        FFMPeg.Loudness loudness = FFMPeg.getLoudness(tag.getPath());
        if(loudness!= null) {
            tag.setTrackLoudness(loudness.getIntegratedLoudness());
            tag.setTrackRange(loudness.getLoudnessRange());
            tag.setTrackTruePeek(loudness.getTruePeak());
            return true;
        }
        return false;

//        try {
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
        /*
            //String cmd = "-i \""+tag.getPath()+"\" -af ebur128= -f null -";
            String cmd = " -hide_banner -i \""+tag.getPath()+"\" -filter_complex ebur128=peak=true -f null -";
            //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
            // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
            FFmpegSession session = FFmpegKit.execute(cmd);
            String data = FFMPegUtils.getFFmpegOutputData(session);
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
        return false; */
    }

    /*
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
    }*/

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
            }else if (MusicTagUtils.isMQA(metadata)) {
                //encSuffix="-MQA";
                filename.append(Constants.MEDIA_PATH_MQA);
            }else if (MusicTagUtils.isPCMHiRes(metadata)) {
                //encSuffix="-HRA";
                filename.append(Constants.MEDIA_PATH_HRA);
            }else if(MusicTagUtils.isDSD(metadata)) {
                filename.append(Constants.MEDIA_PATH_DSD);
            }else {
                filename.append(formatTitle(metadata.getFileFormat()));
            }
            filename.append(File.separator);

            // publisher if albumArtist is various artist
            // albumArtist
            // then artist
            String artist = StringUtils.trimTitle(MusicTagUtils.getFirstArtist(metadata.getArtist()));
            String albumArtist = StringUtils.trimTitle(metadata.getAlbumArtist());
            boolean addArtist2title = false;
            if("Various Artists".equals(albumArtist)) {
                if(isEmpty(metadata.getPublisher())) {
                    filename.append(StringUtils.formatTitle(albumArtist)).append(File.separator);
                }else {
                    filename.append(StringUtils.getAbvByUpperCase(metadata.getPublisher())).append(File.separator);
                }
                addArtist2title = true;
            }else if(!isEmpty(albumArtist)) {
                filename.append(StringUtils.formatTitle(albumArtist)).append(File.separator);
            }else if (!isEmpty(artist)) {
                filename.append(StringUtils.formatTitle(artist)).append(File.separator);
            }

            // album
            String album = StringUtils.trimTitle(metadata.getAlbum());
            if(!StringUtils.isEmpty(album)) {
                if(!album.equalsIgnoreCase(albumArtist)) {
                    filename.append(StringUtils.formatTitle(album)).append(File.separator);
                }
            }

            // track number
            if(!isEmpty(metadata.getTrack())) {
                filename.append(StringUtils.getWord(metadata.getTrack(),"/",0)).append(" - ");
            } else if(addArtist2title) {
                filename.append(artist).append(" - ");
            }

            // title
            String title = StringUtils.trimTitle(metadata.getTitle());
                if (!StringUtils.isEmpty(title)) {
                    filename.append(StringUtils.formatTitle(title));
                } else {
                    filename.append(StringUtils.formatTitle(FileUtils.removeExtension(metadata.getPath())));
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
        }else if(Constants.QUALITY_AUDIOPHILE.equals(metadata.getMediaQuality())) {
            // Audiophile
            return STORAGE_PRIMARY;
       // }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48) {
        }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48 && metadata.getAudioBitsDepth() > 16) {
            // Lossless Hi-Res
            return STORAGE_PRIMARY;
        }
        return STORAGE_SECONDARY;
    }

    private String getAlbumArtistOrArtist(String artist, String albumArtist) {
        if(StringUtils.isEmpty(albumArtist)) {
            if(!StringUtils.isEmpty(artist)) {
                albumArtist = artist;
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


    private boolean importAudioTag(MusicTag tag) {
            String newPath = buildCollectionPath(tag);
            if(newPath.equalsIgnoreCase(tag.getPath())) {
                return true;
            }
            if (FileSystem.move(getContext(), tag.getPath(), newPath)) {
                copyRelatedFiles(tag, newPath);

                File file = new File(tag.getPath());
                cleanMediaDirectory(file.getParentFile());
               // tag.setObsoletePath(tag.getPath());
                tag.setPath(newPath);
                tag.setMusicManaged(true);
                tag.setSimpleName(DocumentFileCompat.getBasePath(getContext(), newPath));
                tag.setStorageId(DocumentFileCompat.getStorageId(getContext(), newPath));
                tag.setFileLastModified(file.lastModified());
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
            FileSystem.copy(getContext(), f, newRelated);
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

    @Deprecated
    public boolean deepScanMediaItem(MusicTag tag) {
        if(tag.isDSD() || tag.isSACDISO()) return false;
        // not support DSD and SACD ISO

        tag = MusicTagRepository.getAudioTagById(tag); // re-read tag from db
        if(detectLoudness(tag)) {
            tag.setTrackGain(FFMPeg.getReplayGain(tag));
            //detectMQA(tag);
            MusicTagRepository.saveTag(tag);
            writeTrackGain(getContext(), tag);
            //setTagFieldsReplayGain(tag);
            return true;
        }
        return false;
    }

}