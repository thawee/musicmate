package apincer.android.mmate.utils;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import apincer.android.mmate.fs.MusicFileProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.service.MusicListeningService;

public class ApplicationUtils {

    public static void startAspect(Activity activity, AudioTag tag) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.andrewkhandr.aspect");
        if (intent != null) {
            ApplicationInfo ai = MusicListeningService.getInstance().getApplicationInfo("com.andrewkhandr.aspect");
            if (ai != null) {
                intent.setAction(Intent.ACTION_SEND);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = tag.getPath();
                //File file = new File(path);
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkURI = MusicFileProvider.getUriForFile(path);
                // Uri apkURI = FileProvider.getUriForFile(getApplicationContext(), "ru.zdevs.zarchiver.system.FileProvider", file);
                intent.setDataAndType(apkURI, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                //  } else {
                //     intent.setDataAndType(Uri.fromFile(file), type);
                //  }
                activity.startActivity(intent);
            }
        }
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

    public void webSearch(Activity activity, AudioTag item) {
        try {
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
            String search= URLEncoder.encode(text, "UTF-8");
            // Uri uri = Uri.parse("http://www.google.com/#q=" + search);
            // Intent gSearchIntent = new Intent(Intent.ACTION_VIEW, uri);
            // startActivity(gSearchIntent);

            Intent viewSearch = new Intent(Intent.ACTION_WEB_SEARCH);
            viewSearch.putExtra(SearchManager.QUERY, search);
            activity.startActivity(viewSearch);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        /*
        Uri uri = Uri.parse("http://www.google.com/#q=" + Search);
        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, uri);
        startActivity(searchIntent);
        */
    }

    /*
    boolean isServiceRunning(Context context, Class<?> serviceClass){
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> mRunningServices = manager.getRunningServices(Integer.MAX_VALUE);
        for(ActivityManager.RunningServiceInfo appService : mRunningServices){
            if(serviceClass.getName().equals(appService.service.getClassName()))
                return true;
        }
        return false;
    } */

    public static boolean isIntentAvailable(Context context, Intent intent)
    {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static boolean isPlugged(Context context) {
        boolean isPlugged= false;
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            isPlugged = isPlugged || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }
        return isPlugged;
    }

    public static boolean isCharging(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            return batteryManager.isCharging();
        } else {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent intent = context.registerReceiver(null, filter);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        }
    }

}
