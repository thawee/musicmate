package apincer.android.utils;

import android.app.UiModeManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;

import androidx.annotation.DimenRes;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;


public class Utils {

    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;
    private static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
    /**
     * MIME types that are visual in nature. For example, they should always be
     * shown as thumbnails in list mode.
     */
    public static final String[] VISUAL_MIMES = new String[] {
            "image/*",
            "video/*",
            "audio/*",
            MIME_TYPE_APK};

    public static final String[] SPECIAL_MIMES = new String[] {
            "application/zip",
            "application/rar",
            "application/gzip",
            MIME_TYPE_APK};

    public static final String[] COMPRESSED_MIMES = new String[] {
            "application/zip",
            "application/rar",
            "application/gzip"};

    public static final String[] SHARE_SKIP_MIMES = new String[] {
            MIME_TYPE_APK };

    public static final String[] TEXT_MIMES = new String[] {
            "text/*", };

    public static boolean isTelevision(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static int dpToPx(int dp) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / DisplayMetrics.DENSITY_MEDIUM);
        return Math.round(px);
    }

    public static boolean isRTL() {
        return TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    public static ContentProviderClient acquireUnstableProviderOrThrow(
            ContentResolver resolver, String authority) throws RemoteException {
        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(authority);
        if (client == null) {
            throw new RemoteException("Failed to acquire provider for " + authority);
        }
        setDetectNotResponding(client, PROVIDER_ANR_TIMEOUT);
        return client;
    }

    private static void setDetectNotResponding(ContentProviderClient client, long anrTimeout) {
        try {
            Method method = client.getClass().getMethod("setDetectNotResponding", long.class);
            if (method != null) {
                method.invoke(client, anrTimeout);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeQuietly(Cursor closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeQuietly(ZipFile closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isDir(String mimeType) {
        return mimeMatches(DocumentsContract.Document.MIME_TYPE_DIR, mimeType);
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }


    public static boolean mimeMatches(String filter, String[] tests) {
        if (tests == null) {
            return true;
        }
        for (String test : tests) {
            if (mimeMatches(filter, test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeMatches(String[] filters, String test) {
        if (filters == null) {
            return true;
        }
        for (String filter : filters) {
            if (mimeMatches(filter, test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeMatches(String filter, String test) {
        if (test == null) {
            return false;
        } else if (filter == null || "*/*".equals(filter)) {
            return true;
        } else if (filter.equals(test)) {
            return true;
        } else if (filter.endsWith("/*")) {
            return filter.regionMatches(0, test, 0, filter.indexOf('/'));
        } else {
            return false;
        }
    }


    public static float getDimension(Context context, @DimenRes int resourceId) {
        return context.getResources().getDimension(resourceId);
    }

    public static int getDimensionPixelSize(Context context, @DimenRes int resourceId) {
        return context.getResources().getDimensionPixelSize(resourceId);
    }
}