package apincer.android.mmate.service;

import androidx.annotation.NonNull;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import apincer.music.core.Constants;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.ReplayGainManager;
import apincer.music.core.Settings;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.utils.TagUtils;

import androidx.annotation.OptIn;
import androidx.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import apincer.android.mmate.audio.AudioTelemetryManager;
import apincer.android.mmate.audio.FfmpegPlayback;
import apincer.android.mmate.audio.LevelingAudioRenderer;
import apincer.android.mmate.audio.StreamGain;
import apincer.android.mmate.audio.TrackGain;
import apincer.android.mmate.audio.MediaRouteLog;
import apincer.android.mmate.audio.MusicAudioOutputProvider;
import apincer.android.mmate.audio.LookaheadAudioSink;
import apincer.android.mmate.audio.UsbOutputFormat;

public class AndroidPlayerController {
    private static final String TAG = "AndroidPlayerController";

    private final Context context;

    private String playbackTargetId;
    private PlaybackCallback playbackCallback;
    private ExoPlayer internalExoPlayer;
    private Track nextTrack;
    /** Leveling gain of the stream being output; the PCM output applies it, so the AudioTrack stays at unity. */
    private final StreamGain streamGain = new StreamGain();
    private volatile boolean replayGainPeakProtection;
    // A field, because SharedPreferences holds its listeners weakly
    private final SharedPreferences.OnSharedPreferenceChangeListener levelingListener = (prefs, key) -> {
        if (Constants.PREF_REPLAYGAIN_MODE.equals(key) || Constants.PREF_REPLAYGAIN_PREAMP.equals(key)
                || Constants.PREF_REPLAYGAIN_PREVENT_CLIPPING.equals(key)
                || Constants.PREF_REPLAYGAIN_TARGET.equals(key)
                || Constants.PREF_REPLAYGAIN_UNTAGGED_POLICY.equals(key)
                || Constants.PREF_REPLAYGAIN_TAGS_CHANGED.equals(key)) {
            runOnMainThread(this::relevel);
        }
    };
    private final UsbOutputFormat usbOutputFormat;
    private final MediaRouteLog mediaRouteLog;
    private final apincer.android.mmate.audio.dsp.CarAutoSelect carAutoSelect;


    // How long to wait between progress updates (in milliseconds)
    private static final long PROGRESS_UPDATE_INTERVAL = 1000; // 1 seconds

    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    private MediaItem buildMediaItem(Track track) {
        androidx.media3.common.MediaMetadata metadata = new androidx.media3.common.MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtist())
                .setAlbumTitle(track.getAlbum())
                .build();
                
