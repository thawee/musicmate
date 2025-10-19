package apincer.android.mmate.service;

import static apincer.android.mmate.playback.ExternalPlayer.SUPPORTED_PLAYERS;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import javax.inject.Inject;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.StringUtils;
import dagger.hilt.android.AndroidEntryPoint;

@Deprecated
@AndroidEntryPoint
public class ExternalPlayerListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    private static final long POLLING_INTERVAL_MS = 1000; // 1 second

    // Handler to schedule the repeating task, Use the constructor that takes a Looper
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    // Store the controller and package name of the media app we are tracking
    private MediaController activeMediaController;

    // Constants for notification extras
    private static final String EXTRA_TITLE = "android.title";
    private static final String EXTRA_TEXT = "android.text";

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    @Inject
    TagRepository tagRepos;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            PlaybackServiceImpl.PlaybackServiceBinder binder = (PlaybackServiceImpl.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
            pollingRunnable = new Runnable() {
                @Override
                public void run() {
                    // Ensure we still have an active controller
                    if (isPlaybackServiceBound && activeMediaController != null) {
                        PlaybackState playbackState = activeMediaController.getPlaybackState();
                        if (playbackState != null && playbackState.getState() == PlaybackState.STATE_PLAYING) {
                            long elapsedSeconds = getElapsedTime(playbackState);
                            apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
                            state.currentTrack = playbackService.getNowPlayingSong();
                            state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
                            state.currentPositionSecond = elapsedSeconds;
                            playbackService.onPlaybackStateChanged(state);

                            // Re-schedule the runnable to run again after the interval
                            handler.postDelayed(this, POLLING_INTERVAL_MS);
                        }
                    }
                }
            };
          //  Log.i(TAG, "PlaybackService bound successfully.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
           // Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    public ExternalPlayerListener() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Bind to the PlaybackService as soon as this service is created
        Intent intent = new Intent(this, PlaybackServiceImpl.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
      //  Log.d(TAG, "NotificationListener service being destroyed");
        if(isPlaybackServiceBound) {
            unbindService(serviceConnection);
        }

        // Let the parent handle unbinding - don't call unbindService yourself
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
       // Log.d(TAG, "NotificationListener service connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
      //  Log.d(TAG, "NotificationListener service disconnected");

        // Attempt to reconnect
        requestRebind(new ComponentName(this, ExternalPlayerListener.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Check if this is from a supported music player
        if (!SUPPORTED_PLAYERS.contains(packageName)) {
            return;
        }

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) {
            Log.w(TAG, "Notification extras is null for package: " + packageName);
            return;
        }

        try {
            // Extract title and artist information
            String title = extras.getString(EXTRA_TITLE);
            if (title == null) {
                return;
            }
           // long elapsedTime = 0;
            title = StringUtils.removeTrackNo(title);

            // Get artist information
            CharSequence textSequence = extras.getCharSequence(EXTRA_TEXT);
            String artist = textSequence != null ? textSequence.toString() : "";
            if(playbackService != null) {
               /// PlaybackTarget currentPlayer = ExternalPlayer.Factory.create(getBaseContext(), packageName,null);
                MusicTag currentSong = tagRepos.findMediaItem(title, artist,"");

                MediaSession.Token token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token.class);
                if (token != null) {
                    // Stop any previous polling
                    stopPolling();

                    // We have a token, now we can get the controller
                   // activeMediaController = new MediaController(getApplicationContext(), token);
                    //PlaybackState playbackState = activeMediaController.getPlaybackState();
    //
                    //if (playbackState != null) {
                    //    elapsedTime = getElapsedTime(playbackState);
                    //}
                    startPolling();
                }

                // Publish the now playing information

               /// playbackService.notifyNewTrackPlaying(currentPlayer, currentSong);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing notification from " + packageName, e);
        }
    }

    private void startPolling() {
        // Remove any old callbacks and start the new one immediately
        handler.removeCallbacks(pollingRunnable);
        handler.post(pollingRunnable);
    }

    private void stopPolling() {
        handler.removeCallbacks(pollingRunnable);
        activeMediaController = null;
    }

    private long getElapsedTime(PlaybackState playbackState) {
        long lastPosition = playbackState.getPosition();
        float playbackSpeed = playbackState.getPlaybackSpeed();
        long elapsedMillis;

        // Check if the media is currently playing
        if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
            // Calculate current position based on last update time
            long timeSinceUpdate = SystemClock.elapsedRealtime() - playbackState.getLastPositionUpdateTime();

            elapsedMillis = lastPosition + (long) (timeSinceUpdate * playbackSpeed);
        } else {
            // If not playing, the position is static
            elapsedMillis = lastPosition;
        }

        return elapsedMillis / 1000; // second
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if(isPlaybackServiceBound) {
            PlaybackTarget currentPlayer = playbackService.getPlayer();
            if (sbn != null && currentPlayer != null && sbn.getPackageName().equals(currentPlayer.getTargetId())) {
                Log.d(TAG, "Stopping polling for: " + sbn.getPackageName());
                stopPolling();
            }
        }
    }

    @Deprecated
    public static void setupNotificationListener(Context context) {
        ComponentName componentName = new ComponentName(context, ExternalPlayerListener.class);
        NotificationListenerService.requestRebind(componentName);
    }
}
