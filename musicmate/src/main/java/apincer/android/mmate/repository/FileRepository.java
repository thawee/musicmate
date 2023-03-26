package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.FFMPeg.writeTrackFields;
import static apincer.android.mmate.utils.StringUtils.formatTitle;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

/**
 * Wrapper class for accessing media information via media store and jaudiotagger
 * Created by e1022387 on 5/10/2017.
 */
public class FileRepository {
    private static final String TAG = JustFLACReader.class.getName();
    private final Context context;
    private final String STORAGE_PRIMARY = StorageId.PRIMARY;
    private final String STORAGE_SECONDARY;

    public static FileRepository newInstance(Context application) {
        return new FileRepository(application);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
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
            Log.e(TAG, "extractCoverArt",e);
        }
    }
    public static void extractCoverArt(String path, File pathFile) {
        try {
            File dir = pathFile.getParentFile();
            dir.mkdirs();
            File coverArtFile = getFolderCoverArt(path);
            if(coverArtFile!=null) {
                // check directory images
                FileSystem.copy(coverArtFile, pathFile);
            }else {
                // extract from media file
                //TODO: extarch by justflac for flac
                FFMPeg.extractCoverArt(path, pathFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "extractCoverArt",e);
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

    public static File getFolderCoverArt(MusicTag mediaItem) {
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

    public static File getFolderCoverArt(String path) {
        // try loading from folder
        // front.png, front.jpg
        // cover.png, cover.jpg

        File mediaFile = new File(path);
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

    /*
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
    } */

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
            Log.e(TAG, "findMediaItem",e);
        }
        return null;
    }

    private double getSimilarScore(double titleScore, double artistScore, double albumScore) {
        return (titleScore*60)+(artistScore*20)+(albumScore*20);
    }

    public boolean setMusicTag(MusicTag item) throws Exception{
        if (item == null || item.getPath() == null) {
            return false;
        }

        if(item.getOriginTag()==null) {
            return false;
        }

        item.setMusicManaged(StringUtils.compare(item.getPath(),buildCollectionPath(item, true)));

        if(FFMPeg.isSupportedFileFormat(item.getPath())) {
            writeTrackFields(getContext(), item);
            item.setOriginTag(null); // reset pending tag
            MusicTagRepository.saveTag(item);
            return true;
        }else if (JustDSDReader.isSupportedFileFormat(item.getPath())) {
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

    public void scanMusicFile(File file, boolean forceRead) {
        try {
            String mediaPath = file.getAbsolutePath();
            long lastModified = file.lastModified();
            if(forceRead) {
                lastModified = System.currentTimeMillis()+2000;
            }
            TagReader reader = TagReader.getReader(mediaPath);
            // if timestamp is outdated
            if(MusicTagRepository.cleanOutdatedMusicTag(mediaPath, lastModified)) {
                Log.d(TAG, "Reading -> "+mediaPath);
                List<MusicTag> tags = reader.readMusicTag(getContext(), mediaPath);
                if (tags != null ) {
                    for (MusicTag tag : tags) {
                        String matePath = buildCollectionPath(tag);
                        tag.setMusicManaged(StringUtils.equals(matePath, tag.getPath()));
                        MusicTagRepository.saveTag(tag);
                    }
                }
            }
        }catch (Exception ex) {
            Log.e(TAG, "scanMusicFile",ex);
        }
    }

    private String buildCollectionPath(MusicTag metadata) {
        return buildCollectionPath(metadata, true);
    }

    public String buildCollectionPath(@NotNull MusicTag metadata, boolean includeStorageDir) {
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
            if(includeStorageDir) {
                return DocumentFileCompat.buildAbsolutePath(getContext(), storageId, newPath);
            }else {
                return newPath;
            }

            //  return newPath;
        } catch (Exception e) {
            Log.e(TAG, "buildCollectionPath",e);
        }
        return metadata.getPath();
    }

    public String getStorageIdFor(MusicTag metadata) {
        if(metadata.isDSD() || metadata.isSACDISO()) {
            // DSD and ISO SACD
            return STORAGE_PRIMARY;
        //}else if(Constants.QUALITY_AUDIOPHILE.equals(metadata.getMediaQuality())) {
            // Audiophile
        //    return STORAGE_PRIMARY;
       // }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48) {
        //}else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48 && metadata.getAudioBitsDepth() > 16) {
            // Lossless Hi-Res
        //    return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
            // lounge
            return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_LIVE.equalsIgnoreCase(metadata.getGrouping())) {
            // lounge
            return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping())) {
                // Acoustic
                return STORAGE_PRIMARY;
            }
        return STORAGE_SECONDARY;
    }

    private String getAlbumArtistOrArtist(String artist, String albumArtist) {
        if(StringUtils.isEmpty(albumArtist)) {
            albumArtist = artist;
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
            File cacheCoverArt = MusicTagUtils.getCoverArt(getContext(), tag);
            if(newPath.equalsIgnoreCase(tag.getPath())) {
                return true;
            }
            if (FileSystem.move(getContext(), tag.getPath(), newPath)) {
                copyRelatedFiles(tag, newPath);
                if(cacheCoverArt!=null) {
                    com.anggrayudi.storage.file.FileUtils.forceDelete(cacheCoverArt);
                }

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
            Log.e(TAG, "importAudioFile",ex);
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
                File cacheCoverart = MusicTagUtils.getCoverArt(getContext(), item);
                if(cacheCoverart!=null) {
                    com.anggrayudi.storage.file.FileUtils.forceDelete(cacheCoverart);
                }
                MusicTagRepository.removeTag(item);
                File file = new File(item.getPath());
                cleanMediaDirectory(file.getParentFile());
            }
        } catch (Exception|OutOfMemoryError ex) {
            status = false;
        }
		return status;
    }
}