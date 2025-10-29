package apincer.android.mmate;

import static apincer.android.mmate.service.MusicMateServiceImpl.CHANNEL_ID;
import static apincer.music.core.Constants.COVER_ARTS;
import static apincer.music.core.Constants.DEFAULT_COVERART;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.work.WorkManager;

import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;

import java.io.IOException;

import javax.inject.Inject;

import apincer.android.mmate.service.MediaServerManager;
import apincer.music.core.Constants;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.utils.LogHelper;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import dagger.hilt.android.HiltAndroidApp;
import sakout.mehdi.StateViews.StateViewsBuilder;

@HiltAndroidApp
public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

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

        // must create notification channel for foreground services
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel(notificationManager);

        // initialize thread executors
        MusicMateExecutors.getInstance();

        //prepare defaultAssets
        initDefaultAssets();

        // start music scan
        startMusicScan();

        startMediaServer();

        // Call the static method to start the service from a valid context
        MediaServerManager serverManager = new MediaServerManager(this);
        serverManager.startServer();

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

    private void createNotificationChannel(NotificationManager notificationManager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                Constants.getPresentationName(),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Manages media");
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initDefaultAssets() {
        String coverFile = COVER_ARTS +DEFAULT_COVERART;
        try {
            ApplicationUtils.copyFileToAndroidCacheDir(this, DEFAULT_COVERART, coverFile);
        } catch (IOException e) {
            Log.e(TAG, "cannot prepare initial assets", e);
        }
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

    // use by worker
    public FileRepository getFileRepository() {
        return fileRepos;
    }

    // use by worker
    public TagRepository getTagRepository() {
        return tagRepos;
    }
}