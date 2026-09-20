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

            updatePlaybackState(state);
            if (state.getState() == PlaybackState.STATE_PLAYING) {
                // Song started playing, so start our progress poller
                scheduleProgressUpdate();
            } else {
                stopProgressUpdate();
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

    public boolean skipToNext() {
        if(mediaController != null) {
            mediaController.getTransportControls().skipToNext();
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

        if (NEUTRON_MUSIC_PACK_NAME.equals(playbackTargetId)) {
            playInNeutron(context, song);
        } else if (ExternalAndroidPlayer.POWERAMP_PACK_NAME.equals(playbackTargetId)) {
            playInPoweramp(context, song);
        } else {
            Uri songUri = MusicFileProvider.getUriForFile(song.getPath());
            if (mediaController != null) {
                mediaController.getTransportControls().playFromUri(songUri, null);
            } else if (playbackTargetId != null) {
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
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(songUri, "audio/*");
                    intent.setPackage(playbackTargetId);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start external player activity", e);
                    }
                }
            }
        }
    }

    public void playInPoweramp(Context context, Track song) {
        if (song == null || song.getPath() == null) return;

        Uri uri = MusicFileProvider.getUriForFile(song.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "audio/*");
        intent.setPackage(ExternalAndroidPlayer.POWERAMP_PACK_NAME);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Poweramp", e);
        }
    }



    public void playInNeutron(Context context, Track song) {
        if (song == null || song.getPath() == null) return;

        Uri uri = MusicFileProvider.getUriForFile(song.getPath());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "audio/*");
        intent.setPackage("com.neutroncode.mp");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    public void pause() {
        if (mediaController != null) {
            mediaController.getTransportControls().pause();
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.pause();
                }
            });
        }
    }

    public void resume() {
        if (mediaController != null) {
            mediaController.getTransportControls().play();
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.play();
                }
            });
        }
    }

    public void seekTo(long positionMs) {
        if (mediaController != null) {
            mediaController.getTransportControls().seekTo(positionMs);
        } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.seekTo(positionMs);
                }
            });
        }
    }

    public boolean skipToPrevious() {
        if(mediaController != null) {
            mediaController.getTransportControls().skipToPrevious();
            return true;
        }
        return false;
    }

    public void stopPlaying() {
        AudioTelemetryManager.INSTANCE.reset();
        if (mediaController != null) {
            mediaController.getTransportControls().stop();
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
