package apincer.android.mmate.service;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
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
    private MediaMetadata currentMetadata;

    private long lastUpdateSongTime;

    // How long to wait between progress updates (in milliseconds)
    private static final long PROGRESS_UPDATE_INTERVAL = 1000; // 1 seconds

    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    // --- ADD THIS ENTIRE CALLBACK ---
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) return;

           /* if (state.getState() == PlaybackState.STATE_PLAYING) {
                // Song started playing, so start our progress poller
                scheduleProgressUpdate();
                if (playbackCallback != null) {
                    playbackCallback.onPlaybackStateChanged(true);
                }
            } else {
                // Song paused or stopped, so stop our progress poller
                stopProgressUpdate();
                if (playbackCallback != null) {
                    playbackCallback.onPlaybackStateChanged(false);
                }
            } */
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            // This is the "song changed" event
            if (metadata == null) return;

            // Check if it's actually a new track
            if (isNewTrack(metadata)) {
                currentMetadata = metadata; // Store the new metadata

                String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
                long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

                if (playbackCallback != null) {
                    playbackCallback.onMediaTrackChanged(title, artist, album, duration);
                }
            }
        }
    };

    /**
     * Compares new metadata to the stored metadata to see if the track has actually changed.
     */
    private boolean isNewTrack(MediaMetadata newMetadata) {
        if (currentMetadata == null) return true; // It's the first track we're seeing

        String oldTitle = currentMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String newTitle = newMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);

        String oldArtist = currentMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String newArtist = newMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        // Simple check based on title and artist. Handle nulls.
        boolean titlesMatch = (oldTitle == null) ? (newTitle == null) : oldTitle.equals(newTitle);
        boolean artistsMatch = (oldArtist == null) ? (newArtist == null) : oldArtist.equals(newArtist);

        // If title or artist is different, it's a new track.
        return !titlesMatch || !artistsMatch;
    }

    public AndroidPlayerController(Context context, MediaSessionManager mediaSessionManager) {
        this.context = context;
        this.mediaSessionManager = mediaSessionManager;
    }

    public void registerCallback(ExternalAndroidPlayer player, PlaybackCallback playbackCallback) {
        this.mediaController = getMediaController(player.getTargetId());
        this.playbackCallback = playbackCallback;
       /* if(this.mediaController != null) {
            Log.d("ExternalPlayer", "registerCallback");
            //this.mediaController.registerCallback(mediaCallback, mProgressHandler);
            progressUpdate();
            scheduleProgressUpdate();
        } */
        if(this.mediaController != null) {
            Log.d("ExternalPlayer", "registerCallback");

            // --- 1. Register the callback for future updates ---
            this.mediaController.registerCallback(mediaCallback, mProgressHandler);

            // --- 2. Get the initial metadata ---
            readMetadata(playbackCallback);
        }
    }

    private void readMetadata(PlaybackCallback playbackCallback) {
        MediaMetadata metadata = mediaController.getMetadata();
        if (metadata != null) {
            currentMetadata = metadata; // Store initial metadata

            if (playbackCallback != null) {
                String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
                long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                playbackCallback.onMediaTrackChanged(title, artist, album, duration);
                lastUpdateSongTime = System.currentTimeMillis();
            }
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
            mediaController.unregisterCallback(mediaCallback);
            mediaController = null;
        }
        stopProgressUpdate();
        this.playbackCallback = null;
        this.currentMetadata = null;
    }

    private final Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            // Log.d(TAG, "mUpdateProgressRunnable.run");
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
                    if((System.currentTimeMillis() - lastUpdateSongTime) > elapsedMillis) {
                        // read current title
                        readMetadata(playbackCallback);
                    }
                }

                // Schedule the next update
                scheduleProgressUpdate();
            }
        }
    };

    private void scheduleProgressUpdate() {
        // Log.d(TAG, "scheduleProgressUpdate");
        // Stop any previous updates
        stopProgressUpdate();
        // Schedule the new one
        mProgressHandler.postDelayed(mUpdateProgressRunnable, PROGRESS_UPDATE_INTERVAL);
    }

    private void stopProgressUpdate() {
       // Log.d(TAG, "stopProgressUpdate");
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
        if(mediaController != null && song != null) {
            File songFile = new File(song.getPath());

            // Convert the File object to a Uri
            Uri songUri = Uri.fromFile(songFile);
            // Pass the Uri to playFromUri. The second parameter (extras) can be null.
            mediaController.getTransportControls().playFromUri(songUri, null);
        }
    }

    public void pause() {
        if(mediaController != null) {
            mediaController.getTransportControls().pause();
        }
    }

    public void skipToPrevious() {
        if(mediaController != null) {
            mediaController.getTransportControls().skipToPrevious();
        }
    }

    public void stopPlaying() {
        if(mediaController != null) {
            mediaController.getTransportControls().stop();
        }
    }
}
