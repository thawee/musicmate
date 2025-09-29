package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.Utils.runGcIfNeeded;

import android.content.Context;
import android.util.Log;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.codec.TagReader;
import apincer.android.mmate.codec.TagWriter;
import apincer.android.mmate.provider.FileSystem;
import apincer.android.mmate.codec.FFMpegHelper;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.worker.MusicMateExecutors;
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

    public File extractCoverArt(MusicTag tag) {
        try {
            String coverFile = COVER_ARTS +tag.getAlbumCoverUniqueKey()+".png";
            File dir =  getContext().getExternalCacheDir();
            File pathFile = new File(dir, coverFile);
            if(!pathFile.exists()) {
                FileUtils.createParentDirs(pathFile);
                extractCoverArt(tag, pathFile);
            }
            return pathFile;
        } catch (Exception e) {
            Log.d(TAG,"extractCoverArt:", e);
        }
        return null;
    }

    public static void extractCoverArt(MusicTag tag, File targetFile) {
        if(tag == null) return;
        try {
            // Log.d(TAG, "extractCoverArt: "+path);
            String path = tag.getPath();
            FileUtils.createParentDirs(targetFile);
            //File coverArtFile = getFolderCoverArt(path);
            //if(coverArtFile!=null) {
                // check directory images
            //    FileSystem.copy(coverArtFile, targetFile);
            //}else if(!StringUtils.isEmpty(tag.getCoverartMime())){
            FFMpegHelper.extractCoverArt(path, targetFile);

            // Only run GC if memory usage is high
            runGcIfNeeded();
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

    public static File getCoverArt(MusicTag music) {
        File cover = getFolderCoverArt(music.getPath());
        if(cover == null) {
            String coverFile = COVER_ARTS +music.getAlbumCoverUniqueKey()+".png";
            File dir =  MusixMateApp.getInstance().getExternalCacheDir();
            cover = new File(dir, coverFile);
            if(!cover.exists()) cover = null;
        }
        return cover;
    }

    public static File getCoverArt(String albumCoverUniqueKey) {
            String coverFile = COVER_ARTS +albumCoverUniqueKey+".png";
            File dir =  MusixMateApp.getInstance().getExternalCacheDir();
            File cover = new File(dir, coverFile);
            if(!cover.exists()) {
                // try to to get folder cover art
                MusicTag song = TagRepository.getByAlbumCoverUniqueKey(albumCoverUniqueKey);
                return getFolderCoverArt(song.getPath());
            }
        return cover;
    }

    public static File getFolderCoverArt(String musicPath) {
        // try loading from folder
        // front.png, front.jpg
        // cover.png, cover.jpg

        File mediaFile = new File(musicPath);
        File coverFile = null;
        File coverDir = mediaFile;
        if(mediaFile.isFile()) {
            coverDir = mediaFile.getParentFile();
        }

        // get cover file with same name as audio file
        String ext = FileUtils.getExtension(mediaFile);
        if(!(isEmpty(ext) && mediaFile.isDirectory())) {
            String artFile = musicPath.replace("." + ext, ".jpg");
            File cover = new File(artFile);
            if (cover.exists()) {
                coverFile = cover;
            } else {
                artFile = musicPath.replace("." + ext, ".png");
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

    public boolean setMusicTag(MusicTag item) {
        if (item == null || item.getPath() == null) {
            return false;
        }

        if(item.getOriginTag()==null) {
            return false;
        }

        item.setMusicManaged(MusicTagUtils.isManagedInLibrary(getContext(), item));

        if(TagWriter.isSupportedFileFormat(item.getPath())) {
            TagWriter.writeTagToFile(getContext(), item);
            item.setOriginTag(null); // reset pending tag
            TagRepository.saveTag(item);
            return true;
       /* }else if (JustDSDReader.isSupportedFileFormat(item.getPath())) {
            // write to somewhere else
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String json = gson.toJson(item);
            File f = new File(item.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+item.getTrack()+".json";
            org.apache.commons.io.FileUtils.write(new File(fileName), json, StandardCharsets.UTF_8);
            TagRepository.saveTag(item);
            return true; */
        }

        return false;

    }

    // Modify scanMusicFile to defer cover art extraction
    public void scanMusicFile(File file, boolean forceRead) {
        try {
            String mediaPath = file.getAbsolutePath();
            long lastModified = file.lastModified();
            if(file.length() == 0) {
                Log.i(TAG, "scanFile: skip zero byte file - " + mediaPath);
                return;
            }
            //if(forceRead) {
            //    lastModified = -1;
            //}

            // if timestamp is outdated
           // if(TagRepository.cleanOutdatedMusicTag(mediaPath, lastModified)) {
            List<MusicTag> tags = TagRepository.getByPath(mediaPath);

            forceRead = forceRead || tags == null || tags.isEmpty();

            if(forceRead || TagRepository.isOutdated(tags.get(0), lastModified)) {
                // Read minimal tag data first
                MusicTag basicTag = TagReader.readBasicTag(context, mediaPath);
                if(tags != null && !tags.isEmpty()) {
                    // maintain id
                    basicTag.setId(tags.get(0).getId());
                }

                if(basicTag != null) {
                    // Save basic tag immediately
                    basicTag.setMusicManaged(MusicTagUtils.isManagedInLibrary(getContext(), basicTag));
                    TagRepository.saveTag(basicTag);

                    // Defer cover art extraction completely
                    MusicMateExecutors.lowPriority(() -> saveCoverartToCache(basicTag));
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "scanMusicFile", ex);
        }
    }

    public void saveCoverartToCache(MusicTag basicTag) {
        try {
            File folderCover = getFolderCoverArt(basicTag.getPath());
            if(folderCover == null) {
                extractCoverArt(basicTag);
            }
        } catch(Exception e) {
            Log.e(TAG, "Error extracting cover art", e);
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
            String musicPath = "Music/";
            String storageId = getStorageIdFor(metadata);
            String ext = FileUtils.getExtension(metadata.getPath());
            StringBuilder filename = new StringBuilder(musicPath);

            if (!StringUtils.isEmpty(metadata.getGrouping())) {
                filename.append(StringUtils.formatFilePath(metadata.getGrouping()));
                filename.append(File.separator);
            }

            if (!isEmpty(metadata.getQualityInd())) {
                if(metadata.getQualityInd().startsWith(Constants.MEDIA_ENC_MQA)) {
                    filename.append(Constants.MEDIA_ENC_MQA);
                } else {
                    filename.append(metadata.getQualityInd());
                }
                filename.append(File.separator);
            }

            // publisher if albumArtist is various artist
            // albumArtist
            // then artist
            String artist = StringUtils.formatFilePath(MusicTagUtils.getFirstArtist(metadata.getArtist()));
            String albumArtist = StringUtils.formatFilePath(metadata.getAlbumArtist());
            boolean addArtist2title = "Various Artists".equals(albumArtist);
            if(!isEmpty(albumArtist)) {
                filename.append(albumArtist).append(File.separator);
            }else if (!isEmpty(artist)) {
                filename.append(artist).append(File.separator);
            }

            // album
            String album = StringUtils.trimTitle(metadata.getAlbum());
            if(!StringUtils.isEmpty(album)) {
                if(!album.equalsIgnoreCase(albumArtist)) {
                    filename.append(StringUtils.formatFilePath(album)).append(File.separator);
                }
            }

            // track number
            if(!isEmpty(metadata.getTrack())) {
                filename.append(StringUtils.getWord(metadata.getTrack(),"/",0)).append(" - ");
            } else if(addArtist2title) {
                filename.append(StringUtils.formatFilePath(artist)).append(" - ");
            }

            // title
            String title = StringUtils.trimTitle(metadata.getTitle());
                if (!StringUtils.isEmpty(title)) {
                    filename.append(StringUtils.formatFilePath(title));
                } else {
                    filename.append(StringUtils.formatFilePath(FileUtils.removeExtension(metadata.getPath())));
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
       // if(metadata.isDSD() || metadata.isSACDISO()) {
            // DSD and ISO SACD
       //     return STORAGE_PRIMARY;
       // }else if (MusicTagUtils.isHiRes(metadata)) {
       //     return STORAGE_PRIMARY;
       // }else
        if(Constants.GROUPING_CLASSICAL.equalsIgnoreCase(metadata.getGrouping())) { // ||
            //    Constants.GROUPING_THAI_CLASSICAL.equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
       // }else if(Constants.GROUPING_TRADITIONAL.equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {// ||
             //   Constants.GROUPING_THAI_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;

      /*  }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping()) ||
                 Constants.GROUPING_THAI_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping()) ||
                Constants.GROUPING_THAI_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping())) {
                return STORAGE_PRIMARY; */
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

    private boolean moveMusicFiles(MusicTag tag) {
            String newPath = buildCollectionPath(tag);
            if(newPath.equalsIgnoreCase(tag.getPath())) {
                tag.setMusicManaged(true);
                TagRepository.saveTag(tag);
                return true;
            }
            if (FileSystem.move(getContext(), tag.getPath(), newPath)) {
                copyRelatedFiles(tag, newPath);
                cleanCacheCover(tag);

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
                        FileSystem.delete(file);
                    }
            }

            // trying delete parent folder
            File parentFolder = mediaDir.getParentFile();
            FileSystem.delete(mediaDir);
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
        if(files != null) {
            for (File f : files) {
                File newRelated = new File(newDir, f.getName());
                FileSystem.copy(getContext(), f, newRelated);
            }
        }
    }

    public boolean importAudioFile(MusicTag item) {
        boolean status;
        try {
            status = moveMusicFiles(item);
        }catch(Exception|OutOfMemoryError ex) {
            Log.e(TAG, "importAudioFile",ex);
            status = false;
        }

        return status;
    }

    public boolean deleteMediaItem(MusicTag item) {
        boolean status = false;
        try {
            // more others tag shared same file, skip delete file
            if(TagRepository.getByPath(item.getPath()).size()==1) {
                status = FileUtils.delete(new File(item.getPath()));
                if(!FileUtils.existed(item.getPath())) {
                    cleanCacheCover(item);
                    TagRepository.removeTag(item);
                    File file = new File(item.getPath());
                    cleanMediaDirectory(file.getParentFile());
                    status = true;
                }
            }else {
                // clan database only
                TagRepository.removeTag(item);
                status = true;
            }
        } catch (Exception|OutOfMemoryError ignored) {
        }
        return status;
    }

    private void cleanCacheCover(MusicTag item) {
        String coverFile = COVER_ARTS +item.getAlbumCoverUniqueKey()+".png";
        File dir =  getContext().getExternalCacheDir();
        File pathFile = new File(dir, coverFile);
        if(pathFile.exists()) {
            FileSystem.delete(pathFile);
        }
    }

    public void cleanCacheCovers() {
        File dir =  getContext().getExternalCacheDir();
        File pathFile = new File(dir, COVER_ARTS);
        if(pathFile.exists()) {
            File[] files = pathFile.listFiles();
            if(files != null) {
                for (File f : files) {
                    FileSystem.delete(f);
                }
            }
            FileSystem.delete(pathFile);
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