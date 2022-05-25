package apincer.android.mmate.repository;

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

public class MediaFileRepository {

    private Context mContext;

    MediaFileRepository(Context context) {
        super();
        this.mContext = context;
    }

    private Context getContext() {
        return mContext;
    }

    /*
     * file manipulations
     */
    public boolean copyFile(final File source, final File target) {
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
                DocumentFile targetDoc = DocumentFileCompat.fromFile(getContext(), target);
                outStream = DocumentFileUtils.openOutputStream(targetDoc, getContext());
                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[16384]; // MAGIC_NUMBER
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
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
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
            byte[] buf = new byte[1024];
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
            try {
                if ( out != null ) {
                    out.close();
                }

                // If you want to close the "in" InputStream yourself then remove this
                // from here but ensure that you close it yourself eventually.
                in.close();
            }
            catch ( IOException e ) {
            }
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

    public boolean moveFile(String path, String newPath) {
        boolean success = false;
        File newFile = new File(newPath);
        File file = new File(path);

        if(!file.exists()) return false;

        // create new directory if not existed
        File newDir  = newFile.getParentFile();
        if(!newDir.exists()) {
            // create new directory
            com.anggrayudi.storage.file.DocumentFileCompat.mkdirs(getContext(), newDir.getAbsolutePath());
            //mkdirs(newDir);
        }

        if(copyFile(file, newFile)) {
            success = delete(file);
        }else {
            // remove new file
            delete(newFile);
        }
        return success;
    }

    /**
     * Delete a file within the constraints of SAF.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    public boolean delete(final File file) {
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
        DocumentFile docFile = DocumentFileCompat.fromFile(getContext(), file);
        return DocumentFileUtils.forceDelete(docFile,getContext());
    }

    /*
    private boolean isValidMediaByExtension(String ext) {
        if(ext == null) return false;

        if(ext.equalsIgnoreCase("mp3")) {
            return true;
        }else if(ext.equalsIgnoreCase("m4a")) {
            return true;
        }else if(ext.equalsIgnoreCase("flac")) {
            return true;
        }else if(ext.equalsIgnoreCase("wav")) {
            return true;
        }else if(ext.equalsIgnoreCase("aif")) {
            return true;
        }else if(ext.equalsIgnoreCase("dsf")) {
            return true;
       // }else if(ext.equalsIgnoreCase("dff")) {
       //     return true;
        }else return ext.equalsIgnoreCase("iso");
    } */


    /*
    public boolean mkdirs(File file) {
        if(file==null)
            return false;
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        return file.mkdirs(); */
       /* if(!file.mkdirs()) {
            com.anggrayudi.storage.file.DocumentFileUtils.m
            return DocumentFileUtils.mkdirs(getContext(), file);
        }else {
            return true;
        } */
    //}

    @Deprecated
    /**
     * Check if a file is writable. Detects write issues on external SD card.
     *
     * @param file The file
     * @return true if the file is writable.
     */
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
/*
    public static MediaFileRepository getInstance(Context context) {
        if(INSTANCE ==null && context!=null) {
            INSTANCE = new MediaFileRepository();
        }
        return INSTANCE;
    } */

    public static String getStorageName(String storageId) {
        if(StorageId.PRIMARY.equals(storageId)) {
            return "PH";
        }else {
            return "SD";
        }
    }
}
