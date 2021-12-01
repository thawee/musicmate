package apincer.android.mmate.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class PermissionUtils {

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        else
        {
            return true;
        }
    }
}
