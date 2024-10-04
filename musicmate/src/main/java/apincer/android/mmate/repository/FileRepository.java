package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.FFMpegHelper.writeTagToFile;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.provider.FileSystem;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

/**
 * Wrapper class for accessing media information via media store
 * Created by e1022387 on 5/10/2017.
 */
public class FileRepository {
    private static final String TAG = "FileRepository";
    private final Context context;
    private final String STORAGE_SECONDARY;

    public static FileRepository newInstance(Context application) {
        return new FileRepository(application);
    }

    @Deprecated
    public static void extractCoverArt(String path, File targetFile) {
        try {
           // Log.d(TAG, "extractCoverArt: "+path);
            File dir = targetFile.getParentFile();
            dir.mkdirs();
            File coverArtFile = getFolderCoverArt(path);
            if(coverArtFile!=null) {
                // check directory images
                FileSystem.copy(coverArtFile, targetFile);
            }else {
                // extract from media file
                // if path is folder, extract from first music file in folder
                File pathFile = new File(path);
                if(pathFile.isDirectory()) {
                    // find first music file in path
                    File[] files = pathFile.listFiles();
                    for(File file: files) {
                        if(FFMPegReader.isSupportedFileFormat(file.getAbsolutePath())) {
                            path = file.getAbsolutePath();
                            break;
                        }
                    }
                }
                FFMpegHelper.extractCoverArt(path, targetFile);
            }
        } catch (Exception e) {
            Log.d(TAG, "extractCoverArt",e);
        }
    }

    public void extractCoverArt(MusicTag tag) {
        try {
            String coverFile = CoverArtProvider.COVER_ARTS +tag.getAlbumUniqueKey()+".png";
            File dir =  getContext().getExternalCacheDir();
            File pathFile = new File(dir, coverFile);
            if(!pathFile.exists()) {
                dir = pathFile.getParentFile();
                dir.mkdirs();
                extractCoverArt(tag, pathFile);
            }
        } catch (Exception e) {
            Log.d(TAG,"extractCoverArt:", e);
        }
    }

