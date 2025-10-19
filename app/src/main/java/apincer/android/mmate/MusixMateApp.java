package apincer.android.mmate;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;

import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;

import javax.inject.Inject;

import apincer.android.mmate.service.MediaServerManager;
import apincer.android.mmate.service.PlaybackServiceImpl;
import apincer.music.core.NotificationId;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.repository.PlaylistRepository;
import apincer.android.mmate.ui.MainActivity;
import apincer.music.core.utils.LogHelper;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import dagger.hilt.android.HiltAndroidApp;
import sakout.mehdi.StateViews.StateViewsBuilder;

@HiltAndroidApp
public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";

  //  private MediaServerHubService mediaServerService;

    @Inject
    FileRepository fileRepos;
    @Inject
    TagRepository tagRepos;

    @Override
    public void onTerminate() {
        super.onTerminate();

        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        MusicMateExecutors.getInstance().shutdown();
    }

    @Override public void onCreate() {
        super.onCreate();
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        LogHelper.initial();
        LogHelper.setSLF4JOn();
        CrashReporter.initialize(getApplicationContext());

        // initialize thread executors
        MusicMateExecutors.getInstance();

        startMusicScan();

        startMediaServer();

        // Call the static method to start the service from a valid context
        PlaybackServiceImpl.startPlaybackService(this);

        PlaylistRepository.initPlaylist(this);

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

    }

    private void startMediaServer() {
        MediaServerManager manager = new MediaServerManager(this);
        manager.startServer();
    }

    // Add this to your MusixMateApp class
    public void startMusicScan() {
        // Clean up any pending work requests
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        if(Settings.checkDirectoriesSet(getApplicationContext())) {
            // On normal startup, do a quick incremental scan
            Log.i(TAG, "Normal startup, performing incremental music scan");
            ScanAudioFileWorker.startScan(getApplicationContext());
        } else {
            Log.w(TAG, "Music scan skipped - no directories configured");
        }
    }

    @Deprecated
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

    @Deprecated
    public void cancelGroupNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager.getActiveNotifications().length == 1) {
            mNotificationManager.cancel(NotificationId.MAIN.getId());
        }
    }

    // use by worker
    public FileRepository getFileRepository() {
        return fileRepos;
    }

    // use by worker
    public TagRepository getTagRepository() {
        return tagRepos;
    }
}