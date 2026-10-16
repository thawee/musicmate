package apincer.music.core.provider;

import android.content.Context;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import apincer.android.utils.FileUtils;
import apincer.music.core.Constants;

public class FileSystem {
    private static final String TAG = "FileSystem";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static volatile MusicFileAccess access = new PathMusicFileAccess();

    FileSystem() {
        super();
    }

    /** The access every library change goes through; see {@link MusicFileAccess}. */
    public static MusicFileAccess access() {
        return access;
    }

    public static void setAccess(MusicFileAccess musicFileAccess) {
        access = musicFileAccess;
    }

    /**
     * Installs the Android access once and returns it. The app calls this at start, and so does
     * MusicFileProvider: Android publishes providers before Application.onCreate runs, so another
     * app's request can arrive first and must not fall back to paths.
     */
    public static synchronized AndroidMusicFileAccess installAndroidAccess(Context context) {
        if (access instanceof AndroidMusicFileAccess installed) return installed;
        AndroidMusicFileAccess created = new AndroidMusicFileAccess(context);
        access = created;
        return created;
    }

    public static boolean copyFile(Context context, final String source, final String target) {
        File newFile = new File(target);
        File file = new File(source);

        if(!file.exists()) return false;

        try {

            copyFile(file, newFile);
            return true;
        } catch (IOException e) {
           // throw new RuntimeException(e);
        }
        return false;
    }

    public static boolean safeMove(Context context, String srcPath, String targetPath) {
        String tmpPath = targetPath+"_tmp_safe_move";
        String bakPath = targetPath+"_tmp_safe_backup";
        if(copyFile(context, srcPath, tmpPath)) {
            // check original and tmp file is valid file size
            if (isValidSize(srcPath, tmpPath, true)) {
                // copy file is ok, no lost file content
                File targetFile = new File(targetPath);
                boolean targetBackedUp = false;
                if (targetFile.exists()) {
                    targetBackedUp = rename(context, targetPath, bakPath); // backup file
                }
                if(rename(context, tmpPath, targetPath)) {
                    if (targetBackedUp) {
                        delete(bakPath);
                    }
                    delete(srcPath); // delete source file if everything ok
                    return true;
                } else {
                    if (targetBackedUp) {
                        rename(context, bakPath, targetPath);
                    }
                    delete(tmpPath);
                }
            } else {
                delete(tmpPath); // copy is fail, no valid file size
            }
        }
        return false;
    }

    public static boolean safeMove(Context context, String srcPath, String targetPath, boolean sameDirectory) {
        if(!sameDirectory) {
            return safeMove(context, srcPath, targetPath);
        }

        File targetFile = new File(targetPath);
        boolean targetBackedUp = false;
        String bakPath = targetPath+"_tmp_safe_backup";
        if (targetFile.exists()) {
            targetBackedUp = rename(context, targetPath, bakPath);
        }
        if(rename(context, srcPath, targetPath)) {
            if (targetBackedUp) {
                delete(bakPath);
            }
            return true;
        }
        if (targetBackedUp) {
            rename(context, bakPath, targetPath); // rollback backup
        }
        return false;
    }

    public static boolean rename(Context context, String srcPath, String targetPath) {
        File newFile = new File(targetPath);
        File file = new File(srcPath);

        if(!file.exists()) return false;

        // Replaces an existing target, as File.renameTo does
        return access.move(file, newFile, true);
    }

    private static boolean isValidSize(String srcPath, String targetPath, boolean exactMatch) {
        File newFile = new File(targetPath);
        File file = new File(srcPath);

        if(!file.exists()) return false;
        if(!newFile.exists()) return false;

        if(exactMatch) {
            return file.length() == newFile.length();
        }else {
            // 200 kb diff
            return Math.abs (file.length() - newFile.length()) <= (5*1024); // 5 kbytes
        }
    }

    public static File getCacheDirForFile(Context context, String pathId) {
        File[] files =context.getExternalCacheDirs();
        for(File file: files) {
            if(file.getAbsolutePath().contains(pathId)) {
                return file;
            }
        }
        return context.getExternalCacheDir();
    }

    /**
     * Copies a file through the installed {@link MusicFileAccess}.
     */
    public static void copyFile(File source, File dest) throws IOException {
        if (source == null || dest == null) {
            throw new NullPointerException("Source or Dest is null");
        }
        access.copy(source, dest);
    }