    public static void extractCoverArt(MusicTag tag, File targetFile) {
        if(tag == null) return;
        try {
            // Log.d(TAG, "extractCoverArt: "+path);
            String path = tag.getPath();
            File dir = targetFile.getParentFile();
            dir.mkdirs();
            File coverArtFile = getFolderCoverArt(path);
            if(coverArtFile!=null) {
                // check directory images
                FileSystem.copy(coverArtFile, targetFile);
               // okio.FileSystem.SYSTEM.copy(coverArtFile, targetFile.toPath());
            }else if(!StringUtils.isEmpty(tag.getCoverartMime())){
                FFMpegHelper.extractCoverArt(path, targetFile);
            }
        } catch (Exception e) {
            Log.d(TAG, "extractCoverArt",e);
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

    @Deprecated
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
        File coverDir = mediaFile;
        if(mediaFile.isFile()) {
            coverDir = mediaFile.getParentFile();
        }

        // get cover file with same name as audio file
        String ext = FileUtils.getExtension(mediaFile);
        if(!(isEmpty(ext) && mediaFile.isDirectory())) {
            String artFile = path.replace("." + ext, ".jpg");
            File cover = new File(artFile);
            if (cover.exists()) {
                coverFile = cover;
            } else {
                artFile = path.replace("." + ext, ".png");
                cover = new File(artFile);
                if (cover.exists()) {
                    coverFile = cover;
                }
            }
        }

        // get folder images
        if(coverFile == null) {
            for (String f : Constants.IMAGE_COVERS) {
                File cover = new File(coverDir, f);
                if (cover.exists()) {
                    coverFile = cover;
                    break;
                }
            }
        }
        return coverFile;
    }

    public MusicTag findMediaItem(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<MusicTag> list = TagRepository.findMediaByTitle(currentTitle);

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

        if(FFMPegReader.isSupportedFileFormat(item.getPath())) {
            writeTagToFile(getContext(), item);
            item.setOriginTag(null); // reset pending tag
            TagRepository.saveTag(item);
            return true;
        }else if (JustDSDReader.isSupportedFileFormat(item.getPath())) {
            // write to somewhere else
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String json = gson.toJson(item);
            File f = new File(item.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+item.getTrack()+".json";
            org.apache.commons.io.FileUtils.write(new File(fileName), json, StandardCharsets.UTF_8);
            TagRepository.saveTag(item);
            return true;
        }

        return false;

    }

    public void scanMusicFile(File file, boolean forceRead) {
        try {
            String mediaPath = file.getAbsolutePath();
            long lastModified = file.lastModified();
            if(file.length() < 1024) {
                Log.i(TAG, "scanMusicFile: skip small file - "+mediaPath);
                return; // skip file less than 1 Mb
            }
            if(forceRead) {
               // lastModified = System.currentTimeMillis()+2000;
                lastModified = -1;
            }
            TagReader reader = TagReader.getReader(mediaPath);
            // if timestamp is outdated
            if(TagRepository.cleanOutdatedMusicTag(mediaPath, lastModified)) {
              //  Log.i(TAG, "scanMusicFile: file - "+mediaPath);
                List<MusicTag> tags = reader.readFullMusicTag(getContext(), mediaPath);
                if (tags != null ) {
                    for (MusicTag tag : tags) {
                        String matePath = buildCollectionPath(tag);
                        tag.setMusicManaged(StringUtils.equals(matePath, tag.getPath()));
                        TagRepository.saveTag(tag);

                        // extract cover art
                        extractCoverArt(tag);
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
                filename.append(Constants.MEDIA_PATH_SACD);
            }else if (MusicTagUtils.isMQA(metadata)) {
                filename.append(Constants.MEDIA_PATH_MQA);
            }else if (MusicTagUtils.isHiRes(metadata)) {
                filename.append(Constants.MEDIA_PATH_HRA);
            }else if(MusicTagUtils.isDSD(metadata)) {
                filename.append(Constants.MEDIA_PATH_DSD);
            }else if(MusicTagUtils.isLossless(metadata)) {
                filename.append(Constants.MEDIA_PATH_HIFI);
            }else {
                filename.append(Constants.MEDIA_PATH_HIGH_QUALITY);
            }
            filename.append(File.separator);

            // publisher if albumArtist is various artist
            // albumArtist
            // then artist
            String artist = StringUtils.trimTitle(MusicTagUtils.getFirstArtist(metadata.getArtist()));
            String albumArtist = StringUtils.trimTitle(metadata.getAlbumArtist());
            boolean addArtist2title = "Various Artists".equals(albumArtist);
            /*if(isEmpty(metadata.getPublisher())) {
                    filename.append(StringUtils.formatTitle(albumArtist)).append(File.separator);
                }else {
                    filename.append(StringUtils.getAbvByUpperCase(metadata.getPublisher())).append(File.separator);
                } */
            //else if(!isEmpty(albumArtist)) {
            if(!isEmpty(albumArtist)) {
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
        String STORAGE_PRIMARY = StorageId.PRIMARY;
        if(metadata.isDSD() || metadata.isSACDISO()) {
            // DSD and ISO SACD
            return STORAGE_PRIMARY;
        }else if (MusicTagUtils.isHiRes(metadata)) {
            return STORAGE_PRIMARY;
       // }else if (!MusicTagUtils.isLossless(metadata)) {
            // compress id keep on
       //     return STORAGE_SECONDARY;
        //}else if(Constants.QUALITY_AUDIOPHILE.equals(metadata.getMediaQuality())) {
            // Audiophile
        //    return STORAGE_PRIMARY;
       // }else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48) {
        //}else if(metadata.getAudioSampleRate() > Constants.QUALITY_SAMPLING_RATE_48 && metadata.getAudioBitsDepth() > 16) {
            // Lossless Hi-Res
        //    return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping()) ||
                 Constants.GROUPING_THAI_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
            // lounge
            return STORAGE_PRIMARY;
       // }else if(Constants.GROUPING_LIVE.equalsIgnoreCase(metadata.getGrouping()) ||
      //           Constants.GROUPING_THAI_LIVE.equalsIgnoreCase(metadata.getGrouping())) {
            // lounge
          //  return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping()) ||
                Constants.GROUPING_THAI_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping())) {
                // Acoustic
                return STORAGE_PRIMARY;
        }
        return STORAGE_SECONDARY;
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
                cleanCacheCover(getContext(), tag);

                File file = new File(tag.getPath());
                cleanMediaDirectory(file.getParentFile());
                tag.setPath(newPath);
                tag.setMusicManaged(true);
                tag.setSimpleName(DocumentFileCompat.getBasePath(getContext(), newPath));
                tag.setStorageId(DocumentFileCompat.getStorageId(getContext(), newPath));
                tag.setFileLastModified(file.lastModified());
                TagRepository.saveTag(tag);
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
            if (files != null) {
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

            // directory is empty or no others media files
            if(!toDelete.isEmpty()) {
                    for (File file: toDelete) {
                        FileSystem.delete(getContext(), file);
                    }
            }

            // trying delete parent folder
            File parentFolder = mediaDir.getParentFile();
            FileSystem.delete(getContext(), mediaDir);
            cleanMediaDirectory(parentFolder);
        }
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
            MusixMateApp.getPlayerControl().playNextSongOnMatched(getContext(), item);
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
            MusixMateApp.getPlayerControl().playNextSongOnMatched(getContext(), item);
            status = com.anggrayudi.storage.file.FileUtils.forceDelete(new File(item.getPath()));
            if(status) {
                cleanCacheCover(getContext(), item);
                TagRepository.removeTag(item);
                File file = new File(item.getPath());
                cleanMediaDirectory(file.getParentFile());
            }
        } catch (Exception|OutOfMemoryError ex) {
            status = false;
        }
        return status;
    }

    public void cleanCacheCover(Context context, MusicTag item) {
        String coverFile = CoverArtProvider.COVER_ARTS +item.getAlbumUniqueKey()+".png";
        File dir =  getContext().getExternalCacheDir();
        File pathFile = new File(dir, coverFile);
        if(pathFile.exists()) {
            com.anggrayudi.storage.file.FileUtils.forceDelete(pathFile);
        }
    }

    public List<String> getDefaultMusicPaths() {
        List<String> storageIds = DocumentFileCompat.getStorageIds(context);
        List<String> files = new ArrayList<>();
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "Music"));
            if (file.exists()) {
                files.add(file.getAbsolutePath());
            }
        }
        return files;
    }
}