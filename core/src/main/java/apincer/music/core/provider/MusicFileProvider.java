package apincer.music.core.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.Executors;

public final class MusicFileProvider extends ContentProvider {
       // private static final String[] a = new String[]{"_display_name", "_size"};
       @Override
       public boolean onCreate() {
           // Initialize WorkManager with the default configuration.
           WorkManager.initialize(
                   getContext(),
                   new Configuration.Builder()
                           .setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()))
                           .build());
           return true;
       }

        public static Uri getUriForFile(String str) {
            return new Builder().scheme("content").authority("apincer.android.mmate.provider").path(str).build();
        }

        private File getFileForUri(Uri uri) {
            if (getContext() != null) {
                return new File(uri.getPath());
            }
            throw new IllegalStateException("No Context attached");
        }

        private static Object[] copyArray(Object[] objArr, int i) {
            Object[] objArr2 = new Object[i];
            System.arraycopy(objArr, 0, objArr2, 0, i);
            return objArr2;
        }

        private static String[] copyArray(String[] strArr, int i) {
            String[] strArr2 = new String[i];
            System.arraycopy(strArr, 0, strArr2, 0, i);
            return strArr2;
        }

        private static int b(String str) {
            if ("r".equals(str)) {
                return 268435456;
            }
            if ("w".equals(str) || "wt".equals(str)) {
                return 738197504;
            }
            if ("wa".equals(str)) {
                return 704643072;
            }
            if ("rw".equals(str)) {
                return 939524096;
            }
            if ("rwt".equals(str)) {
                return 1006632960;
            }
            String stringBuilder = "Invalid mode: " +
                    str;
            throw new IllegalArgumentException(stringBuilder);
        }

        public int delete(@NonNull Uri uri, String str, String[] strArr) {
            return getFileForUri(uri).delete()?1:0;
        }

        public String getType(@NonNull Uri uri) {
            File a = getFileForUri(uri);
            return apincer.music.core.utils.MimeTypeUtils.getMimeTypeFromPath(a.getPath());
        }

        public Uri insert(@NonNull Uri uri, ContentValues contentValues) {
            throw new UnsupportedOperationException("No support inserts");
        }

        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String str) throws FileNotFoundException {
            File file = getFileForUri(uri);
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + uri);
            }
            return ParcelFileDescriptor.open(file, b(str));
        }

        public Cursor query(@NonNull Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
            File a = getFileForUri(uri);
            String[] cols = (strArr != null && strArr.length > 0)
                    ? strArr
                    : new String[]{"_display_name", "_size", "_data"};

            Object[] values = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                String col = cols[i];
                if ("_display_name".equals(col)) {
                    values[i] = a.getName();
                } else if ("_size".equals(col)) {
                    values[i] = a.length();
                } else if ("_data".equals(col)) {
                    values[i] = a.getAbsolutePath();
                } else if ("mime_type".equalsIgnoreCase(col)) {
                    values[i] = getType(uri);
                } else {
                    values[i] = null;
                }
            }

            MatrixCursor matrixCursor = new MatrixCursor(cols, 1);
            matrixCursor.addRow(values);
            return matrixCursor;
        }

        public int update(@NonNull Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }
