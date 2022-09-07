package apincer.android.mmate;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.WorkManager;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.broadcast.Callback;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.objectbox.ObjectBox;
import sakout.mehdi.StateViews.StateViewsBuilder;
import timber.log.Timber;

public class MusixMateApp extends Application  {
    private static final Logger jAudioTaggerLogger1 = Logger.getLogger("org.jaudiotagger.audio");
    private static final Logger jAudioTaggerLogger2 = Logger.getLogger("org.jaudiotagger");

    private static final BroadcastHelper broadcastHelper = new BroadcastHelper(new Callback() {
        @Override
        public void onPlaying(Context context, AudioTag song) {
            try {
                BroadcastData data = new BroadcastData()
                        .setAction(BroadcastData.Action.PLAYING)
                        .setStatus(BroadcastData.Status.COMPLETED)
                        .setTagInfo(song)
                        .setMessage("");
                Intent intent = data.getIntent();
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }
    });

    private static Map<String, List<AudioTag>> pendingQueue = new HashMap();

    public static AudioTag getPlayingSong() {
        return BroadcastHelper.getPlayingSong();
    }

    public static List<AudioTag> getPendingItems(String name) {
        List<AudioTag> list = new ArrayList();
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                list.addAll(pendingQueue.get(name));
                pendingQueue.remove(name);
            }
        }
        return list;
    }

    public static void putPendingItems(String name, List<AudioTag> tags) {
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                List list = pendingQueue.get(name);
                list.addAll(tags);
                pendingQueue.put(name, tags);
            } else {
                pendingQueue.put(name, tags);
            }
        }
    }

    public static MusicPlayerInfo getPlayerInfo() {
        return broadcastHelper.getPlayerInfo();
    }

    public static void playNextSong(Context applicationContext) {
        BroadcastHelper.playNextSong(applicationContext);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        broadcastHelper.onTerminate(this);
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
    }

    @Override public void onCreate() {
        super.onCreate();
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        broadcastHelper.onCreate(this);

        //initialize ObjectBox is when your app starts

        ObjectBox.init(this);

        StateViewsBuilder
                .init(this)
                .setIconColor(Color.parseColor("#D2D5DA"))
                .addState("error",
                        "No Connection",
                        "Error retrieving information from server.",
                        AppCompatResources.getDrawable(this, R.drawable.ic_server_error),
                        "Retry"
                )

                .addState("search",
                        "No Results Found",
                        "Could not find any results matching search criteria",
                        AppCompatResources.getDrawable(this, R.drawable.ic_search_black_24dp), null)

                .setButtonBackgroundColor(Color.parseColor("#317DED"))
                .setButtonTextColor(Color.parseColor("#FFFFFF"))
                .setIconSize(getResources().getDimensionPixelSize(R.dimen.state_views_icon_size));

        // to detect not expected thread
/*        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
*/
        // TURN OFF log for JAudioTagger
            jAudioTaggerLogger1.setLevel(Level.SEVERE);
            jAudioTaggerLogger2.setLevel(Level.SEVERE);
            //Timber.plant(new DebugTree());
       // } else {
        //    jAudioTaggerLogger1.setLevel(Level.SEVERE);
        //    jAudioTaggerLogger2.setLevel(Level.SEVERE);
       // }

/*
        BlockCanary.install(this, new BlockCanaryContext() {

            public String provideQualifier() {
                return "unknown";
            }

            public String provideUid() {
                return "uid";
            }

            public String provideNetworkType() {
                return "unknown";
            }

            public int provideMonitorDuration() {
                return -1;
            }

            public int provideBlockThreshold() {
                return 1000;
            }

            public int provideDumpInterval() {
                return provideBlockThreshold();
            }

            public String providePath() {
                return "/blockcanary/";
            }

            public boolean displayNotification() {
                return true;
            }

            public boolean zip(File[] src, File dest) {
                return false;
            }

            public void upload(File zippedFile) {
                throw new UnsupportedOperationException();
            }


            public List<String> concernPackages() {
                return null;
            }

            public boolean filterNonConcernStack() {
                return false;
            }

            public List<String> provideWhiteList() {
                LinkedList<String> whiteList = new LinkedList<>();
                whiteList.add("org.chromium");
                return whiteList;
            }

            public boolean deleteFilesInWhiteList() {
                return true;
            }

            public void onBlock(Context context, BlockInfo blockInfo) {

            }
        }).start(); */
    }
}