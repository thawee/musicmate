package apincer.music.core.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import apincer.music.core.Constants;
import apincer.music.core.provider.MusicFileProvider;
import apincer.music.core.database.MusicTag;
import apincer.music.core.model.SearchCriteria;
import apincer.android.utils.FileUtils;

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


    /**
     * Recursively copies files and directories from a given path in the assets to the cache.
     * @param context The application context.
     * @param path The path within the assets folder.
     */
    public static void copyFilesToCache(Context context, String path) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(path);

        if (assets == null || assets.length == 0) { // It's a file
            copyFile(context, path);
        } else { // It's a directory
            File fullPath = new File(context.getFilesDir(), path);
            if (!fullPath.exists()) {
                fullPath.mkdirs();
            }
            for (String asset : assets) {
                String newPath = path.isEmpty() ? asset : path + "/" + asset;
                copyFilesToCache(context, newPath);
            }
        }
    }

    /**
     * Copies a single file from assets to the cache directory.
     * @param context The application context.
     * @param filename The name/path of the file in assets.
     */
    private static void copyFile(Context context, String filename) throws IOException {
        File newFile = new File(context.getFilesDir(), filename);

        // Use try-with-resources to automatically close streams
        try (InputStream inputStream = context.getAssets().open(filename);
             OutputStream outputStream = new FileOutputStream(newFile, false)) {

            // Manually copy the file stream
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
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
        search = URLEncoder.encode(text, StandardCharsets.UTF_8);
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

    public static void startFileExplorer(Activity activity, MusicTag displayTag) {
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

    public static String getAndroidRelease() {
       // return "Android " +StringUtils.trimToEmpty(Build.VERSION.RELEASE) +" on "+StringUtils.trimToEmpty(Build.MANUFACTURER) +" "+  StringUtils.trimToEmpty(Build.MODEL)+".";
       return "Android " +StringUtils.trimToEmpty(Build.VERSION.RELEASE);
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
        //SM-S931B for S25
        return StringUtils.trimToEmpty(Build.MODEL);
    }

    /**
     * Returns a user-friendly, marketable device name for presentation.
     * It attempts to map known technical model codes to their common names
     * (e.g., "SM-S931B" -> "Galaxy S25"). If the model is unknown, it falls back
     * to a sensible "Manufacturer + Model" format.
     *
     * @return A clean, user-facing device name.
     */
    public static String getFriendlyDeviceName() {
        String model = Build.MODEL;

        // The mapping logic. Add more popular devices here over time.
        switch (model) {
            // --- Samsung Galaxy S Series ---
            case "SM-S931B": // This is your hypothetical S25 example
                return "Galaxy S25";
            case "SM-S928B":
            case "SM-S928U":
                return "Galaxy S24 Ultra";
            case "SM-S918B":
            case "SM-S918U":
                return "Galaxy S23 Ultra";
            case "SM-S908E":
            case "SM-S908U":
                return "Galaxy S22 Ultra";

            // --- Google Pixel Series ---
            case "Pixel 8 Pro":
                return "Google Pixel 8 Pro";
            case "Pixel 8":
                return "Google Pixel 8";
            case "Pixel 7 Pro":
                return "Google Pixel 7 Pro";
            case "Pixel 7":
                return "Google Pixel 7";

            // --- Add other common models here ---
            // case "some-other-model-code":
            //     return "Friendly Name";

            default:
                // Fallback for unknown models
                return getGenericDeviceName();
        }
    }

    /**
     * Returns a generic device name by combining manufacturer and model.
     * Used as a fallback when a friendly name isn't available.
     * @return A formatted, user-facing device name (e.g., "Samsung SM-S931B").
     */
    private static String getGenericDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public static void deleteFilesFromCache(Context context, String path) {
        File fullPath = new File(context.getFilesDir(), path);
        if (fullPath.exists()) {
            FileUtils.delete(fullPath);
        }
    }

    public static void purgeAndroidCache(Context context) {

    }

    public static void purgeAndroidFiles(Context context) {
         context.getFilesDir();

    }

    public static boolean isInstalled(Context context, String packName) {
        try {
            context.getPackageManager().getPackageInfo(packName,0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }


}
