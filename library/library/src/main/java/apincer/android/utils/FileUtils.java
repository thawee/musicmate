package apincer.android.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract; 
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.List;

import androidx.documentfile.provider.DocumentFile;
import apincer.android.library.R;
import okio.Buffer;
import okio.Okio;
import okio.Source;

import static android.provider.DocumentsContract.buildDocumentUri;
import static android.provider.DocumentsContract.getTreeDocumentId;

public class FileUtils {
    private static final String PATH_TREE = "tree";
    private static final String PATH_DOCUMENT = "document";
    public static final String BASIC_MIME_TYPE = "application/octet-stream";

    @Deprecated
    public static String getTypeForFile(File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }

    /**
     * Returns the extension of the given file.
     * The extension is empty if there is no extension
     * The extension is the string after the last "."
     *
     * @param f The file whose extension is requested
     * @return The extension of the given file
     */
    public static String getExtension(final File f)
    {
        final String name = f.getName().toLowerCase();
        final int i = name.lastIndexOf(".");
        if (i == -1)
        {
            return "";
        }

        return name.substring(i + 1);
    }

    /**
     * Returns the extension of the given file.
     * The extension is empty if there is no extension
     * The extension is the string after the last "."
     *
     * @param filename The file whose extension is requested
     * @return The extension of the given file
     */
    public static String getExtension(final String filename)
    {
        final String name = filename.toLowerCase();
        final int i = name.lastIndexOf(".");
        if (i == -1)
        {
            return "";
        }

        return name.substring(i + 1);
    }

    public static String removeExtension(File file) {
        if(file==null) {
            return "";
        }

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public static String removeExtension(String fileName) {
        if(fileName==null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    private static String getTypeForFile(DocumentFile file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }

    public static String formatFileCount(Context context, int count) {
        String value = NumberFormat.getInstance().format(count);
        String fileIndex = context.getString(R.string.index_file);
        String empty = context.getString(R.string.index_empty);
        return count == 0 ? empty : value + " " + fileIndex;
    }

    public static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }

        return BASIC_MIME_TYPE;
    }

    private static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath(PATH_TREE)
                .appendPath(getTreeDocumentId(treeUri)).appendPath(PATH_DOCUMENT)
                .appendPath(documentId).build();
    }

    private static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
    }

    public static Uri buildDocumentUriMaybeUsingTree(Uri baseUri, String documentId) {
        if (isTreeUri(baseUri)) {
            return buildDocumentUriUsingTree(baseUri, documentId);
        } else {
            return buildDocumentUri(baseUri.getAuthority(), documentId);
        }
    }

    public static Uri getRootUriForStorage(Context context, String storageId){
        Uri treeUri = null;

        //get root dynamically
        List<UriPermission> permissions = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : permissions) {
            String treeRootId = getRootUri(permission.getUri());
            //if(storageId.startsWith(treeRootId) || storageId.contains(treeRootId)){
            if(treeRootId.endsWith(":")) treeRootId = treeRootId.substring(0,treeRootId.length()-1);
            if(storageId.contains(treeRootId)){
                treeUri = permission.getUri();
                return treeUri;
            }
        }
        return treeUri;
    }

    private static String getRootUri(Uri uri) {
        if (isTreeUri(uri)) {
            return DocumentsContract.getTreeDocumentId(uri);
        }
        return DocumentsContract.getDocumentId(uri);
    }

    /**
     * Test if a file lives under the given directory, either as a direct child
     * or a distant grandchild.
     * <p>
     * Both files <em>must</em> have been resolved using
     * {@link File#getCanonicalFile()} to avoid symlink or path traversal
     * attacks.
     */
    public static boolean contains(File dir, File file) {
        if (dir == null || file == null) return false;
        String dirPath = dir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (dirPath.equals(filePath)) {
            return true;
        }
        if (!dirPath.endsWith("/")) {
            dirPath += "/";
        }
        return filePath.startsWith(dirPath);
    }

    public static void updateMediaStore(Context context, String path) {
        try {
            Uri contentUri = Uri.fromFile(new File(path).getParentFile());
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
            context.sendBroadcast(mediaScanIntent);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public static String makeFilePath(File parentFile, String name){
        if(null == parentFile || TextUtils.isEmpty(name)){
            return "";
        }
        return new File(parentFile, name).getPath();
    }

    public static int parseMode(String mode) {
        final int modeBits;
        if ("r".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Bad mode '" + mode + "'");
        }
        return modeBits;
    }

    @Deprecated
    public static boolean isExisted(String s) {
        if(s ==null) return false;

        File f = new File(s);
        return f.exists();
    }

    public static String getFileName(String mediaPath) {
        if(mediaPath==null) {
            return "";
        }
        File file = new File(mediaPath);
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    @Deprecated
    public static String getFullFileName(String mediaPath) {
        if(mediaPath==null) {
            return "";
        }
        File file = new File(mediaPath);
        return file.getName();
    }

    @Deprecated
    public static String getFolderName(String mediaPath) {
        if(mediaPath==null) {
            return "";
        }
        File file = new File(mediaPath);
        return file.getParentFile().getPath();
    }

    public static String getParentName(String path) {
        if(path != null) {
            int indx = path.lastIndexOf("/");
            if(indx>0) {
                return path.substring(0, indx);
            }
        }
        return "";
    }

    /**
     * Delete the directory and all sub content.
     *
     * @param directory The absolute directory path. For example:
     *             <i>mnt/sdcard/NewFolder/</i>.
     */
    public static void deleteDirectory(File directory) {

        // If the directory exists then delete
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files == null) {
                return;
            }
            // Run on all sub files and folders and delete them
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        directory.delete();
    }

    public static ByteBuffer getBytes(File file) throws IOException {
        // nio
        Path filePath = Paths.get(file.getAbsolutePath());
        // Open the file for reading
        ByteBuffer buffer;
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {

            // Get the size of the file
            long fileSize = fileChannel.size();

            // Allocate a ByteBuffer to hold the file's contents
            buffer = ByteBuffer.allocate((int) fileSize);

            // Read the file into the ByteBuffer
            fileChannel.read(buffer);

            // Flip the buffer to prepare it for reading
            buffer.flip();
        } 

        return buffer;

        //okio
        /*
        try (Source source = Okio.source(file); Buffer buffer = new Buffer()) {
            buffer.writeAll(source);
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } */
    }
}