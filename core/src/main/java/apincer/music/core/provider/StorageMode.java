package apincer.music.core.provider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * How this build reaches the music library, read from its own manifest. The GitHub build requests
 * all-files access and works by path; the Play build does not request it (Play policy) and works with
 * READ_MEDIA_AUDIO plus folders the user granted in the system folder picker.
 */
public final class StorageMode {
    private static final String TAG = "StorageMode";
    private static volatile Boolean allFilesAccess;

    private StorageMode() {
    }

    /** True for the GitHub build, which requests all-files access. */
    public static boolean usesAllFilesAccess(Context context) {
        Boolean known = allFilesAccess;
        if (known == null) {
            known = requests(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE);
            allFilesAccess = known;
        }
        return known;
    }

    static boolean requests(Context context, String permission) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            if (info.requestedPermissions == null) return false;
            for (String requested : info.requestedPermissions) {
                if (permission.equals(requested)) return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot read this app's permissions", e);
        }
        return false;
    }
}