    public static void copyDir(File sourceDir, File targetDir) {
        access.mkdirs(targetDir);
        // Through the access: a path listing hides covers and .cue under scoped storage
        for (File f : access.list(sourceDir)) {
            File newRelated = new File(targetDir, f.getName());
            try {
                if(f.isDirectory()) {
                    FileSystem.copyDir(f, newRelated);
                }else {
                    FileSystem.copyFile(f, newRelated);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
        copy file is parents of source file to parents of target fie
     */
    public static void copyRelatedFiles(File sourceDir, File targetDir) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
        if(sourceDir == null) return;

        if(!sourceDir.isDirectory()) {
            sourceDir = sourceDir.getParentFile();
        }
        if(!targetDir.isDirectory()) {
            targetDir = targetDir.getParentFile();
        }
        access.mkdirs(targetDir);

        // Through the access: a path listing hides covers and .cue under scoped storage
        for (File f : access.list(sourceDir)) {
            if(isRelatedDir(f)) {
                File newRelated = new File(targetDir, f.getName());
                FileSystem.copyDir(f, newRelated);
            }else if(isRelatedFile(f)) {
                File newRelated = new File(targetDir, f.getName());
                try {
                    FileSystem.copyFile(f, newRelated);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static boolean isRelatedFile(File file) {
        if(file.isDirectory()) return false;
        return Constants.RELATED_FILE_TYPES.contains(FileUtils.getExtension(file).toLowerCase());
    }

    private static boolean isRelatedDir(File file) {
        if(!file.isDirectory()) return false;

        String name = file.getName();
        if("cover".equalsIgnoreCase(name)) {
            return true;
        }else if("art works".equalsIgnoreCase(name)) {
            return true;
        }
        return false;
    }

    // Copy an InputStream to a File.
    public void copy(InputStream in, File file) {
        OutputStream out = null;

        try {
            out = new FileOutputStream(file);
            byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
        }
        catch (Exception e) {
            Log.e(TAG,"copy",e);
        }
        finally {
            // Ensure that the InputStreams are closed even if there's an exception.
            closeSilently(out);
            closeSilently(in);
        }
    }

    public static boolean move(Context context, final String path, final String newPath) {
        return access.move(new File(path), new File(newPath), false);
    }

    /**
     * True only when the file is provably gone: its nearest existing folder lists no entry on the way to
     * it. That folder may be a few levels up (an album or artist folder deleted outside the app), but
     * never above the storage volume's root, and only while that volume is mounted: an unmounted SD card
     * or a missing volume is unavailable, not evidence of deletion.
     */
    public static boolean isConfirmedMissingFile(String path) {
        if (path == null || path.isEmpty()) return false;
        return isConfirmedMissingFile(path, volumeRoot(new File(path).getAbsoluteFile()));
    }

    /** As {@link #isConfirmedMissingFile(String)}; with no known volume root only the file's own folder counts. */
    static boolean isConfirmedMissingFile(String path, File volumeRoot) {
        File file = new File(path).getAbsoluteFile();
        if (file.exists()) return false;
        File child = file;
        File folder = file.getParentFile();
        if (volumeRoot != null) {
            if (!volumeRoot.isDirectory()) return false;
            String root = volumeRoot.getAbsolutePath();
            while (folder != null && !folder.exists()) {
                if (!folder.getPath().startsWith(root + File.separator)) return false;
                child = folder;
                folder = folder.getParentFile();
            }
            if (folder == null || !(folder.getPath() + File.separator).startsWith(root + File.separator)) return false;
        }
        if (folder == null || !folder.isDirectory() || !folder.canRead()) return false;
        String[] entries = folder.list();
        if (entries == null) return false;
        for (String entry : entries) {
            if (entry.equals(child.getName())) return false;
        }
        return true;
    }

    /** The storage volume a path is on (/storage/emulated/0, /storage/1234-ABCD), or null for any other path. */
    static File volumeRoot(File file) {
        String[] parts = file.getPath().split("/");
        if (parts.length < 4 || !parts[0].isEmpty() || !"storage".equals(parts[1])) return null;
        if ("emulated".equals(parts[2])) return parts.length > 4 ? new File("/storage/emulated/" + parts[3]) : null;
        return new File("/storage/" + parts[2]);
    }

    /** Moves completed output without replacing any existing recording. */
    public static String moveToAvailablePath(String source, String target) throws IOException {
        File requested = new File(target);
        String name = requested.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int suffix = 0; ; suffix++) {
            File candidate = suffix == 0 ? requested : new File(requested.getParentFile(),
                    base + String.format(java.util.Locale.ROOT, "_%03d", suffix) + extension);
            if (candidate.exists()) continue;
            if (access.move(new File(source), candidate, false)) return candidate.getPath();
            // Another conversion may have claimed this name; try the next suffix.
            if (!candidate.exists()) throw new IOException("Cannot move " + source + " to " + candidate);
        }
    }

    public static boolean delete(final String file) {
        return delete(new File(file));
    }

    /**
     * Delete a file through the installed {@link MusicFileAccess}.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    public static boolean delete(final File file) {
        if (!file.exists()) {
            Log.i(TAG,"cannot delete path "+file.getAbsolutePath());
            return false;
        }
        return access.delete(file);
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException t) {
            Log.w(TAG,"closeSilently");
        }
    }
}
