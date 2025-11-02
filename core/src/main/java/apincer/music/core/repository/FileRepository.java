package apincer.music.core.repository;

import static com.anggrayudi.storage.file.StorageId.PRIMARY;
import static apincer.music.core.Constants.COVER_ARTS;
import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.Constants.IMAGE_COVERS;
import static apincer.music.core.Constants.TITLE_MQA_SHORT;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import apincer.music.core.Constants;
import apincer.music.core.codec.FFMpegHelper;
import apincer.music.core.codec.TagReader;
import apincer.music.core.codec.TagWriter;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.provider.FileSystem;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.TagUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.utils.FileUtils;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Wrapper class for accessing media information via media store
 * Created by e1022387 on 5/10/2017.
 */
@Singleton
public class FileRepository {
    private static final String TAG = "FileRepository";
    private final Context context;
    private final TagRepository tagRepos;

    public static File getCoverArt(Context context, MediaTrack music) {
        File cacheDir =  getCoverartDir(context);
        if(music instanceof MusicFolder folder) {
            SearchCriteria.TYPE type = folder.getType();

            File cover = switch (type) {
                case ARTIST -> {
                    File file = new File(DocumentFileCompat.buildAbsolutePath(context, PRIMARY, "Music"));
                    File dir = findFileAnyCase(music.getTitle(), file.listFiles());
                    File image = null;
                    if(dir != null) {
                        image = getFolderCoverArt(new File(dir, DEFAULT_COVERART));
                    }
                    if(image == null || !image.exists()) {
                        image = new File(cacheDir, music.getPath());
                    }
                    yield image;
                }
                case GENRE, CODEC -> new File(cacheDir, music.getPath());
                default ->
                    // Fallback, but it's better to throw an error
                     new File(cacheDir, music.getPath());
            };

            return getFolderCoverArt(cover);
        }else {
            File cover = getFolderCoverArt(music.getPath());
            if (cover == null && (isEmpty(music.getAlbumArtFilename()) && music.getAlbumArtFilename().contains(DEFAULT_COVERART))) {
                cover = new File(cacheDir, music.getAlbumArtFilename());
                Log.d(TAG, "getCoverArt: no folder image, check " + cover.getAbsolutePath());
            }
            return cover;
        }
    }

   //also save albumArtName
    private String extractEmbedCoverArt(MusicTag tag) {
        try {
            //CacheDir/Covers/HEX.EXT
            //Music/xxx/Cover.EXT
            File dir =  getCoverartDir(getContext());
            String path = tag.getPath();
            String coverartName = tag.getAlbumArtFilename();
           // Log.d(TAG, "extractEmbedCoverArt: from: " + path +", by:  "+coverartName);
            if(isEmpty(coverartName) || DEFAULT_COVERART.equals(coverartName)) {
                if (isManagedInLibrary(tag)) {

                    File pathFile = new File(path);
                    pathFile = pathFile.getParentFile();
                    pathFile = new File(pathFile, "Cover.png");
                   // Log.d(TAG, "extractEmbedCoverArt: from: " + path +", to:  "+pathFile.getAbsolutePath());
                    FFMpegHelper.extractCoverArt(path, pathFile);
                    return DigestUtils.md5Hex(pathFile.getParentFile().getAbsolutePath()); // hex for folder i.e. artist/album
                } else {
                    String coverFilename = DigestUtils.md5Hex(path);
                    File pathFile = new File(dir, coverFilename + ".png");

                    FileUtils.createParentDirs(pathFile);
                   // Log.d(TAG, "extractEmbedCoverArt: from: " + path +", to:  "+pathFile.getAbsolutePath());
                    FFMpegHelper.extractCoverArt(path, pathFile);
                    return pathFile.getName(); // hex for individual file
                }
            }
        } catch (Exception e) {
            Log.d(TAG,"extractCoverArt:", e);
        }
        return DEFAULT_COVERART;
    }

    public boolean isManagedInLibrary(MusicTag tag) {
        String path = buildCollectionPath(tag, true);
        return StringUtils.compare(path, tag.getPath());
    }

    public Context getContext() {
        return context;
    }

    @Inject // Hilt will now know how to create a TagRepository
    public FileRepository(@ApplicationContext Context context, TagRepository tagRepos) {
        super();
        this.context = context;
        this.tagRepos = tagRepos;
        //String STORAGE_SECONDARY = getSecondaryId(context);
    }

