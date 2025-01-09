package apincer.android.mmate;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.Constants.DLNA_DEFAULT_COVERART_FILE;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;

import com.arthenica.ffmpegkit.FFmpegKitConfig;
//import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.notification.NotificationId;
import apincer.android.mmate.player.PlayerControl;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.OrmLiteHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.worker.MusicMateExecutors;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.utils.FileUtils;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

    private static MusixMateApp INSTANCE;
    private SearchCriteria criteria;

    public static MusixMateApp getInstance() {
        return INSTANCE;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";

    private static final PlayerControl playerControl = new PlayerControl();

    private static final Map<String, List<MusicTag>> pendingQueue = new HashMap<>();

   // private static final List<ByteBuffer> NO_IMAGE_COVERS = new ArrayList<>();

    public static List<MusicTag> getPendingItems(String name) {
        List<MusicTag> list = new ArrayList<>();
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                list.addAll(pendingQueue.get(name));
                pendingQueue.remove(name);
            }
        }
        return list;
    }

    public static void putPendingItems(String name, List<MusicTag> tags) {
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                List<MusicTag> list = pendingQueue.get(name);
                assert list != null;
                list.addAll(tags);
                pendingQueue.put(name, tags);
            } else {
                pendingQueue.put(name, tags);
            }
        }
    }

    public static PlayerControl getPlayerControl() {
        return playerControl;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        MediaServerService.stopMediaServer(this);
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        MusicMateExecutors.getInstance().shutdown();
    }


    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        LogHelper.initial();
        LogHelper.setSLF4JOn();
        //CrashReporter.initialize(this);
        //initNoImageCovers();

        // initialize thread executors
        MusicMateExecutors.getInstance();

        // turn off ffmpeg-kit log
        FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_ERROR);

        createNotificationChannel();

        //initialize ObjectBox is when your app starts
        //ObjectBox.init(this);
        if(Settings.isEnableMediaServer(getApplicationContext())) {
            MediaServerService.startMediaServer(this);
        }

        StateViewsBuilder
                .init(this)
                .setIconColor(Color.parseColor("#D2D5DA"))
                .addState("error",
                        "No Connection",
                        "Error retrieving information from server.",
                        AppCompatResources.getDrawable(this, sakout.mehdi.StateViews.R.drawable.ic_server_error),
                        "Retry"
                )

                .addState("search",
                        "No Results Found",
                        "Could not find any results matching search criteria",
                        AppCompatResources.getDrawable(this, R.drawable.ic_search_black_24dp), null)

                .setButtonBackgroundColor(Color.parseColor("#317DED"))
                .setButtonTextColor(Color.parseColor("#FFFFFF"))
                .setIconSize(getResources().getDimensionPixelSize(R.dimen.state_views_icon_size));

        // setup default cover art
        try {
            File pathFile = new File(getApplicationContext().getExternalCacheDir(), CoverArtProvider.COVER_ARTS);
            File defaultCoverart = new File(pathFile, DEFAULT_COVERART_FILE);
            FileUtils.createParentDirs(defaultCoverart);
            InputStream in = ApplicationUtils.getAssetsAsStream(getApplicationContext(), "no_cover2.png");
            Files.copy(in, defaultCoverart.toPath(), REPLACE_EXISTING);

            defaultCoverart = new File(pathFile, DLNA_DEFAULT_COVERART_FILE);
            FileUtils.createParentDirs(defaultCoverart);
            in = ApplicationUtils.getAssetsAsStream(getApplicationContext(), "no_cover2.png");
            Files.copy(in, defaultCoverart.toPath(), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*
        // to detect not expected thread
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
         */

        // Do it on main process
       // BlockCanary.install(this, new AppBlockCanaryContext()).start();
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

        // scan music files
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        if(Settings.checkDirectoriesSet(getApplicationContext())) {
            ScanAudioFileWorker.startScan(getApplicationContext());
        }
    }

    /*
Provides the SQLite Helper Object among the application
 */
    public OrmLiteHelper getOrmLite() {
        return OpenHelperManager.getHelper(this, OrmLiteHelper.class);
    }

    public void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    public void createGroupNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                .setSilent(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle("MusicMate")
                .setContentText("")
                .setContentIntent(pendingIntent);
        notificationManager.notify(NotificationId.MAIN.getId(), mBuilder.build());
    }

    public void cancelGroupNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager.getActiveNotifications().length == 1) {
            mNotificationManager.cancel(NotificationId.MAIN.getId());
        }
    }

    public void setSearchCriteria(SearchCriteria criteria) {
        this.criteria = criteria;
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    public void clearCaches() {
        File dir = getApplicationContext().getExternalCacheDir();
        // /tmp/
       // CoverArtProvider.COVER_ARTS
        // /Icons/
        FileUtils.deleteDirectory(new File(dir, "/tmp/"));
        FileUtils.deleteDirectory(new File(dir, "/Icons/"));
        FileUtils.deleteDirectory(new File(dir, CoverArtProvider.COVER_ARTS));
       // ToastHelper.showActionMessage(getApplicationContext(), "", "");
    }
}