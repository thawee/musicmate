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
import apincer.music.core.playback.ReplayGainManager;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.provider.MusicFileProvider;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import apincer.android.mmate.audio.AudioLevelProcessor;
import apincer.android.mmate.audio.AudioTelemetryManager;

public class AndroidPlayerController {
    private static final String TAG = "AndroidPlayerController";

    private final Context context;
    private final MediaSessionManager mediaSessionManager;

    private String playbackTargetId;
    private MediaController mediaController;
    private PlaybackCallback playbackCallback;
    private ExoPlayer internalExoPlayer;
    private Track nextTrack;

    private long lastUpdateSongTime;

    // How long to wait between progress updates (in milliseconds)
    private static final long PROGRESS_UPDATE_INTERVAL = 1000; // 1 seconds

    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) return;

            long elapsedMillis = updatePlaybackState(state);
            boolean isPlaying = (state.getState() == PlaybackState.STATE_PLAYING);
            if (isPlaying) {
                // Song started playing, so start our progress poller
                scheduleProgressUpdate();
            } else {
                stopProgressUpdate();
            }

            if (playbackCallback != null && !ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                apincer.music.core.playback.PlaybackState appState = new apincer.music.core.playback.PlaybackState();
                switch (state.getState()) {
                    case PlaybackState.STATE_PLAYING:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
                        break;
                    case PlaybackState.STATE_PAUSED:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.PAUSED;
                        break;
                    case PlaybackState.STATE_STOPPED:
                    case PlaybackState.STATE_NONE:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.STOPPED;
                        break;
                    case PlaybackState.STATE_BUFFERING:
                    case PlaybackState.STATE_CONNECTING:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.BUFFERING;
                        break;
                    case PlaybackState.STATE_ERROR:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.ERROR;
                        if (state.getErrorMessage() != null) {
                            appState.errorMessage = state.getErrorMessage().toString();
                        }
                        break;
                    default:
                        appState.currentState = apincer.music.core.playback.PlaybackState.State.STOPPED;
                        break;
                }
                appState.currentPositionSecond = elapsedMillis / 1000;
                playbackCallback.onPlaybackStateChanged(appState);
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

    @OptIn(markerClass = UnstableApi.class)
    
    private MediaItem buildMediaItem(Track track) {
        androidx.media3.common.MediaMetadata metadata = new androidx.media3.common.MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtist())
                .setAlbumTitle(track.getAlbum())
                .build();
                
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(track.getId()))
                .setUri(Uri.fromFile(new File(track.getPath())))
                .setMediaMetadata(metadata)
                .build();
    }

    @OptIn(markerClass = UnstableApi.class)
    public AndroidPlayerController(Context context, MediaSessionManager mediaSessionManager) {
        this.context = context;
        this.mediaSessionManager = mediaSessionManager;
        
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context) {
                @Nullable
                @Override
                protected AudioSink buildAudioSink(
                        Context context,
                        boolean enableFloatOutput,
                        boolean enableAudioTrackPlaybackParams) {
                    return new DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .setAudioProcessors(new AudioProcessor[] {
                                    new AudioLevelProcessor(null)
                            })
                            .build();
                }
            };

            this.internalExoPlayer = new ExoPlayer.Builder(context, renderersFactory)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .setSeekParameters(SeekParameters.EXACT)
                    .build();

            this.internalExoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (playbackCallback != null) {
                            playbackCallback.onPlaybackCompleted();
                        }
                    }
                }
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        Log.d(TAG, "ExoPlayer: Gapless automatic track transition occurred");
                        if (playbackCallback != null) {
                            if (nextTrack != null) {
                                Track transitioningTrack = nextTrack;
                                nextTrack = null;
                                applyReplayGain(transitioningTrack);
                                playbackCallback.onMediaTrackChanged(transitioningTrack);
                            } else {
                                playbackCallback.onPlaybackCompleted();
                            }
                        }
                    }
                }
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        scheduleProgressUpdate();
                    } else {
                        stopProgressUpdate();
                        AudioTelemetryManager.INSTANCE.reset();
                    }
                    if (playbackCallback != null && ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
                        state.currentState = isPlaying ? 
                                apincer.music.core.playback.PlaybackState.State.PLAYING : 
                                apincer.music.core.playback.PlaybackState.State.PAUSED;
                        playbackCallback.onPlaybackStateChanged(state);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ExoPlayer", e);
        }
    }

    public void registerCallback(ExternalAndroidPlayer player, PlaybackCallback playbackCallback) {
        unregisterCallback();
        this.playbackCallback = playbackCallback;
        this.playbackTargetId = player.getTargetId();
        this.mediaController = getMediaController(player.getTargetId());

        if(this.mediaController != null) {
            Log.d("ExternalPlayer", "registerCallback for " + player.getTargetId());

            // --- 1. Register the callback for future updates ---
            this.mediaController.registerCallback(mediaCallback, mProgressHandler);

            // --- 2. Get the initial metadata ---
            updateMetadata(playbackCallback);

            // --- 3. Check initial playback state ---
            PlaybackState state = this.mediaController.getPlaybackState();
            if (state != null) {
                long elapsedMillis = updatePlaybackState(state);
                if( state.getState() == PlaybackState.STATE_PLAYING) {
                    scheduleProgressUpdate();
                }
                if (!ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                    apincer.music.core.playback.PlaybackState appState = new apincer.music.core.playback.PlaybackState();
                    appState.currentState = (state.getState() == PlaybackState.STATE_PLAYING) ?
                            apincer.music.core.playback.PlaybackState.State.PLAYING :
                            apincer.music.core.playback.PlaybackState.State.PAUSED;
                    appState.currentPositionSecond = elapsedMillis / 1000;
                    playbackCallback.onPlaybackStateChanged(appState);
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
        if (context == null || mediaSessionManager == null || targetId == null) return null;
        try {
            if (!apincer.android.mmate.utils.PermissionUtils.isNotificationListenerEnabled(context)) {
                return null;
            }
            ComponentName notificationListener = new ComponentName(context, MediaNotificationListener.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(notificationListener);
            if (controllers != null) {
                for (MediaController controller : controllers) {
                    if (controller != null && targetId.equals(controller.getPackageName())) {
                        return controller;
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to get media controller for: " + targetId, e);
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
            if (mediaController == null && internalExoPlayer == null) {
                return;
            }

            if (mediaController != null) {
                PlaybackState state = mediaController.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    if (playbackCallback != null) {
                        updatePlaybackState(state);
                    }
                    // Schedule the next update
                    scheduleProgressUpdate();
                } else {
                    stopProgressUpdate();
                }
            } else if (internalExoPlayer != null && internalExoPlayer.isPlaying()) {
                if (playbackCallback != null) {
                    playbackCallback.onPlaybackStateTimeElapsedSeconds(internalExoPlayer.getCurrentPosition() / 1000);
                }
                scheduleProgressUpdate();
            } else {
                stopProgressUpdate();
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

    private MediaController ensureMediaController() {
        if (mediaController == null && playbackTargetId != null && !ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            mediaController = getMediaController(playbackTargetId);
            if (mediaController != null && playbackCallback != null) {
                try {
                    mediaController.registerCallback(mediaCallback, mProgressHandler);
                } catch (Exception ignored) {}
            }
        }
        return mediaController;
    }

    public boolean skipToNext() {
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().skipToNext();
            return true;
        }
        return false;
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mProgressHandler.post(action);
        }
    }

    public void setNextTrack(Track nextSong) {
        if (nextSong == null || nextSong.getPath() == null) return;
        this.nextTrack = nextSong;
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    try {
                        if (internalExoPlayer.getMediaItemCount() > 1) {
                            internalExoPlayer.removeMediaItem(1);
                        }
                        internalExoPlayer.addMediaItem(buildMediaItem(nextSong));
                        Log.d(TAG, "Gapless ExoPlayer: Preloaded next media item: " + nextSong.getTitle());
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to preload next media item in ExoPlayer", e);
                    }
                }
            });
        }
    }

    public void play(Track song) {
        if (song == null || song.getPath() == null) return;
        this.nextTrack = null;

        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            // Use internal ExoPlayer for local/bluetooth playback
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    try {
                        internalExoPlayer.clearMediaItems();
                        internalExoPlayer.setMediaItem(buildMediaItem(song));
                        applyReplayGain(song);
                        internalExoPlayer.prepare();
                        internalExoPlayer.play();
                    } catch (Exception e) {
                        Log.e(TAG, "ExoPlayer playback failed", e);
                    }
                }
            });
        } else if (playbackTargetId != null) {
            Uri songUri = MusicFileProvider.getUriForFile(song.getPath());
            try {
                context.grantUriPermission(playbackTargetId, songUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            if (mediaController != null) {
                try {
                    mediaController.getTransportControls().playFromUri(songUri, null);
                } catch (Exception e) {
                    Log.w(TAG, "playFromUri failed on mediaController, falling back to ACTION_VIEW", e);
                    playInExternalApp(context, song, playbackTargetId);
                }
            } else {
                playInExternalApp(context, song, playbackTargetId);
            }
        }
    }

    public void playInPoweramp(Context context, Track song) {
        playInExternalApp(context, song, ExternalAndroidPlayer.POWERAMP_PACK_NAME);
    }

    public void playInNeutron(Context context, Track song) {
        playInExternalApp(context, song, NEUTRON_MUSIC_PACK_NAME);
    }

    public void playInExternalApp(Context context, Track song, String targetPackage) {
        if (song == null || song.getPath() == null || targetPackage == null) return;
        try {
            Uri uri = MusicFileProvider.getUriForFile(song.getPath());
            String mimeType = apincer.music.core.utils.MimeTypeUtils.getMimeTypeFromPath(song.getPath());
            try {
                context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setPackage(targetPackage);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start external player activity for: " + targetPackage, e);
        }
    }

    public void pause() {
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().pause();
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.pause();
                }
            });
        }
    }

    public void resume() {
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().play();
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.play();
                }
            });
        }
    }

    public void seekTo(long positionMs) {
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().seekTo(positionMs);
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.seekTo(positionMs);
                }
            });
        }
    }

    public boolean skipToPrevious() {
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().skipToPrevious();
            return true;
        }
        return false;
    }

    public void stopPlaying() {
        AudioTelemetryManager.INSTANCE.reset();
        MediaController controller = ensureMediaController();
        if (controller != null) {
            controller.getTransportControls().stop();
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.stop();
                }
            });
        }
    }

    
    public ExoPlayer getInternalExoPlayer() {
        return internalExoPlayer;
    }

    public void release() {
        unregisterCallback();
        runOnMainThread(() -> {
            if (internalExoPlayer != null) {
                internalExoPlayer.release();
                internalExoPlayer = null;
            }
        });
    }

    private void applyReplayGain(Track song) {
        if (internalExoPlayer == null || song == null) return;
        try {
            float volume = ReplayGainManager.getInstance().calculateGainVolume(context, song);
            internalExoPlayer.setVolume(volume);
            Log.d(TAG, "Applied ReplayGain volume: " + volume + " for track: " + song.getTitle());
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply ReplayGain", e);
        }
    }
}
