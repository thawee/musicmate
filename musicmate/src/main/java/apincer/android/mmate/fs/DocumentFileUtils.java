package apincer.android.mmate.fs;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Preferences;
import timber.log.Timber;

@Deprecated
public class DocumentFileUtils {

    private static final String TAG = DocumentFileUtils.class.getSimpleName();

    public static final int OPEN_DOCUMENT_TREE_CODE = 8000;

    private static List<String> sExtSdCardPaths = new ArrayList<>();

    private DocumentFileUtils() {

    }

    public static void cleanCache() {
        sExtSdCardPaths.clear();
    }

    /**
     * Get a list of external SD card paths. (Kitkat or higher.)
     *
     * @return A list of external SD card paths.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String[] getExtSdCardPaths(Context context) {
        if (sExtSdCardPaths.size() > 0) {
            return sExtSdCardPaths.toArray(new String[0]);
        }
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Timber.w(TAG, "Unexpected external file dir: %s", file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    sExtSdCardPaths.add(path);
                }
            }
        }
        if (sExtSdCardPaths.isEmpty()) sExtSdCardPaths.add("/storage/sdcard1");
        return sExtSdCardPaths.toArray(new String[0]);
    }

    /**
     * Determine the main folder of the external SD card containing the given file.
     *
     * @param file the file.
     * @return The main folder of the external SD card containing this file, if the file is on an SD
     * card. Otherwise,
     * null is returned.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String getExtSdCardFolder(final File file, Context context) {
        String[] extSdPaths = getExtSdCardPaths(context);
        try {
            for (int i = 0; i < extSdPaths.length; i++) {
                if (file.getCanonicalPath().startsWith(extSdPaths[i])) {
                    return extSdPaths[i];
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Determine if a file is on external sd card. (Kitkat or higher.)
     *
     * @param file The file.
     * @return true if on external sd card.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isOnExtSdCard(final File file, Context c) {
        return true; // FIXME
        //return getExtSdCardFolder(file, c) != null;
    }

    /**
     * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5).
     * If the file is not
     * existing, it is created.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the file should be a directory.
     * @return The DocumentFile
     */
    public static DocumentFile getDocumentFile(final File file, final boolean isDirectory,
                                               Context context) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return DocumentFile.fromFile(file);
        }

        String baseFolder = getExtSdCardFolder(file, context);
        boolean originalDirectory = false;
        if (baseFolder == null) {
            return null;
        }

        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                originalDirectory = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            originalDirectory = true;
            //continue
        }