    public File getCoverArtByAlbumartFilename(String albumArtFilename) {
            File dir = getCoverartDir(context);
            File cover = new File(dir, albumArtFilename);
            if(!cover.exists()) {
                // try to to get folder cover art
                MusicTag song = tagRepos.getByAlbumArtFilename(albumArtFilename);
                if(song != null) {
                    return getFolderCoverArt(song.getPath());
                }
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

        if(coverFile != null && coverFile.exists()) return coverFile;

        if(coverDir == null) return null;

        File[] files = coverDir.listFiles((file, s) -> IMAGE_COVERS.contains(s.toLowerCase()));

        return (files!=null&&files.length>0)?files[0]:null;
    }

    public static File getCoverartDir(Context context) {
        File coverartDir = context.getCacheDir();
        return new File(coverartDir, COVER_ARTS);
    }

    /**
     * Gets the cover art for a given folder with a specific priority.
     *
     * Priority 1: An image file with the *same name as the folder* (e.g., folder "AlbumName" contains "AlbumName.jpg").
     * Priority 2: Standard cover art files (e.g., "cover.jpg", "front.png").
     *
     * @param file The directory to search in.
     * @return The File object for the cover art, or null if no cover is found.
     */
    public static File getFolderCoverArt(File file) {
        if (file == null) {
            return null; // Not a valid file
        }

        File folder = file.getParentFile();

        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            // --- Priority 1: Check for file matching the folder's name in same directory ---
            String folderName = file.getName();
            //Log.d(TAG, "Check for file matching in same directory: "+folder.getAbsolutePath());
            // Check for folderName.png
            File folderNamePng = findFileAnyCase(folderName + ".png", files);
            if (folderNamePng != null) {
                Log.d(TAG, "found: "+folderNamePng);
                return folderNamePng;
            }

            // Check for folderName.jpg
            File folderNameJpg = findFileAnyCase(folderName + ".jpg", files);
            if (folderNameJpg != null) {
                Log.d(TAG, "found: "+folderNamePng);
                return folderNameJpg;
            }

            // Check for folderName.jpeg as well, just in case
            File folderNameJpeg = findFileAnyCase(folderName + ".jpeg", files);
            if (folderNameJpeg != null) {
                Log.d(TAG, "found: "+folderNamePng);
                return folderNameJpeg;
            }

            // --- Priority 2: Check for standard cover names (e.g., cover.jpg) ---
            //files = file.listFiles();
            for (String priorityName : Constants.IMAGE_COVERS) {
                //Log.d(TAG, "check : "+folder.getAbsolutePath()+" - "+priorityName);
                File coverFile = findFileAnyCase(priorityName, files);
                if (coverFile != null) {
                    Log.d(TAG, "found: "+coverFile.getAbsolutePath());
                    return coverFile; // Found a match
                }
            }
        }

        // 3. If we checked all priorities and found no file
        return null; // Return null, not the original folder
    }

    @Nullable
    private static File findFileAnyCase(String priorityName, @org.jetbrains.annotations.Nullable File[] files) {
        if(files == null) return null;

        priorityName = StringUtils.formatFilePath(priorityName);
       // Log.d(TAG, "findFileAnyCase: base dir - " + priorityName);
        // 2. Check all files in the directory for a case-insensitive match
        for (File file : files) {
            String fileName = file.getName();
            //Log.d(TAG, "findFileAnyCase: compare - " + priorityName +" == "+fileName);
            if (fileName.equalsIgnoreCase(priorityName)) {
                // Found a match! Since we're looping in order of
                // priority, this is the best one we can find.
                return file;
            }
        }
        return null;
    }

