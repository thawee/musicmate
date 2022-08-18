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
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.fs.MusicFileProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.TagsActivity;

public class ApplicationUtils {

    public static void startAspect(Activity activity, AudioTag tag) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.andrewkhandr.aspect");
        if (intent != null) {
          /*  ApplicationInfo ai = null;
            try {
                ai =  activity.getPackageManager().getApplicationInfo("com.andrewkhandr.aspect",0);  //MusicListeningService.getInstance().getApplicationInfo("com.andrewkhandr.aspect");
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (ai != null) { */
                intent.setAction(Intent.ACTION_SEND);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = tag.getPath();
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri apkURI = MusicFileProvider.getUriForFile(path);
                intent.setDataAndType(apkURI, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
            //}
        }
    }

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

    public static void webSearch(Activity activity, AudioTag item) {
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
            //Uri uri = Uri.parse("http://www.google.com/#q=" + search);
            Uri uri = Uri.parse("http://www.google.com/search?q=" + search);
            Intent gSearchIntent = new Intent(Intent.ACTION_VIEW, uri);
            activity.startActivity(gSearchIntent);

         //   Intent viewSearch = new Intent(Intent.ACTION_WEB_SEARCH);
          //  viewSearch.putExtra(SearchManager.QUERY, search);
          //  activity.startActivity(viewSearch);
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
       // if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            isPlugged = isPlugged || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
       // }
        return isPlugged;
    }

    public static void startFileExplorer(TagsActivity activity, AudioTag displayTag) {
       /* File path = new File(displayTag.getPath());
        Uri uri = MusicFileProvider.getUriForFile(path.getParentFile().getAbsolutePath());

        Intent intent = new Intent();
       // intent.setAction(android.content.Intent.ACTION_VIEW);
       // intent.setType("resource/folder");

        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("file/*");

        intent.setData(uri);
 */
        File filePath = new File(displayTag.getPath());
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("pl.solidexplorer2");
        if (intent != null) {
           /* ApplicationInfo ai = null;
            try {
                ai = activity.getPackageManager().getApplicationInfo("pl.solidexplorer2", 0);  //MusicListeningService.getInstance().getApplicationInfo("com.andrewkhandr.aspect");
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (ai != null) { */
                intent.setAction(Intent.ACTION_VIEW);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = filePath.getParentFile().getAbsolutePath();
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri apkURI = MusicFileProvider.getUriForFile(path);
                intent.setDataAndType(apkURI, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
                return;
           // }
        } else {
                intent = new Intent();
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                // intent.setAction(android.content.Intent.ACTION_VIEW);
                // intent.setType("resource/folder");

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
}