/*
        List<UriPermission> perms = context.getContentResolver().getPersistedUriPermissions();
        if (perms == null || perms.size()==0) {
            return null;
        }
        Uri treeUri = perms.get(0).getUri();
        if (treeUri == null) {
            return null;
        } */

        Uri treeUri = Preferences.getPersistableUriPermission(context, file.getAbsolutePath());
        if (treeUri == null) {
            return null;
        }

        // start with root of SD card and then parse through document tree.
        DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
        if (originalDirectory) return document;
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);

            if (nextDocument == null) {
                if ((i < parts.length - 1) || isDirectory) {
                    nextDocument = document.createDirectory(parts[i]);
                } else {
                    nextDocument = document.createFile("image", parts[i]);
                }
            }
            document = nextDocument;
        }

        return document;
    }

    public static boolean mkdirs(Context context, File dir) {
        boolean res = dir.mkdirs();
        if (!res) {
            if (DocumentFileUtils.isOnExtSdCard(dir, context)) {
                DocumentFile documentFile = DocumentFileUtils.getDocumentFile(dir, true, context);
                res = documentFile != null && documentFile.canWrite();
            }
        }
        return res;
    }

    public static boolean delete(Context context, File file) {
        boolean ret = file.delete();

        if (!ret && DocumentFileUtils.isOnExtSdCard(file, context)) {
            DocumentFile f = DocumentFileUtils.getDocumentFile(file, false, context);
            if (f != null) {
                ret = f.delete();
            }
        }
        return ret;
    }

    public static boolean canWrite(File file) {
        return file.exists() && file.canWrite();
      //  boolean res = file.exists() && file.canWrite();
/*
        if (!res && !file.exists()) {
            try {
                if (!file.isDirectory()) {
                    res = file.createNewFile() && file.delete();
                } else {
                    res = file.mkdirs() && file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } */
       // return res;
    }

    public static boolean canRead(File file) {
        return file.exists() && file.canRead();
    }

    public static boolean canWrite(Context context, File file) {
        boolean res = canWrite(file);

        if (!res) { // && DocumentFileUtils.isOnExtSdCard(file, context)) {
            DocumentFile documentFile = DocumentFileUtils.getDocumentFile(file, true, context);
            res = documentFile != null && documentFile.canWrite();
        }
        return res;
    }

    public static boolean renameTo(Context context, File src, File dest) {
        boolean res = src.renameTo(dest);

        if (!res && isOnExtSdCard(dest, context)) {
            DocumentFile srcDoc;
            if (isOnExtSdCard(src, context)) {
                srcDoc = getDocumentFile(src, false, context);
            } else {
                srcDoc = DocumentFile.fromFile(src);
            }
            DocumentFile destDoc = getDocumentFile(dest.getParentFile(), true, context);
            if (srcDoc != null && destDoc != null) {
                try {
                    if (src.getParent().equals(dest.getParent())) {
                        res = srcDoc.renameTo(dest.getName());
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        res = DocumentsContract.moveDocument(context.getContentResolver(),
                                srcDoc.getUri(),
                                srcDoc.getParentFile().getUri(),
                                destDoc.getUri()) != null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return res;
    }

    public static FileChannel getFileChannel(Context context, File destFile) {
        FileChannel in = null;
        try {
            if (!canWrite(destFile)) { // && isOnExtSdCard(destFile, context)) {
                DocumentFile file = DocumentFileUtils.getDocumentFile(destFile, false, context);
                return getFileChannel(context, file);
            } else {
                in = (new FileInputStream(destFile)).getChannel();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static FileChannel getFileChannel(Context context, DocumentFile doc) {
        try {
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(doc.getUri(), "rw");
                FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                return fis.getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        return null;
    }

    public static InputStream getInputStream(Context context, File destFile) {
        InputStream in = null;
        try {
            if (!canRead(destFile)) { // && isOnExtSdCard(destFile, context)) {
                DocumentFile file = DocumentFileUtils.getDocumentFile(destFile, false, context);
                return getInputStream(context, file);
            } else if(destFile.exists()){
                in = new FileInputStream(destFile);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static InputStream getInputStream(Context context, DocumentFile doc) {
        InputStream in = null;
        try {
            if (doc != null && doc.canWrite()) {
                in = context.getContentResolver().openInputStream(doc.getUri());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static OutputStream getOutputStream(Context context, File destFile) {
        OutputStream out = null;
        try {
            if (!canWrite(destFile) && isOnExtSdCard(destFile, context)) {
                DocumentFile file = DocumentFileUtils.getDocumentFile(destFile, false, context);
                return getOutputStream(context, file);
            } else {
                out = new FileOutputStream(destFile);

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return out;
    }

    public static OutputStream getOutputStream(Context context, DocumentFile doc) {
        OutputStream out = null;
        try {
            if (doc != null && doc.canWrite()) {
                out = context.getContentResolver().openOutputStream(doc.getUri());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return out;
    }

    public static boolean checkWritableRootPath(Context context, String rootPath) {
        File root = new File(rootPath);
        if (!root.canWrite()) {

            if (DocumentFileUtils.isOnExtSdCard(root, context)) {
                DocumentFile documentFile = DocumentFileUtils.getDocumentFile(root, true, context);
                return documentFile == null || !documentFile.canWrite();
            } else {
                SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);

                String documentUri = perf.getString(rootPath, "");

                if (documentUri == null || documentUri.isEmpty()) {
                    return true;
                } else {
                    DocumentFile file = DocumentFile.fromTreeUri(context, Uri.parse(documentUri));
                    return !(file != null && file.canWrite());
                }
            }
        }
        return false;
    }

    public static String getRealPathFromUri(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }else {
                    // TODO handle non-primary volumes
                    System.out.println(type);
                }
            }
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}
