package apincer.android.mmate.provider;

import static apincer.android.mmate.core.utils.StringUtils.isEmpty;

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

public class FileSystem {
    private static final String TAG = "FileSystem";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
   // private final Context mContext;

    FileSystem() {
        super();
    }

    public static boolean copy(Context context, final String source, final String target) {
        File newFile = new File(target);
        File file = new File(source);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        if(newDir != null && !newDir.exists()) {
            // create new directory
            com.anggrayudi.storage.file.DocumentFileCompat.mkdirs(context, newDir.getAbsolutePath());
            //mkdirs(newDir);
        }
        return copy(context, file, newFile);
    }
    /*
     * file manipulations
     */
    public static boolean copy(Context context, final File source, final File target) {
        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);

            // First try the normal way
          //  try {
           // if(target.canWrite()) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
          /*  }catch(Exception ex) {
                Log.d(TAG, "Trying copy by document file");
           // } else {
                // Storage Access Framework
                DocumentFile targetDoc = DocumentFileCompat.fromFile(context, target);
                outStream = DocumentFileUtils.openOutputStream(targetDoc, context);
                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE]; // MAGIC_NUMBER
                    int bytesRead;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                }else {
                    return false;
                } */
          //  }
        } catch (Exception e) {
            Log.e(TAG,"Error when copying file from " + source.getAbsolutePath() + " to " + target.getAbsolutePath());
            return false;
        } finally {
            closeSilently(inStream);
            closeSilently(outStream);
            closeSilently(inChannel);
            closeSilently(outChannel);
        }

        //check file size
        if(!isValidSize(source.getAbsolutePath(), target.getAbsolutePath(), true)) {
            delete(target.getAbsolutePath());
            return false;
        }

        return true;
    }

    /*
    Fastest copy methods
     */
    public static void copy(File source, File dest) throws IOException {
        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    @Deprecated
    public static String getFilename(String path) {
        if(isEmpty(path)) {
            return "";
        }
        File file = new File(path);
        return file.getName();
    }

    public static boolean safeMove(Context context, String srcPath, String targetPath) {
        String tmpPath = targetPath+"_tmp_safe_move";
        String bakPath = targetPath+"_tmp_safe_backup";
        if(copy(context, srcPath, tmpPath)) {
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
