package apincer.android.mmate.service;

import static apincer.music.core.playback.ExternalAndroidPlayer.NEUTRON_MUSIC_PACK_NAME;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import apincer.music.core.provider.MusicFileProvider;

public class AndroidPlayerController {
    private static final String TAG = "AndroidPlayerController";

    private final Context context;
    private final MediaSessionManager mediaSessionManager;

    private String playbackTargetId;
    private MediaController mediaController;
    private PlaybackCallback playbackCallback;

    private long lastUpdateSongTime;

    // How long to wait between progress updates (in milliseconds)
    private static final long PROGRESS_UPDATE_INTERVAL = 1000; // 1 seconds

    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) return;

            updatePlaybackState(state);
            if (state.getState() == PlaybackState.STATE_PLAYING) {
                // Song started playing, so start our progress poller
                scheduleProgressUpdate();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            // This is the "song changed" event
            if (metadata == null) return;

            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

            if (playbackCallback != null) {
                playbackCallback.onMediaTrackChanged(title, artist, album, duration);
                lastUpdateSongTime = System.currentTimeMillis();
            }
        }
    };

    public AndroidPlayerController(Context context, MediaSessionManager mediaSessionManager) {
        this.context = context;
        this.mediaSessionManager = mediaSessionManager;
    }

    public void registerCallback(ExternalAndroidPlayer player, PlaybackCallback playbackCallback) {
        this.mediaController = getMediaController(player.getTargetId());
        this.playbackCallback = playbackCallback;
        this.playbackTargetId = player.getTargetId();

        if(this.mediaController != null) {
            Log.d("ExternalPlayer", "registerCallback");

            // --- 1. Register the callback for future updates ---
            this.mediaController.registerCallback(mediaCallback, mProgressHandler);

            // --- 2. Get the initial metadata ---
            updateMetadata(playbackCallback);

            // --- 3. Check initial playback state ---
            PlaybackState state = this.mediaController.getPlaybackState();
            if (state != null) {
                updatePlaybackState(state);
                if( state.getState() == PlaybackState.STATE_PLAYING) {
                    scheduleProgressUpdate();
                }
            }
        }
    }

    private long updatePlaybackState(PlaybackState state) {
        if(playbackCallback == null) return 0;

        long lastPosition = state.getPosition();
        long lastUpdateTime = state.getLastPositionUpdateTime();
        float playbackSpeed = state.getPlaybackSpeed();
        long timeSinceUpdate = SystemClock.elapsedRealtime() - lastUpdateTime;
        long elapsedMillis = lastPosition + (long) (timeSinceUpdate * playbackSpeed);
        playbackCallback.onPlaybackStateTimeElapsedSeconds(elapsedMillis/1000);
        return elapsedMillis;
    }

    private void updateMetadata(PlaybackCallback playbackCallback) {
        MediaMetadata metadata = mediaController.getMetadata();
        if (metadata != null) {
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
                if(playbackCallback != null) {
                    long elapsedMillis = updatePlaybackState(state);
                    if((System.currentTimeMillis() - lastUpdateSongTime) > elapsedMillis) {
                        // read current title
                        updateMetadata(playbackCallback);
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

    public void skipToNext() {
        if(mediaController != null) {
            mediaController.getTransportControls().skipToNext();
        }
    }

    public void play(MediaTrack song) {
        if(NEUTRON_MUSIC_PACK_NAME.equals(playbackTargetId)) {
            playInNeutron(context, song);
        }else {
            if (mediaController != null && song != null) {
                File songFile = new File(song.getPath());

                // Convert the File object to a Uri
                Uri songUri = Uri.fromFile(songFile);
                // Pass the Uri to playFromUri. The second parameter (extras) can be null.
                mediaController.getTransportControls().playFromUri(songUri, null);
            }
        }
    }



    public void playInNeutron(Context context, MediaTrack song) {
        if (song == null || song.getPath() == null) return;

        /*
        // Use Neutron's specific broadcast action
        Intent intent = new Intent("com.neutroncode.mp.PLAY");

        // Target both the paid and evaluation versions
        intent.setPackage("com.neutroncode.mp");

        Uri uri = MusicFileProvider.getUriForFile(song.getPath());
       // File file = new File(song.getPath());
       // Uri uri = Uri.fromFile(file);

        // Set the data and the type
        intent.setDataAndType(uri, "audio/*");

        // Essential flags for Android 7+ and background execution
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        context.sendBroadcast(intent); */

        Uri uri = MusicFileProvider.getUriForFile(song.getPath());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "audio/*");
        intent.setPackage("com.neutroncode.mp");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
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
