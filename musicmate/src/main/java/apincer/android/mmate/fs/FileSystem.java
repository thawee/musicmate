package apincer.android.mmate.fs;

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
import java.io.FileNotFoundException;
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

    /*
     * file manipulations
     */
    public static boolean copyFile(Context context, final File source, final File target) {
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

    public static boolean moveFile(Context context, String path, String newPath) {
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

        if(copyFile(context, file, newFile)) {
            success = delete(context,file);
        }else {
            // remove new file
            delete(context,newFile);
        }
        return success;
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


    /**
     * Check if a file is writable. Detects write issues on external SD card.
     *
     * @param file The file
     * @return true if the file is writable.
     */
    @Deprecated
    public static boolean isWritable(final File file) {
        if (file == null) {
            return false;
        }

        boolean isExisting = file.exists();

        try {
            FileOutputStream output = new FileOutputStream(file, true);
            closeSilently(output);
        } catch (FileNotFoundException e) {
            return false;
        }
        boolean result = file.canWrite();

        // Ensure that file is not created during this process.
        if (!isExisting) {
            file.delete();
        }

        return result;
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

    @Deprecated
    public static String removePathExtension(File file) {
        if(file==null) {
            return "";
        }

        String fileName = file.getAbsolutePath();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
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
