package apincer.android.mmate.service;

import static apincer.android.mmate.service.MediaNotificationBuilder.updateNotification;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import apincer.music.core.Constants;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.service.spi.MusicMateServiceBinder;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * Media session monitoring service that:
 * 1. Discovers all active media sessions (external music player + streaming renderers)
 * 2. Controls external players (UAPP, Neutron Music, Hiby Music, etc.) and streaming players (RopieeeXL, WiiM)
 * 3. Bridges external playback state to streaming player targets
 * 4. Manages playing queue for streaming player playback
 *
 * NO internal ExoPlayer - only monitors and controls external apps and streaming targets.
 */
@AndroidEntryPoint
public class MusicMateServiceImpl extends Service implements PlaybackService {
    private static final String TAG = "MusicMateServiceImpl";

    enum RUNNING_MODE {MONITOR, CONTROL}
    public static final String CHANNEL_ID = "musicmate_service_channel";
    public static final int SERVICE_ID = 1;

    public static final String ACTION_PLAY_PAUSE = "apincer.musicmate.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "apincer.musicmate.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "apincer.musicmate.ACTION_PREVIOUS";
    public static final String ACTION_STOP = "apincer.musicmate.ACTION_STOP";

    public static final String SERVER_STATUS_NO_WIFI = "No Network"; // Red circle for Offline
    // Used as a prefix for the dynamic SSID
    public static final String SERVER_STATUS_ONLINE_PREFIX = "Online"; // Green circle for Online
    public static final String SERVER_STATUS_OFFLINE = "Offline"; // Red circle for Offline

    public static final String SERVER_STATUS_CAST_PREFIX = "Cast"; // Green circle for Online

    @Inject
    TagRepository tagRepos;

    @Inject
    MediaServerHub mediaHub;

    @Inject
    QueueManager queueManager;

    private AndroidPlayerController androidPlayer;

    private MediaSessionManager mediaSessionManager;

    private final BehaviorSubject<apincer.music.core.playback.PlaybackState> playbackStateSubject =
            BehaviorSubject.createDefault(new apincer.music.core.playback.PlaybackState());
    private final BehaviorSubject<Optional<Track>> currentTrackSubject =
            BehaviorSubject.createDefault(Optional.empty());
    private final BehaviorSubject<Optional<PlaybackTarget>> currentPlayerSubject =
            BehaviorSubject.createDefault(Optional.empty());
    private final BehaviorSubject<List<Track>> playingQueueSubject =
            BehaviorSubject.createDefault(new ArrayList<>());

