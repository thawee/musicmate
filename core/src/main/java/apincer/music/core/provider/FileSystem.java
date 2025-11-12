package apincer.music.core.provider;

import android.content.Context;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import apincer.android.utils.FileUtils;
import apincer.music.core.Constants;

public class FileSystem {
    private static final String TAG = "FileSystem";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    FileSystem() {
        super();
    }

    public static boolean copyFile(Context context, final String source, final String target) {
        File newFile = new File(target);
        File file = new File(source);

        if(!file.exists()) return false;

        try {

            // create new directory if not existed
            File newDir  = newFile.getParentFile();
            if(newDir != null && !newDir.exists()) {
                // create new directory
                com.anggrayudi.storage.file.DocumentFileCompat.mkdirs(context, newDir.getAbsolutePath());
                //mkdirs(newDir);
            }

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
                if(rename(context, targetPath, bakPath)) { // backup file
                   // return rename(context, tmpPath, srcPath);
                    if(rename(context, tmpPath, targetPath)) {
                        delete(bakPath);
                        delete(srcPath); // delete source file if everything ok
                        return true;
                    }else {
                        rename(context, bakPath, targetPath);
                    }
                }
            }else {
                delete(tmpPath); // copy is fail, no valid file size
            }
        }
        return false;
    }

    public static void safeMove(Context context, String srcPath, String targetPath, boolean sameDirectory) {
        if(!sameDirectory) {
            safeMove(context, srcPath, targetPath);
            return;
        }

        String bakPath = targetPath+"_tmp_safe_backup";
        if(rename(context, targetPath, bakPath)) {
            if(rename(context, srcPath, targetPath)) {
                delete(bakPath);
                delete(srcPath);
            }else {
                rename(context, bakPath, targetPath); // copy is fail
            }
        }
    }

    public static boolean rename(Context context, String srcPath, String targetPath) {
        File newFile = new File(targetPath);
        File file = new File(srcPath);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        assert newDir != null;
        if(!newDir.exists()) {
            // create new directory
            com.anggrayudi.storage.file.DocumentFileCompat.mkdirs(context, newDir.getAbsolutePath());
            //mkdirs(newDir);
        }

        //Log.i(TAG, "rename path "+file.getAbsolutePath());
        return file.renameTo(newFile);
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
     * Copies a file using the fastest method (NIO FileChannel)
     * and the safest resource management (try-with-resources).
     */
    public static void copyFile(File source, File dest) throws IOException {
        if (source == null || dest == null) {
            throw new NullPointerException("Source or Dest is null");
        }
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + source);
        }
        if (source.getAbsoluteFile().equals(dest.getAbsoluteFile())) {
            return; // Source and destination are the same
        }

        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {

            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    public static void copyRelatedDir(File f, File newRelated) {
        if(newRelated.exists()) {
            newRelated.mkdirs();
        }
        copyRelatedFiles(f, newRelated);
    }

    public static void copyRelatedFiles(File oldFile, File newFile) {
        // move related file, front.jpg, cover.jpg, folder.jpg, *.cue,
       // File oldFile = new File(item.getPath());
        File oldDir = oldFile.getParentFile();
       // File newFile = new File(newPath);
        if(newFile.isFile()) {
            newFile = newFile.getParentFile();
        }
       // File newDir = newFile.getParentFile();

        assert oldDir != null;
        File [] files = oldDir.listFiles(file -> (file.isDirectory() && "cover".equalsIgnoreCase(file.getName()) || Constants.RELATED_FILE_TYPES.contains(FileUtils.getExtension(file).toLowerCase())));
        if(files != null) {
            for (File f : files) {
                File newRelated = new File(newFile, f.getName());
                try {
                    if(f.isDirectory()) {
                        FileSystem.copyRelatedDir(f, newRelated);
                    }else {
                        FileSystem.copyFile(f, newRelated);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
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
        boolean success = false;
        File newFile = new File(newPath);
        File file = new File(path);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        assert newDir != null;
        if(!newDir.exists()) {
            newDir.mkdirs();
            // create new directory
        }

        try {
            Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
          //  System.out.println("Moved file: " + file.getName());
            success = true;
        } catch (IOException e) {
            Log.e(TAG, "move", e);
        }
        return success;
    }

    public static boolean delete(final String file) {
        return delete(new File(file));
    }

    /**
     * Delete a file within the constraints of SAF.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    public static boolean delete(final File file) {
        if (!file.exists()) {
            Log.i(TAG,"cannot delete path "+file.getAbsolutePath());
            return false;
        }

        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "delete", e);
        }

        return false;
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