    public boolean setMusicTag(MusicTag item) {
        if (item == null || item.getPath() == null) {
            return false;
        }

        if(item.getOriginTag()==null) {
            return false;
        }

        item.setMusicManaged(isManagedInLibrary(item));

        if(TagWriter.isSupportedFileFormat(item.getPath())) {
            TagWriter.writeTagToFile(getContext(), item);
            item.setOriginTag(null); // reset pending tag
            tagRepos.saveTag(item);
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

            List<MusicTag> tags = tagRepos.getByPath(mediaPath);

            forceRead = forceRead || tags == null || tags.isEmpty();

            if(forceRead || tagRepos.isOutdated(tags.get(0), lastModified)) {
                // Read minimal tag data first
                MusicTag basicTag = TagReader.readBasicTag(context, mediaPath);
                if(tags != null && !tags.isEmpty()) {
                    // maintain id
                    basicTag.setId(tags.get(0).getId());
                }

                if(basicTag != null) {
                    // Save basic tag immediately
                    basicTag.setMusicManaged(isManagedInLibrary(basicTag));
                    saveCoverartToCache(basicTag); // must call before savetag, update albumArtName
                    basicTag.setOriginTag(null);
                    tagRepos.saveTag(basicTag);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "scanMusicFile", ex);
        }
    }

    public void saveCoverartToCache(MusicTag basicTag) {
        try {
            File folderCover = getFolderCoverArt(basicTag.getPath());
            if(folderCover != null && folderCover.exists()) {
                //update filename, use folder for hex
                File file = new File(basicTag.getPath());
                String albumArtName = DigestUtils.md5Hex(file.getParentFile().getAbsolutePath());
                String ext = FileUtils.getExtension(file);
                //String ext = FileTypeUtil.getExtensionFromContent(folderCover);
                basicTag.setAlbumArtFilename(albumArtName+"."+ext);
            }else {
                // if no folder album art,
                String albumArtName = extractEmbedCoverArt(basicTag);
                basicTag.setAlbumArtFilename(albumArtName);
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
        // music/album artist/album (sound quality[HR/SQ/LC/DSD/MQA])/track - title.ext
        final String ReservedChars = "?|\\*<\":>[]~#%^@.";
        try {
            String musicPath = "Music/";
            //getStorageIdFor(metadata);
            String ext = FileUtils.getExtension(metadata.getPath());
            StringBuilder filename = new StringBuilder(musicPath);

            // albumArtist
            // then artist
            String firstArtist = StringUtils.formatFilePath(TagUtils.getFirstArtist(metadata.getArtist()));
            String albumArtist = StringUtils.formatFilePath(metadata.getAlbumArtist());
            if(!isEmpty(albumArtist)) {
                filename.append(albumArtist).append(File.separator);
            }else if (!isEmpty(firstArtist)) {
                filename.append(firstArtist).append(File.separator);
            }else {
                filename.append(Constants.UNKNOWN).append(File.separator);
            }

            // album
            String album = StringUtils.trimTitle(metadata.getAlbum());
            String sqInd = trimToEmpty(metadata.getQualityInd());
            if(StringUtils.isEmpty(album)) {
                album = Constants.UNKNOWN;
            }
            if(!StringUtils.isEmpty(sqInd)) {
                if (sqInd.contains(TITLE_MQA_SHORT)) {
                    // use MQA for MQA and MQA Studio
                    sqInd = TITLE_MQA_SHORT;
                }

                if (!Constants.TITLE_HIFI_LOSSLESS_SHORT.equals(sqInd)) {
                    album = album + " (" + sqInd + ")";
                }
            }
            filename.append(StringUtils.formatFilePath(album)).append(File.separator);

            // track number
            if(!isEmpty(metadata.getTrack())) {
                filename.append(StringUtils.getWord(metadata.getTrack(),"/",0)).append(" - ");
            } else if(!isEmpty(firstArtist)) {
                filename.append(StringUtils.formatFilePath(firstArtist)).append(" - ");
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
                return DocumentFileCompat.buildAbsolutePath(getContext(), PRIMARY, newPath);
            }else {
                return newPath;
            }

            //  return newPath;
        } catch (Exception e) {
            Log.e(TAG, "buildCollectionPath",e);
        }
        return metadata.getPath();
    }

    /*
    public String getStorageIdFor(MusicTag metadata) {
        String STORAGE_PRIMARY = PRIMARY;
       // if(metadata.isDSD() || metadata.isSACDISO()) {
            // DSD and ISO SACD
       //     return STORAGE_PRIMARY;
       // }else if (MusicTagUtils.isHiRes(metadata)) {
       //     return STORAGE_PRIMARY;
       // }else
       // if(Constants.GROUPING_CLASSICAL.equalsIgnoreCase(metadata.getGrouping())) { // ||
            //    Constants.GROUPING_THAI_CLASSICAL.equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }else if(Constants.GROUPING_TRADITIONAL.equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY;
       // }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {// ||
             //   Constants.GROUPING_THAI_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
       //     return STORAGE_PRIMARY; */

      /*  }else if(Constants.GROUPING_LOUNGE.equalsIgnoreCase(metadata.getGrouping()) ||
                 Constants.GROUPING_THAI_LOUNGE.equalsIgnoreCase(metadata.getGrouping())) {
            return STORAGE_PRIMARY;
        }else if(Constants.GROUPING_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping()) ||
                Constants.GROUPING_THAI_ACOUSTIC.equalsIgnoreCase(metadata.getGrouping())) {
                return STORAGE_PRIMARY; */
       // }
    /*
        return STORAGE_SECONDARY;
    } */

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
                tagRepos.saveTag(tag);
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
                try {
                    FileSystem.copyFile( f, newRelated);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
            if(tagRepos.getByPath(item.getPath()).size()==1) {
                status = FileUtils.delete(new File(item.getPath()));
                if(!FileUtils.existed(item.getPath())) {
                    cleanCacheCover(item);
                    tagRepos.removeTag(item);
                    File file = new File(item.getPath());
                    cleanMediaDirectory(file.getParentFile());
                    status = true;
                }
            }else {
                // clan database only
                tagRepos.removeTag(item);
                status = true;
            }
        } catch (Exception|OutOfMemoryError ignored) {
        }
        return status;
    }

    private void cleanCacheCover(MusicTag item) {
        String covertName = item.getAlbumArtFilename();
        if(isEmpty(covertName) || covertName.contains(DEFAULT_COVERART)) return;

        File dir =  getCoverartDir(context);
        File pathFile = new File(dir, covertName);
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

    public static List<String> getDefaultMusicPaths(Context context) {
        List<String> storageIds = DocumentFileCompat.getStorageIds(context);
        List<String> files = new ArrayList<>();
        for (String sid : storageIds) {
            // path Music
            File file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "Music"));
            if (file.exists()) {
                files.add(file.getAbsolutePath());
            }

            // path Download
            file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "Download"));
            if (file.exists()) {
                files.add(file.getAbsolutePath());
            }
        }
        return files;
    }
}