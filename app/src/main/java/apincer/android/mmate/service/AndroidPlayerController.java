package apincer.android.mmate.service;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackCallback;

public class AndroidPlayerController {
    private static final String TAG = "AndroidPlayerController";

    private final Context context;
    private final MediaSessionManager mediaSessionManager;

    private MediaController mediaController;
    private PlaybackCallback playbackCallback;

    // How long to wait between progress updates (in milliseconds)
    private static final long PROGRESS_UPDATE_INTERVAL = 1000; // 1 seconds

    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    public AndroidPlayerController(Context context, MediaSessionManager mediaSessionManager) {
        this.context = context;
        this.mediaSessionManager = mediaSessionManager;
    }

    public void registerCallback(ExternalAndroidPlayer player, PlaybackCallback playbackCallback) {
        this.mediaController = getMediaController(player.getTargetId());
        this.playbackCallback = playbackCallback;
        if(this.mediaController != null) {
            Log.d("ExternalPlayer", "registerCallback");
            //this.mediaController.registerCallback(mediaCallback, mProgressHandler);
            progressUpdate();
            scheduleProgressUpdate();
        }
    }

    private MediaController getMediaController(String targetId) {
        try {
            ComponentName notificationListener = new ComponentName(context, MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(notificationListener);
            for (MediaController controller : controllers) {
                String packageName = controller.getPackageName();
                if(packageName.equals(targetId)) return controller;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing notification listener permission", e);
        }
        return null;
    }

    public void unregisterCallback() {
        if(mediaController != null) {
            // mediaController.unregisterCallback(mediaCallback);
            mediaController = null;
        }
        stopProgressUpdate();
        this.playbackCallback = null;
    }

    private final Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            // Log.d("ExternalPlayer", "mUpdateProgressRunnable.run");
            if (mediaController == null) {
                return;
            }

            PlaybackState state = mediaController.getPlaybackState();
            // Log.d("ExternalPlayer", "mUpdateProgressRunnable.state: "+ state);
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                // --- This is the key calculation ---
                long lastPosition = state.getPosition();
                long lastUpdateTime = state.getLastPositionUpdateTime();
                float playbackSpeed = state.getPlaybackSpeed();
                if(playbackCallback != null) {
                    // Calculate current position based on last update time
                    long timeSinceUpdate = SystemClock.elapsedRealtime() - lastUpdateTime;
                    long elapsedMillis = lastPosition + (long) (timeSinceUpdate * playbackSpeed);
                    playbackCallback.onPlaybackStateTimeElapsedSeconds(elapsedMillis/1000);
                }

                // Schedule the next update
                scheduleProgressUpdate();
            }
        }
    };

    private void scheduleProgressUpdate() {
        // Log.d("ExternalPlayer", "scheduleProgressUpdate");
        // Stop any previous updates
        stopProgressUpdate();
        // Schedule the new one
        mProgressHandler.postDelayed(mUpdateProgressRunnable, PROGRESS_UPDATE_INTERVAL);
    }

    private void stopProgressUpdate() {
        //  Log.d("ExternalPlayer", "stopProgressUpdate");
        mProgressHandler.removeCallbacks(mUpdateProgressRunnable);
    }

    private void progressUpdate() {
        if(mediaController ==null) return;

        MediaMetadata metadata = mediaController.getMetadata();
        if(metadata ==null) return;

        String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
        long duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);

        if(playbackCallback != null) {
            playbackCallback.onMediaTrackChanged(title, artist, album, duration);
        }

    }

    public void skipToNext() {
        if(mediaController != null) {
            mediaController.getTransportControls().skipToNext();
        }
    }

    public void play(MediaTrack song) {
        if(mediaController != null) {
            //play  song from song.getPath()
            //mediaController.getTransportControls()
        }
    }

    public void pause() {
    }

    public void playPrevious() {
    }

    public void stopPlaying() {

    }
}
