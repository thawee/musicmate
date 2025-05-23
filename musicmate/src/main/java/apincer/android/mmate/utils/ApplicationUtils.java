package apincer.android.mmate.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.provider.MusicFileProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.TagsActivity;

public class ApplicationUtils {

    public static void startAspect(Activity activity, MusicTag tag) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.andrewkhandr.aspect");
        if (intent != null) {
                intent.setAction(Intent.ACTION_SEND);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = tag.getPath();
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri apkURI = MusicFileProvider.getUriForFile(path);
                intent.setDataAndType(apkURI, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);

        }
    }

    @Deprecated
    public static boolean isAppRunning(final Context context, final String packageName) {
        // can see nly activity on self-app
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos != null)
        {
            for (final ActivityManager.RunningAppProcessInfo processInfo : procInfos) {
                if (processInfo.processName.equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getAssetsText(Activity activity, String inFile) {
        String tContents = "";

        try {
            InputStream stream =  activity.getAssets().open(inFile);

            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer);
            stream.close();
            tContents = new String(buffer);
        } catch (IOException e) {
            // Handle exceptions here
        }

        return tContents;

    }

    public static InputStream getAssetsAsStream(Context context, String inFile) {

        try {
            return context.getAssets().open(inFile);
        } catch (IOException e) {
            // Handle exceptions here
        }

        return null;
    }

    @Deprecated
    public static String getAssetsText(Context context, String inFile) {
        String tContents = "";

        try {
            InputStream stream =  context.getAssets().open(inFile);

            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer);
            stream.close();
            tContents = new String(buffer);
        } catch (IOException e) {
            // Handle exceptions here
        }

        return tContents;

    }

    public static void webSearch(Activity activity, MusicTag item) {
        String text = "";
        // title and artist
        if(!StringUtils.isEmpty(item.getTitle())) {
            text = text+" "+item.getTitle();
        }
        if(!StringUtils.isEmpty(item.getAlbum())) {
            text = text+" "+item.getAlbum();
        }
        if(!StringUtils.isEmpty(item.getArtist())) {
            text = text+" "+item.getArtist();
        }
        String search= text; //StandardCharsets.UTF_8);
        try {
            search = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        //Uri uri = Uri.parse("http://www.google.com/#q=" + search);
        Uri uri = Uri.parse("http://www.google.com/search?q=" + search);
        Intent gSearchIntent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivity(gSearchIntent);

    }

    @Deprecated
    public static boolean isIntentAvailable(Context context, Intent intent)
    {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    @Deprecated
    public static boolean isPlugged(Context context) {
        boolean isPlugged;
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
       // if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            isPlugged = isPlugged || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
       // }
        return isPlugged;
    }

    public static void startFileExplorer(TagsActivity activity, MusicTag displayTag) {
        File filePath = new File(displayTag.getPath());
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("pl.solidexplorer2");
        if(filePath.getParentFile() == null) return;
        if (intent != null) {
                intent.setAction(Intent.ACTION_VIEW);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = filePath.getParentFile().getAbsolutePath();
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri apkURI = MusicFileProvider.getUriForFile(path);
                intent.setDataAndType(apkURI, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
        } else {
                intent = new Intent();
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                intent.setAction(Intent.ACTION_VIEW);
                String path = filePath.getParentFile().getAbsolutePath();
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri uri = MusicFileProvider.getUriForFile(path);
                intent.setDataAndType(uri, type);
                activity.startActivity(intent);
        }
    }

    public static SearchCriteria getSearchCriteria(Intent intent) {
        SearchCriteria criteria = null;
        String type = intent.getStringExtra(Constants.KEY_SEARCH_TYPE);
        if(type!=null) {
            String keyword = intent.getStringExtra(Constants.KEY_SEARCH_KEYWORD);
            criteria = new SearchCriteria(SearchCriteria.TYPE.valueOf(type), keyword);
            criteria.setFilterType(intent.getStringExtra(Constants.KEY_FILTER_TYPE));
            criteria.setFilterText(intent.getStringExtra(Constants.KEY_FILTER_KEYWORD));
        }
        return criteria;
    }

    public static void setSearchCriteria(Intent intent, SearchCriteria criteria) {
        if(criteria!=null && intent!=null) {
            intent.putExtra(Constants.KEY_SEARCH_TYPE, criteria.getType().name());
            intent.putExtra(Constants.KEY_SEARCH_KEYWORD,StringUtils.trimToEmpty(criteria.getKeyword()));
            intent.putExtra(Constants.KEY_FILTER_TYPE,StringUtils.trimToEmpty(criteria.getFilterType()));
            intent.putExtra(Constants.KEY_FILTER_KEYWORD,StringUtils.trimToEmpty(criteria.getFilterText()));
        }
    }

    public static void openBrowser(Context context, String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(browserIntent);
    }

    public static ByteBuffer getAssetsAsBytes(Context context, String inFile) {
        try {
            InputStream stream =  context.getAssets().open(inFile);

            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer);
            stream.close();
            return ByteBuffer.wrap(buffer);
        } catch (IOException e) {
            // Handle exceptions here
        }

        return null;
    }

    public static String getDeviceDetails() {
        return "Android " +StringUtils.trimToEmpty(Build.VERSION.RELEASE) +" on "+StringUtils.trimToEmpty(Build.MANUFACTURER) +" "+  StringUtils.trimToEmpty(Build.MODEL)+".";
    }

    public static String getVersionNumber(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {

        }
        return "1.0.0";
    }

    public static long getVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) {

        }
        return 0;
    }

    public static String getDeviceModel() {
        return StringUtils.trimToEmpty(Build.MODEL);
    }

}
