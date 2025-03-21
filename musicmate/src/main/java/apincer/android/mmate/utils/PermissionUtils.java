package apincer.android.mmate.utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

public class PermissionUtils {
    private static final String TAG = PermissionUtils.class.getName();

    public static String[] PERMISSIONS_ALL = {Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.READ_MEDIA_AUDIO};

    public static boolean hasPermissions(@NonNull Context context, @Size(min = 1) @NonNull String... perms) {
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasPermissionsOld(@NonNull Context context,
                                         @Size(min = 1) @NonNull String... perms) {
        // Always return true for SDK < M, let the system deal with the permissions
       // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {

            // DANGER ZONE!!! Changing this will break the library.
       //     return true;
       // }

        // Null context may be passed if we have detected Low API (less than M) so getting
        // to this point with a null context should not be possible.
        if (context == null) {
            throw new IllegalArgumentException("Can't check permissions for null context");
        }

        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(context, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }


    public static boolean setPersistableUriPermission(Context context, String rootPath, Uri uri) {
        DocumentFile file = DocumentFile.fromTreeUri(context, uri);
        if (file != null && file.canWrite()) {
            SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);
            String pathMap =perf.getString("PersistableUriPermissions", "{}");
            try {
                JSONObject json = new JSONObject(pathMap);
                json.put(rootPath, uri.getPath());
                perf.edit().putString("PersistableUriPermissions", json.toString()).apply();

            } catch (JSONException e) {
                Log.e(TAG, "", e);
            }
            return true;
        } else {
            Log.i(TAG, "no write permission: "+rootPath);
        }
        return false;
    }


    public static boolean isPermissionsEnabled(Context context, String[] permissionList)
    {
        for (String permission : permissionList)
        {
            if (!isPermissionEnabled(context, permission))
            {
                return false;
            }
        }

        return true;
    }

    public static boolean isPermissionEnabled(Context context, String permission)
    {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkAccessPermissions(Context context) {
      //  return checkMediaAccessPermissions(context) && checkFullStorageAccessPermissions(context);
        return checkFullStorageAccessPermissions(context);
    }

    public static boolean checkFullStorageAccessPermissions(Context context) {
        return Environment.isExternalStorageManager();
    }

    public static boolean checkMediaAccessPermissions(Context context) {
        String audioPermission = Manifest.permission.READ_MEDIA_AUDIO;
        String imagesPermission = Manifest.permission.READ_MEDIA_IMAGES;
        String videoPermission = Manifest.permission.READ_MEDIA_VIDEO;
        // Check for permissions and if permissions are granted then it will return true
        // You have the permissions, you can proceed with your media file operations.
        return context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(imagesPermission) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(videoPermission) == PackageManager.PERMISSION_GRANTED;
    }

}
