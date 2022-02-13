package apincer.android.mmate.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

public class PermissionUtils {

    public static boolean hasPermissions(@NonNull Context context,
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

                /*
                List<UriPermission> perms = context.getContentResolver().getPersistedUriPermissions();
                int i =0;
                for (UriPermission perm:perms) {
                    if(perm.getUri().equals(uri)) {
                        JSONObject json = new JSONObject(pathMap);
                        json.put(rootPath, i);
                        perf.edit().putString("PersistableUriPermissions", json.toString()).apply();
                        break;
                    }
                    i++;
                }*/
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            Timber.e( "no write permission: %s", rootPath);
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
       // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
       // }
      //  else
      //  {
      //      return true;
      //  }
    }
}
