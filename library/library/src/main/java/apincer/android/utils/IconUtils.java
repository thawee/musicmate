package apincer.android.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public class IconUtils {

    public static Drawable loadPackageIcon(Context context, String authority, int icon) {
        if (icon != 0) {
            if (authority != null) {
                final PackageManager pm = context.getPackageManager();
                final ProviderInfo info = pm.resolveContentProvider(authority, 0);
                if (info != null) {
                    return pm.getDrawable(info.packageName, icon, info.applicationInfo);
                }
            } else {
                return ContextCompat.getDrawable(context, icon);
            }
        }
        return null;
    }

    public static Drawable applyTintList(Context context, int drawableId, int tintColorId) {
        final Drawable icon = getDrawable(context, drawableId);
        icon.mutate();
        DrawableCompat.setTintList(DrawableCompat.wrap(icon), ContextCompat.getColorStateList(context, tintColorId));
        return icon;
    }

    public static Drawable applyTint(Context context, int drawableId, int tintColorId) {
        final Drawable icon = getDrawable(context, drawableId);
        icon.mutate();
        DrawableCompat.setTint(DrawableCompat.wrap(icon), tintColorId);
        return icon;
    }

    public static Drawable applyTintAttr(Context context, int drawableId, int tintAttrId) {
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(tintAttrId, outValue, true);
        return applyTintList(context, drawableId, outValue.resourceId);
    }


    private static Drawable getDrawable(Context context, int drawableId){
        try {
            return ContextCompat.getDrawable(context, drawableId);
        } catch (Resources.NotFoundException e){
            return null;
        }
    }

    private static Drawable getDrawable(Context context, int drawableId, int missingDrawableId){
        try {
            return ContextCompat.getDrawable(context, drawableId);
        } catch (Resources.NotFoundException e){
            return ContextCompat.getDrawable(context, missingDrawableId);
        }
    }
}