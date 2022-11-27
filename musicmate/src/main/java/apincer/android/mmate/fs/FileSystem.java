package apincer.android.mmate.fs;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import androidx.documentfile.provider.DocumentFile;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.file.StorageId;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import timber.log.Timber;

public class FileSystem {
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
        if(!newDir.exists()) {
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
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);

            // First try the normal way
            try {
           // if(target.canWrite()) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }catch(Exception ex) {
                Timber.d("Trying copy by documentfile");
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
                }
            }
        } catch (Exception e) {
            Timber.e(e,"Error when copying file from " + source.getAbsolutePath() + " to " + target.getAbsolutePath());
            return false;
        } finally {
            closeSilently(inStream);
            closeSilently(outStream);
            closeSilently(inChannel);
            closeSilently(outChannel);
        }

        //check file size
        if(!isValidSize(source.getAbsolutePath(), target.getAbsolutePath(), true)) {
            delete(context, target.getAbsolutePath());
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
                        delete(context, bakPath);
                        delete(context, srcPath); // delete source file if everything ok
                        return true;
                    }else {
                        rename(context, bakPath, targetPath);
                    }
                }
            }else {
                delete(context, tmpPath); // copy is fail, no valid file size
            }
        }
        return false;
    }

    public static boolean safeMove(Context context, String srcPath, String targetPath, boolean sameDirectory) {
        if(!sameDirectory) return safeMove(context, srcPath, targetPath);

        String bakPath = targetPath+"_tmp_safe_backup";
        if(rename(context, targetPath, bakPath)) {
            if(rename(context, srcPath, targetPath)) {
                delete(context, bakPath);
                delete(context, srcPath);
                return true;
            }else {
                rename(context, bakPath, targetPath); // copy is fail
            }
        }
        return false;
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

        // First try the normal deletion.
        if (file.renameTo(newFile)) {
            Timber.i( "rename path %s", file.getAbsolutePath());
            return true;
        }

        return false;

        // Try with Storage Access Framework.
       // Timber.i( "start deleting DocumentFile");
       // DocumentFile docFile = DocumentFileCompat.fromFile(context, file);
       // return DocumentFileUtils.Delete(docFile,context);
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

    /*
    public boolean copy(final DocumentFile source, final File target) {
        InputStream inStream = null;
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = DocumentFileUtils.getInputStream(getContext(), source); //new FileInputStream(source);

            // First try the normal way
            //if (isWritable(target)) {
            if (target.canWrite()) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = DocumentFileUtils.getFileChannel(getContext(), source);  //inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } else {
                // Storage Access Framework
                DocumentFile documentFile = DocumentFileUtils.getDocumentFile(target,false,getContext());
                Uri documentUri = documentFile.getUri();

                // open stream
                ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(documentUri, "w");
                if (pfd != null) {
                    outStream = new FileOutputStream(pfd.getFileDescriptor());
                    if (outStream != null) {
                        // Both for SAF and for Kitkat, write to output stream.
                        byte[] buffer = new byte[16384]; // MAGIC_NUMBER
                        int bytesRead;
                        while ((bytesRead = inStream.read(buffer)) != -1) {
                            outStream.write(buffer, 0, bytesRead);
                        }
                    }
                    pfd.close();
                }
            }
        } catch (Exception e) {
            Timber.e(e,"Error when copying file from " + source.getName() + " to " + target.getAbsolutePath());
            return false;
        } finally {
            closeSilently(inStream);
            closeSilently(outStream);
            closeSilently(inChannel);
            closeSilently(outChannel);
        }
        return true;
    } */

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
            Timber.e(e);
        }
        finally {
            // Ensure that the InputStreams are closed even if there's an exception.
            closeSilently(out);
            closeSilently(in);
        }
    }

    /*
    public boolean copyFile(String path, String newPath) {
        boolean success = false;
        File newFile = new File(newPath);
        File file = new File(path);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        if(!newDir.exists()) {
            // create new directory
            mkdirs(newDir);
        }

        return copyFile(file, newFile);
    } */

    public static boolean move(Context context, final String path, final String newPath) {
        boolean success = false;
        File newFile = new File(newPath);
        File file = new File(path);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        assert newDir != null;
        if(!newDir.exists()) {
            // create new directory
            com.anggrayudi.storage.file.DocumentFileCompat.mkdirs(context, newDir.getAbsolutePath());
            //mkdirs(newDir);
        }

        if(copy(context, file, newFile)) {
            success = delete(context,file);
        }else {
            // remove new file
            delete(context,newFile);
        }
        return success;
    }

    public static boolean delete(Context context, final String file) {
        return delete(context, new File(file));
    }

    /**
     * Delete a file within the constraints of SAF.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    public static boolean delete(Context context, final File file) {
        if (!file.exists()) {
            Timber.i("cannot delete path %s", file.getAbsolutePath());
            return false;
        }

        // First try the normal deletion.
        if (file.delete()) {
            Timber.i( "delete path %s", file.getAbsolutePath());
            return true;
        }

        // Try with Storage Access Framework.
        Timber.i( "start deleting DocumentFile");
        DocumentFile docFile = DocumentFileCompat.fromFile(context, file);
        return DocumentFileUtils.forceDelete(docFile,context);
    }

    @Deprecated
    public static void saveImage(Bitmap resource, File coverartFile) {
        try {
            //Convert bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            resource.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

//write the bytes in file
            FileOutputStream fos = new FileOutputStream(coverartFile);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
        }
        catch ( IOException e ) {
        }
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException t) {
            Timber.w(t);
        }
    }

    public static File getDownloadPath(Context context, String path) {
        File download = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return new File(download, path);
    }

    public static String getStorageName(String storageId) {
        if(StorageId.PRIMARY.equals(storageId)) {
            return "PH";
        }else {
            return "SD";
        }
    }
}