        File file = new File(track.getPath());
        MediaItem.Builder item = new MediaItem.Builder()
                .setMediaId(String.valueOf(track.getId()))
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(metadata)
                .setTag(new TrackGain(track.getPath(), levelingGain(track.getPath())));
        if (FfmpegPlayback.needsFfmpeg(track.getPath(),
                TagUtils.isALACFile(track) || TagUtils.isAIFFile(track), TagUtils.isDSD(track))) {
            // Served as WAV by FfmpegWavDataSource; the MIME type keeps the extension from steering extractor choice
            item.setUri(FfmpegPlayback.uriFor(file)).setMimeType(MimeTypes.AUDIO_WAV);
        }
        return item.build();
    }

    @OptIn(markerClass = UnstableApi.class)
    public AndroidPlayerController(Context context) {
        this.context = context;
        this.usbOutputFormat = new UsbOutputFormat(context);
        this.usbOutputFormat.start();
        this.mediaRouteLog = new MediaRouteLog(context, usbOutputFormat);
        apincer.android.mmate.audio.dsp.CarDspPreferences.init(context);
        this.carAutoSelect = new apincer.android.mmate.audio.dsp.CarAutoSelect(context, mediaRouteLog::note);
        this.carAutoSelect.start();
        // Speaker Tuning follows the media route, so it listens before the first route check
        this.mediaRouteLog.setRouteListener(routed -> {
            carAutoSelect.onRoute(routed);
            return kotlin.Unit.INSTANCE;
        });
        this.mediaRouteLog.start();
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(levelingListener);
        updatePeakProtection();
        
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
                    AudioTrackAudioOutputProvider baseProvider = new AudioTrackAudioOutputProvider.Builder(context)
                            .build();

                    MusicAudioOutputProvider outputProvider =
                            new MusicAudioOutputProvider(baseProvider, usbOutputFormat);

                    // Float keeps high-resolution sources above 16 bits; IntegerPcmAudioOutput
                    // turns it back into integer PCM before Android sees it. Media3 skips its
                    // speed processor in float mode, so speed requests go to the AudioTrack.
                    AudioSink sink = new DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(true)
                            .setEnableAudioTrackPlaybackParams(true)
                            .setAudioOutputProvider(outputProvider)
                            .build();
                    return new LookaheadAudioSink(sink, () -> replayGainPeakProtection, streamGain::getCurrent);
                }

                @Override
                protected void buildAudioRenderers(
                        Context context,
                        int extensionRendererMode,
                        MediaCodecSelector mediaCodecSelector,
                        boolean enableDecoderFallback,
                        AudioSink audioSink,
                        Handler eventHandler,
                        AudioRendererEventListener eventListener,
                        ArrayList<Renderer> out) {
                    // Extension renderers are off, so this replaces the only audio renderer Media3 builds
                    out.add(new LevelingAudioRenderer(context, getCodecAdapterFactory(), mediaCodecSelector,
                            enableDecoderFallback, eventHandler, eventListener, audioSink, streamGain));
                }
            };

            renderersFactory.setMediaCodecSelector(apincer.android.mmate.audio.FlacDecoderSelector.INSTANCE);

            this.internalExoPlayer = new ExoPlayer.Builder(context, renderersFactory)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(FfmpegPlayback.dataSourceFactory(context)))
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .setSeekParameters(SeekParameters.EXACT)
                    .build();

            this.internalExoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                    MediaItem failed = internalExoPlayer != null ? internalExoPlayer.getCurrentMediaItem() : null;
                    String trackId = failed != null && failed.mediaId.matches("[0-9]+")
                            ? failed.mediaId : "unknown";
                    String details = "playback error: " + error.getErrorCodeName()
                            + " trackId=" + trackId
                            + " positionMs=" + (internalExoPlayer != null ? internalExoPlayer.getCurrentPosition() : -1)
                            + " state=" + (internalExoPlayer != null ? internalExoPlayer.getPlaybackState() : -1);
                    if (error instanceof androidx.media3.exoplayer.ExoPlaybackException) {
                        androidx.media3.exoplayer.ExoPlaybackException exoError =
                                (androidx.media3.exoplayer.ExoPlaybackException) error;
                        details += " type=" + exoError.type + " renderer=" + exoError.rendererName;
                        if (exoError.rendererFormat != null) {
                            details += " mime=" + exoError.rendererFormat.sampleMimeType
                                    + " sampleRate=" + exoError.rendererFormat.sampleRate
                                    + " channels=" + exoError.rendererFormat.channelCount;
                        }
                    }
                    mediaRouteLog.note(details + " causes=" + describeErrorCauses(error));
                    // Without this, one unreadable or missing file stopped playback for the rest of
                    // the queue. Skip to the next track, but stop after a few failures in a row
                    // (e.g. storage removed) instead of racing through the whole queue.
                    // ExoPlayer can report one bad item more than once; skip it only once
                    if (failed != null && failed == lastFailedItem) return;
                    lastFailedItem = failed;
                    consecutivePlayerErrors++;
                    Log.w(TAG, "Playback error (" + consecutivePlayerErrors + " in a row): "
                            + error.getErrorCodeName(), error);
                    if (playbackCallback == null
                            || !ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) return;
                    if (consecutivePlayerErrors <= MAX_CONSECUTIVE_PLAYER_ERRORS) {
                        playbackCallback.onPlaybackCompleted();
                    } else {
                        Log.e(TAG, "Too many playback errors in a row; stopping");
                        consecutivePlayerErrors = 0;
                    }
                }
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        consecutivePlayerErrors = 0;
                        lastFailedItem = null;
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        if (playbackCallback != null) {
                            if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                                playbackCallback.onNaturalTrackEnd();
                            }
                            playbackCallback.onPlaybackCompleted();
                        }
                    }
                }
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        Log.d(TAG, "ExoPlayer: Gapless automatic track transition occurred");
                        if (playbackCallback != null) {
                            if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                                playbackCallback.onNaturalTrackEnd();
                            }
                            if (nextTrack != null) {
                                Track transitioningTrack = nextTrack;
                                nextTrack = null;
                                playbackCallback.onMediaTrackChanged(transitioningTrack);
                            } else {
                                playbackCallback.onPlaybackCompleted();
                            }
                        }
                    }
                }
                @Override
                public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    mediaRouteLog.note("playWhenReady=" + playWhenReady + " reason=" + reason
                            + " (" + playWhenReadyReason(reason) + ")"
                            + " positionMs=" + internalExoPlayer.getCurrentPosition()
                            + " state=" + internalExoPlayer.getPlaybackState());
                    if (playWhenReady) usbOutputFormat.resume();
                    else usbOutputFormat.suspend();
                }
                @Override
                public void onPlaybackSuppressionReasonChanged(int reason) {
                    mediaRouteLog.note("playback suppression=" + reason
                            + " positionMs=" + internalExoPlayer.getCurrentPosition());
                }
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        scheduleProgressUpdate();
                        mediaRouteLog.check("playing");
                    } else {
                        stopProgressUpdate();
                        AudioTelemetryManager.INSTANCE.reset();
                    }
                    if (playbackCallback != null && ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
                        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
                        state.currentState = isPlaying ? 
                                apincer.music.core.playback.PlaybackState.State.PLAYING : 
                                apincer.music.core.playback.PlaybackState.State.PAUSED;
                        // A seek pauses and resumes playback; without the position the slider jumps to 0
                        state.currentPositionSecond = internalExoPlayer.getCurrentPosition() / 1000;
                        playbackCallback.onPlaybackStateChanged(state);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ExoPlayer", e);
        }
    }

    private static final int MAX_CONSECUTIVE_PLAYER_ERRORS = 3;
    private int consecutivePlayerErrors = 0;
    private MediaItem lastFailedItem;

    // Messages can contain private file paths and titles. Keep only failure types and code locations.
    static String describeErrorCauses(Throwable error) {
        StringBuilder details = new StringBuilder();
        int depth = 0;
        while (error != null && depth++ < 8) {
            if (details.length() > 0) details.append(" -> ");
            details.append(error.getClass().getName());
            StackTraceElement[] frames = error.getStackTrace();
            if (frames.length > 0) {
                StackTraceElement frame = frames[0];
                details.append('@').append(frame.getClassName()).append('.')
                        .append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            }
            error = error.getCause();
        }
        if (error != null) details.append(" -> ...");
        return details.toString();
    }

    private static String playWhenReadyReason(int reason) {
        switch (reason) {
            case Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST: return "user request";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS: return "audio focus loss";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY: return "audio becoming noisy";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE: return "remote";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM: return "end of media item";
            default: return "other";
        }
    }

    public void registerCallback(ExternalAndroidPlayer player, PlaybackCallback playbackCallback) {
        unregisterCallback();
        this.playbackCallback = playbackCallback;
        this.playbackTargetId = player.getTargetId();
    }

    public void unregisterCallback() {
        stopProgressUpdate();
        this.playbackCallback = null;
    }

    private final Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (internalExoPlayer != null && internalExoPlayer.isPlaying()) {
                if (playbackCallback != null) {
                    playbackCallback.onPlaybackStateTimeElapsedSeconds(internalExoPlayer.getCurrentPosition() / 1000);
                }
                mediaRouteLog.poll();
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

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mProgressHandler.post(action);
        }
    }

    /** Queues the gapless follower; {@code null} clears it so the current track ends the playlist. */
    public void setNextTrack(Track nextSong) {
        if (nextSong != null && nextSong.getPath() == null) return;
        this.nextTrack = nextSong;
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    try {
                        // Positions are relative to the playing item: auto transitions leave finished
                        // items in front of it, so a fixed index could remove the playing track.
                        int current = internalExoPlayer.getCurrentMediaItemIndex();
                        int count = internalExoPlayer.getMediaItemCount();
                        if (count > current + 1) {
                            internalExoPlayer.removeMediaItems(current + 1, count);
                        }
                        if (current > 0) {
                            internalExoPlayer.removeMediaItems(0, current);
                        }
                        if (nextSong == null) return;
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
                        // Replace the playlist in one step: clearing it first ends the empty
                        // playlist, and STATE_ENDED would advance the queue a second time.
                        internalExoPlayer.setMediaItem(buildMediaItem(song));
                        internalExoPlayer.prepare();
                        internalExoPlayer.play();
                    } catch (Exception e) {
                        Log.e(TAG, "ExoPlayer playback failed", e);
                    }
                }
            });
        }
    }

    public void pause() {
        StringBuilder callers = new StringBuilder();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("apincer.")
                    && !frame.getClassName().equals(AndroidPlayerController.class.getName())) {
                if (callers.length() > 0) callers.append(" <- ");
                callers.append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber());
                if (callers.length() > 400) break;
            }
        }
        mediaRouteLog.note("app pause requested target=" + playbackTargetId + " callers=" + callers);
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.pause();
                }
            });
        }
    }

    public void resume() {
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.play();
                }
            });
        }
    }

    public void seekTo(long positionMs) {
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.seekTo(positionMs);
                }
            });
        }
    }

    public void stopPlaying() {
        mediaRouteLog.note("app stop requested target=" + playbackTargetId);
        AudioTelemetryManager.INSTANCE.reset();
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTargetId)) {
            runOnMainThread(() -> {
                if (internalExoPlayer != null) {
                    internalExoPlayer.stop();
                }
                usbOutputFormat.clear();
            });
        }
    }

    
    public ExoPlayer getInternalExoPlayer() {
        return internalExoPlayer;
    }

    public void release() {
        unregisterCallback();
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(levelingListener);
        runOnMainThread(() -> {
            if (internalExoPlayer != null) {
                internalExoPlayer.release();
                internalExoPlayer = null;
            }
            usbOutputFormat.release();
            mediaRouteLog.release();
            carAutoSelect.release();
        });
    }

    /** The format MusicMate asked this USB DAC to run at, or null when Android's default applies. */
    @Nullable
    public android.media.AudioFormat getUsbOutputFormat(int deviceId) {
        return usbOutputFormat.requestedFormat(deviceId);
    }

    private double levelingGain(String path) {
        return ReplayGainManager.getInstance().leveling(context, path).volume;
    }

    /** Recomputes the queued tracks' leveling after a settings or tag change. */
    private void relevel() {
        updatePeakProtection();
        if (internalExoPlayer == null) return;
        for (int i = 0; i < internalExoPlayer.getMediaItemCount(); i++) {
            MediaItem.LocalConfiguration local = internalExoPlayer.getMediaItemAt(i).localConfiguration;
            if (local != null && local.tag instanceof TrackGain gain) gain.setGain(levelingGain(gain.getPath()));
        }
        streamGain.refresh();
    }

    private void updatePeakProtection() {
        replayGainPeakProtection = !"off".equalsIgnoreCase(Settings.getReplayGainMode(context))
                && Settings.isReplayGainPreventClipping(context);
    }
}
