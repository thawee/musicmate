package apincer.android.utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileUtils {

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

    /*
    private static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath(PATH_TREE)
                .appendPath(getTreeDocumentId(treeUri)).appendPath(PATH_DOCUMENT)
                .appendPath(documentId).build();
    }

    private static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
    } */

    /*
    public static Uri buildDocumentUriMaybeUsingTree(Uri baseUri, String documentId) {
        if (isTreeUri(baseUri)) {
            return buildDocumentUriUsingTree(baseUri, documentId);
        } else {
            return buildDocumentUri(baseUri.getAuthority(), documentId);
        }
    } */

    /*
    private static String getRootUri(Uri uri) {
        if (isTreeUri(uri)) {
            return DocumentsContract.getTreeDocumentId(uri);
        }
        return DocumentsContract.getDocumentId(uri);
    } */

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

    /*
    public static void updateMediaStore(Context context, String path) {
        try {
            Uri contentUri = Uri.fromFile(new File(path).getParentFile());
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
            context.sendBroadcast(mediaScanIntent);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    } */

    /*
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
    } */

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
    public static boolean delete(File directory) {
        // If the directory exists then delete
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                // Run on all sub files and folders and delete them
                for (File file : files) {
                    if (file.isDirectory()) {
                        delete(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
        return false;
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
    }

    public static void createParentDirs(File dir) {
        if(dir == null) return;
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
    }

    public static boolean existed(String path) {
        if(path == null) return false;
        File file = new File(path);
        return file.exists();
    }
}