   //TODO: change to playing/monitor mode
    private RUNNING_MODE runningMode = RUNNING_MODE.MONITOR;
    private String controlledPlayerTargetId;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "MM:QueueTimer"));
    private ScheduledFuture<?> nextTrackTask;

    private volatile long lastPreloadedTrackId = -1;
    private volatile long lastPlaybackTrackId = -1;

    // The Service is now the single source of truth for its status.
    private final MutableLiveData<MediaServerHub.ServerStatus> statusLiveData = new MutableLiveData<>(MediaServerHub.ServerStatus.STOPPED);

    private final PlaybackCallback playbackCallback = new PlaybackCallback() {

        @Override
        public void onMediaTrackChanged(Track metadata) {
            MusicMateServiceImpl.this.onMediaTrackChanged(metadata);
        }

        @Override
        public void onMediaTrackChanged(String title, String artist, String album, long duration) {
            Track song = tagRepos.findMusic(title, artist, album);
            if(song != null) {
                onMediaTrackChanged(song);
            }
        }

        @Override
        public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
            MusicMateServiceImpl.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
            onPlaybackStateElapsedTime(elapsedSeconds);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionChangeListener =
            controllers -> {
               // Log.d(TAG, "Active sessions changed: " + controllers.size());
                updateAvailableExternalPlayers(controllers);
            };

    public MusicMateServiceImpl( ) {
    }

    private PlaybackTarget getActivePlayer() {
        return currentPlayerSubject.getValue().orElse(null);
    }

    private void updateAvailableExternalPlayers(List<MediaController> controllers) {
        // Remove all existing external Players
        addLocalPlaybackTarget(null, true);

        // Add external media session targets
        for (MediaController controller : controllers) {
            String packageName = controller.getPackageName();
           // String sessionTag = controller.getTag();
            PlaybackTarget player = ExternalAndroidPlayer.Factory.create(getApplicationContext(), packageName);
            addLocalPlaybackTarget(player, false);
        }

        // Streaming players will be added via registerStreamingPlayer()
        Log.d(TAG, "Updated available targets: " + getPlaybackTargets().size());
    }

    // ==================== Service Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Only call it ONCE based on the Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_ID, createInitialNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(SERVICE_ID, createInitialNotification());
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e instanceof ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Service bound in background. Cannot elevate to Foreground. Running silently.");
            } else if (e instanceof SecurityException) {
                Log.w(TAG, "Lacking permissions for foreground start. Running silently.");
            } else {
                Log.e(TAG, "Unexpected error starting foreground service", e);
            }
        }

        getStatusLiveData().observeForever(status -> updateNotification(getApplicationContext(),null, null, mediaHub.getStatus().getValue(), tagRepos.getTotalSongs()));

        // external player controller
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        androidPlayer = new AndroidPlayerController(getApplicationContext(), mediaSessionManager);
        ComponentName notificationListener = new ComponentName(this, MediaNotificationListener.class);
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(notificationListener);
            // initial with external player
            updateAvailableExternalPlayers(controllers);
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionChangeListener, notificationListener);

            // Load queue from database
            if(queueManager != null) {
                playingQueueSubject.onNext(queueManager.getSongs());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing notification listener permission", e);
        }

        initWebUIAssets(this);

        Optional<PlaybackTarget> bestChoice = autoSelectBestPlayer();
        if (bestChoice.isPresent()) {
            // We found a player, now switch to it
            PlaybackTarget selectedPlayer = bestChoice.get();
            switchPlayer(selectedPlayer, false); // or false, depending on your 'controlled' logic
        } else {
            // No players are available, maybe switch to local playback or show a message
            Log.w(TAG, "No players available to auto-select.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                // --- Server Actions ---
                case MediaServerManager.ACTION_START_SERVER:
                    startServers();
                    break;
                case MediaServerManager.ACTION_STOP_SERVER:
                    stopServers();
                    break;

                // --- Notification Media Control Actions ---
                case ACTION_PLAY_PAUSE:
                    // Add your logic to toggle play/pause for the current player
                    handlePlayPause();
                    break;
                case ACTION_NEXT:
                    // Add your logic to skip to the next track
                    skipToNextInQueue();
                    break;
                case ACTION_PREVIOUS:
                    // Add your logic to go to the previous track
                    skipToPrevious();
                    break;
                case ACTION_STOP:
                    // Add your logic to stop playback completely
                    // This is often triggered when the notification is swiped away
                    stopPlaying();
                    break;

            }
        }
        // Change this from START_STICKY to START_NOT_STICKY
        return START_NOT_STICKY;
    }

    private void handlePlayPause() {
        if (isPlaying()) {
            pausePlayer();
        } else {
            // Find current track and resume
            currentPlayerSubject.getValue().ifPresent(target -> playSong(getNowPlayingSong()));
        }
    }

    public void startServers() {
      //  if (mediaServer.isInitialized()) return;

        if (!NetworkUtils.isWifiConnected(this)) {
            statusLiveData.postValue(MediaServerHub.ServerStatus.ERROR);
            Log.d(TAG, TAG+" - Error, Required WiFi network");
            return;
        }

        // --- ACQUIRE RESOURCES ---
        // Wake lock keeps the CPU from sleeping. A timeout is used as a safeguard.
       // wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusixMate:MediaServerWakeLock");
       // wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        // WifiLock keeps the Wi-Fi radio from turning off, crucial for streaming.
       // wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MusixMate:WifiLock");
      //  wifiLock.setReferenceCounted(false);
       // wifiLock.acquire();

        // MulticastLock is required for device discovery (DLNA/UPnP).
       // multicastLock = wifiManager.createMulticastLock("MusixMate:MulticastLock");
       // multicastLock.setReferenceCounted(false);
       // multicastLock.acquire();
       // Log.d(TAG, "CPU, Wi-Fi, and Multicast locks acquired.");

        // --- START SERVICES ---
        //showNotification(null);
        mediaHub.start();
        //startNetworkMonitoring();

        // Report that the server is now running.
        statusLiveData.postValue(MediaServerHub.ServerStatus.RUNNING);
    }

    public void stopServers() {
       // if (!mediaServer.isInitialized()) return;

        // --- RELEASE RESOURCES ---
        mediaHub.stop();

        // Report that the server has stopped.
        statusLiveData.postValue(MediaServerHub.ServerStatus.STOPPED);
    }

    // It just creates the *first* notification shown before anything is loaded.
    private Notification createInitialNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Monitoring media sessions")
                .setSmallIcon(apincer.android.mmate.R.drawable.ic_notification_default)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }


    private boolean isPlaying() {
        return playbackStateSubject.getValue().currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
    }

    @Override
    public void onDestroy() {
        // 1. Cancel the pending "Next Track" timer
        if (nextTrackTask != null) {
            nextTrackTask.cancel(true);
        }

        // 2. Shut down the scheduler
        scheduler.shutdownNow();

        // Ensure everything is cleaned up if the service is destroyed.
        stopServers();

        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionChangeListener);
        }
        deactivatePlayer(getActivePlayer());
        super.onDestroy();
    }

    private void deactivatePlayer(PlaybackTarget player) {
        if(player != null) {
            if (player.isStreaming()){
                mediaHub.playerDeactivate(player.getTargetId());
            }else {
                androidPlayer.unregisterCallback();
            }
        }
    }

    // ==================== Playback Control (Unified) ====================

    @Override
    public void playSong(Track song) {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            runningMode = RUNNING_MODE.CONTROL;
            if (isControllable(playbackTarget)) {
                internalPlayOnDMRPlayer(playbackTarget, song);
            } else {
                androidPlayer.play(song);
            }
        });
    }

    @Override
    public void skipToNextInQueue() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalSkipToNextOnDMRPlayer();
            } else {
                // external player
                androidPlayer.skipToNext();
            }
        });
    }

    private void internalSkipToNextOnDMRPlayer() {
        resetGaplessState();

        // get next song from queuemanager
        queueManager.setCurrentTrack(getNowPlayingSong());
        Track song = queueManager.getNextTrack();
        if(song != null) {
            mediaHub.playerPlaySong(song);
        }
    }

    @Override
    public void skipToPrevious() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalPreviousOnDMRPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.skipToPrevious();
            }
        });
    }

    private void internalPreviousOnDMRPlayer(PlaybackTarget playbackTarget) {
    }

    @Override
    public void pausePlayer() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                InternalPauseDMRPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.pause();
            }
        });
    }

    private void InternalPauseDMRPlayer(PlaybackTarget playbackTarget) {
        mediaHub.playerStop(playbackTarget.getTargetId());
    }

    @Override
    public void stopPlaying() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalStopOnDMRPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.stopPlaying();
            }
        });
    }

    private void internalStopOnDMRPlayer(PlaybackTarget playbackTarget) {
        resetGaplessState();
    }

    @Override
    public void setShuffleMode(boolean enabled) {
       /* if (getActivePlayer().isStreaming()) {
            // Streaming player shuffle logic (if supported)
            Log.d(TAG, "Shuffle mode for streaming player: " + enabled);
        } else if (activeExternalController != null) {
           // activeExternalController.getTransportControls() .setShuffleMode(
           //         enabled ? PlaybackState.SHUFFLE_MODE_ALL : PlaybackState.SHUFFLE_MODE_NONE
           // );
        } */
    }

    @Override
    public void setRepeatMode(String mode) {
        if (getActivePlayer().isStreaming()) {
            // Streaming player repeat logic (if supported)
            Log.d(TAG, "Repeat mode for streaming player: " + mode);
        } else if (getActivePlayer() != null) {
           /* int repeatMode = PlaybackState.REPEAT_MODE_NONE;
            if ("ONE".equalsIgnoreCase(mode)) repeatMode = PlaybackState.REPEAT_MODE_ONE;
            else if ("ALL".equalsIgnoreCase(mode)) repeatMode = PlaybackState.REPEAT_MODE_ALL;
            activeExternalController.getTransportControls().setRepeatMode(repeatMode); */
        }
    }

    // ==================== Streaming Player Management ====================

    private void internalPlayOnDMRPlayer(PlaybackTarget player, Track song) {
        if (song == null) return;

        // 1. Start playback
        mediaHub.playerPlaySong(player.getTargetId(), song);
        currentTrackSubject.onNext(Optional.of(song));

        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionSecond = 0;

        onPlaybackStateChanged(state);

        handleTrackStartEvent(song);

        // 3. Cancel any existing task
        if (nextTrackTask != null && !nextTrackTask.isDone()) {
            nextTrackTask.cancel(false);
        }

        // Let EVENT drive everything, Wait for real UPnP event

        /*
        // =========================
        // ✅ GAPLESS: PRELOAD EARLY
        // =========================
        preloadNextTrack();

        // =========================
        // ⚠️ FALLBACK TIMER
        // =========================
        long durationMs = (long) song.getAudioDuration();

        // fallback trigger ~95% (safe)
        long fallbackDelay = (long) (durationMs * 0.95);

        // short track protection
        fallbackDelay = Math.max(3000, fallbackDelay);

        Log.d(TAG, "Gapless: Fallback timer in " + (fallbackDelay / 1000) + " sec");

        nextTrackTask = scheduler.schedule(() -> {
            Log.w(TAG, "Gapless fallback triggered → forcing next track");
            fallbackToNextTrack(player);
        }, fallbackDelay, TimeUnit.MILLISECONDS);

         */
    }

    private void preloadNextTrack() {
        Track next = queueManager.getNextTrack();

        if (next != null) {
            Log.d(TAG, "Gapless: Preloading next → " + next.getTitle());
            mediaHub.setNextTrack(next); // DLNA SetNextAVTransportURI
        } else {
            Log.d(TAG, "Gapless: No next track to preload");
        }
    }

    private void fallbackToNextTrack(PlaybackTarget player) {
        Track current = getNowPlayingSong();
        Track expectedNext = queueManager.getNextTrack();

        if (expectedNext == null) {
            Log.w(TAG, "Fallback: No next track");
            return;
        }

        // Check if renderer already moved
        if (current != null && current.getId() == expectedNext.getId()) {
            Log.d(TAG, "Fallback skipped: renderer already advanced");
            return;
        }

        Log.w(TAG, "Fallback: Forcing next → " + expectedNext.getTitle());

        mediaHub.playerPlaySong(player.getTargetId(), expectedNext);
        queueManager.setPlaybackTrack(expectedNext);

        lastPreloadedTrackId = -1;
    }

    @Override
    public List<PlaybackTarget> getPlaybackTargets() {
        return mediaHub.getPlaybackTargets();
    }

    @Override
    public void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting) {
        mediaHub.addLocalPlaybackTarget(playbackTarget, purgeExisting);
    }

    @Override
    public PlaybackTarget getPlayer() {
        return getActivePlayer();
    }

    @Override
    public void switchPlayer(String targetId, boolean controlled) {
        //if (targetId.startsWith(STREAMING_PLAYER_PREFIX)) {
        // Find and activate streaming player
        PlaybackTarget newTarget = getPlaybackTargets().stream()
                .filter(target -> target.getTargetId().equals(targetId))
                .findFirst()
                .orElse(null);

        switchPlayer(newTarget, controlled);
    }

    @Override
    public void switchPlayer(PlaybackTarget newTarget, boolean controlled) {
        if (newTarget != null) {
            // 1. Resolve the real target (e.g. proxy to actual DMR)
            final PlaybackTarget resolvedTarget = (newTarget.isStreaming()) ? resolveStreamingPlayerTarget(newTarget) : newTarget;

            // 2. Deactivate current player IF DIFFERENT
            currentPlayerSubject.getValue().ifPresent(oldTarget -> {
                if (!oldTarget.getTargetId().equals(resolvedTarget.getTargetId())) {
                    deactivatePlayer(oldTarget);
                }
            });

            // 3. Activate the new player
            if (resolvedTarget instanceof ExternalAndroidPlayer externalPlayer) {
                // Register callback to ensure we are listening to this session
                androidPlayer.registerCallback(externalPlayer, playbackCallback);
            } else if (resolvedTarget.isStreaming()) {
                mediaHub.playerActivate(resolvedTarget.getTargetId(), playbackCallback);
            }

            if (controlled) {
                this.controlledPlayerTargetId = resolvedTarget.getTargetId();
            }

            currentPlayerSubject.onNext(Optional.of(resolvedTarget));
            updateNotification(getApplicationContext(), null, resolvedTarget, mediaHub.getStatus().getValue(), tagRepos.getTotalSongs());
        }
    }

    @Override
    public void setNextSongInQueue() {
        // get next track from que manager
        queueManager.setPlaybackTrack(getNowPlayingSong());
        Track track = queueManager.getNextTrack();
        if(track != null) {
            mediaHub.setNextTrack(track);
        }
    }

    private PlaybackTarget resolveStreamingPlayerTarget(PlaybackTarget player) {
        if(!player.isStreaming()) return player;
        if(player.getDescription() == null) return player;

        //Log.d(TAG, "resolve streaming player: " + player.getTargetId() +" :: "+getAvailablePlaybackTargets().size());
        for(PlaybackTarget dev: getPlaybackTargets()) {
            if(dev!=null && dev.isStreaming()
                    && player.getDescription().equals(dev.getDescription())) {
               // Log.d(TAG, "resolve player:"+ dev.getTargetId()+" - "+dev.getDisplayName());
                return dev;
            }
        }
        return player;
    }

    public boolean isControllable(PlaybackTarget player) {
        final boolean[] controlled = {false};
        if(controlledPlayerTargetId != null) {
            currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
                if(playbackTarget.isStreaming()) {
                    controlled[0] = controlledPlayerTargetId.equals(playbackTarget.getTargetId());
                }
            });
        }
        return controlled[0];
    }

    /**
     * Automatically selects the best available player based on a predefined priority.
     *
     * Priority Order:
     * 1. DMR Player (DLNA/UPnP, target.isStreaming() and target.canReadSate())
     * 2. WebStreaming Player (target.isStreaming())
     * 3. ExternalPlayer (MediaSession-based)
     *
     * @return An Optional containing the highest-priority player found, or Optional.empty() if no suitable player is available.
     */
    public Optional<PlaybackTarget> autoSelectBestPlayer() {
        if (getPlaybackTargets() == null || getPlaybackTargets().isEmpty()) {
            return Optional.empty();
        }

        PlaybackTarget webStreamingFallback = null;
        PlaybackTarget externalPlayerFallback = null;

        // We iterate once to find the best match
        for (PlaybackTarget target : getPlaybackTargets()) {
            if(target == null) continue;

            // --- Priority 1: DMCA / DMR Player ---
            // (Replace 'DMR Player' with your actual DLNA/UPnP player class name)
            if (target.isStreaming() && target.canReadSate()) {
                Log.d(TAG, "Auto-select: Found high-priority DMR Player: " + target.getTargetId());
                // This is the highest priority, so we can return immediately
                return Optional.of(target);
            }

            // --- Priority 2: WebStreaming Player ---
            // If we haven't found a streaming player yet, save this one
            if (webStreamingFallback == null && target.isStreaming()) {
                Log.d(TAG, "Auto-select: Found streaming player (fallback 1): " + target.getTargetId());
                webStreamingFallback = target;
            }

            // --- Priority 3: External Player ---
            // If we haven't found an external player yet, save this one
            if (externalPlayerFallback == null && target instanceof ExternalAndroidPlayer) {
                Log.d(TAG, "Auto-select: Found external player (fallback 2): " + target.getTargetId());
                externalPlayerFallback = target;
            }
        }

        // After checking all players, we use our fallbacks in order of priority

        if (webStreamingFallback != null) {
            Log.d(TAG, "Auto-select: Using streaming player fallback: " + webStreamingFallback.getTargetId());
            return Optional.of(webStreamingFallback);
        }

        if (externalPlayerFallback != null) {
            Log.d(TAG, "Auto-select: Using external player fallback: " + externalPlayerFallback.getTargetId());
            return Optional.of(externalPlayerFallback);
        }

        // No players matched any of our criteria
        Log.d(TAG, "Auto-select: No suitable player found in the list.");
        return Optional.empty();
    }

    private void initWebUIAssets(Context context) {
        try {
            String assetDir = "webui";
            // Per your code, this will delete and re-copy on every service creation.
            ApplicationUtils.deleteFilesFromAndroidFilesDir(context, assetDir);
            ApplicationUtils.copyDirToAndroidFilesDir(context, assetDir);
            ApplicationUtils.copyFileToAndroidFilesDir(context, "playlists.json",  assetDir+"/playlists.json");
            ApplicationUtils.copyFileToAndroidFilesDir(context, "noto_sans_thai_semi_bold.ttf",  assetDir+"/noto_sans_thai.ttf");
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy web assets", e);
        }
    }

    //
    @Override
    public Track getNowPlayingSong() {
        return currentTrackSubject.getValue().orElse(null);
    }

    public String getLibraryNames() {
        return mediaHub.getLibraryNames();
    }

    /**
     * Allows clients (like MediaServerManager) to observe the service's status.
     */
    public LiveData<MediaServerHub.ServerStatus> getStatusLiveData() {
        return statusLiveData;
    }

    // ==================== State Notifications ====================

    private synchronized void handleTrackStartEvent(Track track) {
        if (track == null) return;

        long trackId = track.getId();

        // جلوگیری event ซ้ำ / duplicate events
        if (trackId == lastPlaybackTrackId) {
            Log.d(TAG, "Event ignored (duplicate): " + track.getTitle());
            return;
        }

        lastPlaybackTrackId = trackId;

        Log.d(TAG, "Event-driven: Track started → " + track.getTitle());

        // 1. Sync queue with actual renderer state
        queueManager.setPlaybackTrack(track);

        // 2. Preload next track (PRIMARY)
        preloadNextTrackSafe();

        // 3. Setup fallback timer
        scheduleFallback(track);
    }

    private void preloadNextTrackSafe() {
        Track next = queueManager.getNextTrack();

        if (next == null) {
            Log.d(TAG, "Gapless: No next track");
            return;
        }

        if (next.getId() == lastPreloadedTrackId) {
            Log.d(TAG, "Gapless: Already preloaded → " + next.getTitle());
            return;
        }

        lastPreloadedTrackId = next.getId();

        Log.d(TAG, "Gapless: Preloading → " + next.getTitle());

        mediaHub.setNextTrack(next); // SetNextAVTransportURI
    }

    private void scheduleFallback(Track song) {
        if (nextTrackTask != null && !nextTrackTask.isDone()) {
            nextTrackTask.cancel(false);
        }

        long durationMs = (long) (song.getAudioDuration() * 1000);

        // trigger at ~97% (very late fallback)
        long delay = (long) (durationMs * 0.97);

        delay = Math.max(3000, delay);

        Log.d(TAG, "Fallback Preloading scheduled in " + (delay / 1000) + " sec");

        nextTrackTask = scheduler.schedule(() -> {
            Log.w(TAG, "Fallback triggered!");

            currentPlayerSubject.getValue().ifPresent(this::fallbackToNextTrack);

        }, delay, TimeUnit.MILLISECONDS);
    }

    private void resetGaplessState() {
        lastPreloadedTrackId = -1;
        lastPlaybackTrackId = -1;

        if (nextTrackTask != null) {
            nextTrackTask.cancel(true);
        }
    }

    @Override
    public void onMediaTrackChanged(Track song) {
        currentTrackSubject.onNext(Optional.ofNullable(song));
        runningMode = RUNNING_MODE.CONTROL;
        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionSecond = 0;
        onPlaybackStateChanged(state);

        // EVENT-DRIVEN PIPELINE ENTRY
        handleTrackStartEvent(song);
    }

    @Override
    public void onAccessMediaTrack(Track song) {
        currentTrackSubject.onNext(Optional.ofNullable(song));
        runningMode = RUNNING_MODE.MONITOR;
        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionSecond = 0;
        onPlaybackStateChanged(state);
    }

    @Override
    public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
        playbackStateSubject.onNext(state);
        updateNotification(getApplicationContext(), state.currentTrack, currentPlayerSubject.getValue().orElse(null), mediaHub.getStatus().getValue(), tagRepos.getTotalSongs());
    }

    @Override
    public void onPlaybackStateElapsedTime(long elapsedTimeMS) {
        apincer.music.core.playback.PlaybackState state = playbackStateSubject.getValue();
        if (state != null) {
            state.currentPositionSecond = elapsedTimeMS;
            playbackStateSubject.onNext(state);
        }
    }

    @Override
    public @NonNull Disposable subscribePlaybackState(Consumer<PlaybackState> consumer, Consumer<Throwable> onErrorConsumer) {
        return playbackStateSubject.subscribe(consumer, onErrorConsumer);
    }

    @Override
    public @NonNull Disposable subscribeNowPlayingSong(
            Consumer<Optional<Track>> onNextConsumer,
            Consumer<Throwable> onErrorConsumer
    ) {
        // Use the subscribe overload that takes both
        return currentTrackSubject.subscribe(onNextConsumer, onErrorConsumer);
    }

    @Override
    public @NonNull Disposable subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer, Consumer<Throwable> onErrorConsumer) {
        return currentPlayerSubject.subscribe(consumer, onErrorConsumer);
    }

    // ==================== Binder ====================

    public class MusicMateServiceImplBinder extends Binder implements MusicMateServiceBinder {
        @Override
        public PlaybackService getPlaybackService() {
            return MusicMateServiceImpl.this;
        }
        @Override
        public MediaServerHub getMediaServerHub() {
            return mediaHub;
        }

        public MusicMateServiceImpl getService() {
            return MusicMateServiceImpl.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MusicMateServiceImplBinder();
    }
}
