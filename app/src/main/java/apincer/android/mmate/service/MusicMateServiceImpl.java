package apincer.android.mmate.service;

import static apincer.android.mmate.service.MediaNotificationBuilder.updateNotification;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.DefaultMediaNotificationProvider;
import apincer.android.mmate.R;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.music.core.Constants;
import apincer.music.core.playback.AudioStreamCacheManager;
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
import java.util.function.Consumer;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

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
public class MusicMateServiceImpl extends MediaLibraryService implements PlaybackService {
    private static final String TAG = "MusicMateServiceImpl";

    enum RUNNING_MODE {MONITOR, CONTROL}
    public static final String CHANNEL_ID = "musicmate_service_channel";
    public static final int SERVICE_ID = 1;



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
    private MediaLibrarySession mediaLibrarySession;

    private MediaSessionManager mediaSessionManager;

    // Kotlin coroutines scope for this Android Service (used for cleanup)
    private final kotlinx.coroutines.Job serviceJob = kotlinx.coroutines.SupervisorKt.SupervisorJob(null);

    private final MutableStateFlow<apincer.music.core.playback.PlaybackState> playbackStateFlow =
            StateFlowKt.MutableStateFlow(new apincer.music.core.playback.PlaybackState());
    private final MutableStateFlow<Optional<Track>> currentTrackFlow =
            StateFlowKt.MutableStateFlow(Optional.empty());
    private final MutableStateFlow<Optional<PlaybackTarget>> currentPlayerFlow =
            StateFlowKt.MutableStateFlow(Optional.empty());
    private final MutableStateFlow<List<Track>> playingQueueFlow =
            StateFlowKt.MutableStateFlow(new ArrayList<>());

    // Expose as StateFlow for external read-only access
    public StateFlow<apincer.music.core.playback.PlaybackState> getPlaybackStateFlow() { return playbackStateFlow; }
    public StateFlow<Optional<Track>> getCurrentTrackFlow() { return currentTrackFlow; }
    public StateFlow<Optional<PlaybackTarget>> getCurrentPlayerFlow() { return currentPlayerFlow; }

    private volatile RUNNING_MODE runningMode = RUNNING_MODE.MONITOR;
    private volatile String controlledPlayerTargetId;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "MM:QueueTimer"));
    private ScheduledFuture<?> nextTrackTask;
    private ScheduledFuture<?> preloadTask;
    private ScheduledFuture<?> dmrStartupTimeoutTask;
    private ScheduledFuture<?> trackStartTask;

    private volatile long lastPreloadedTrackId = -1;
    private volatile long lastPlaybackTrackId = -1;

    // The Service is now the single source of truth for its status.
    private final MutableLiveData<MediaServerHub.ServerStatus> statusLiveData = new MutableLiveData<>(MediaServerHub.ServerStatus.STOPPED);
    private androidx.lifecycle.Observer<MediaServerHub.ServerStatus> statusObserver;

    private final PlaybackCallback playbackCallback = new PlaybackCallback() {

        @Override
        public void onMediaTrackChanged(Track metadata) {
            MusicMateServiceImpl.this.onMediaTrackChanged(metadata);
        }

        @Override
        public void onMediaTrackChanged(String title, String artist, String album, long duration) {
            scheduler.execute(() -> {
                Track song = tagRepos.findMusic(title, artist, album);
                if (song != null) {
                    onMediaTrackChanged(song);
                }
            });
        }

        @Override
        public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
            apincer.music.core.playback.PlaybackState current = getPlaybackStateFlow().getValue();
            if (state != null && state.currentTrack == null && current != null) {
                state.currentTrack = current.currentTrack; // Preserve track if missing from callback state
            }
            MusicMateServiceImpl.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onPlaybackCompleted() {
            skipToNextInQueue();
        }

        @Override
        public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
            onPlaybackStateElapsedTime(elapsedSeconds);
        }
    };

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)
                    || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                if (isPlaying() && isLocalTarget()) {
                    Log.d(TAG, "Audio output disconnected / becoming noisy. Auto-pausing local playback.");
                    pausePlayer();
                } else {
                    Log.d(TAG, "Audio output disconnected / becoming noisy, but active playback is remote (DLNA/Cast). Continuing playback.");
                }
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    AudioOutputHelper.clearCachedBluetoothCodec();
                    AudioOutputHelper.refreshBluetoothCodecStatus(context);
                    refreshExternalPlayersSafe();
                }
            } else if ("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED".equals(action)) {
                Log.d(TAG, "Bluetooth codec configuration changed.");
                try {
                    if (intent != null) {
                        try {
                            intent.setExtrasClassLoader(BluetoothProfile.class.getClassLoader());
                        } catch (Exception ignored) {}
                    }
                    Object codecStatus = null;
                    if (intent != null) {
                        if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                            try {
                                Class<?> clazz = Class.forName("android.bluetooth.BluetoothCodecStatus");
                                java.lang.reflect.Method getParcelableExtraMethod = Intent.class.getMethod("getParcelableExtra", String.class, Class.class);
                                codecStatus = getParcelableExtraMethod.invoke(intent, "android.bluetooth.extra.CODEC_STATUS", clazz);
                            } catch (Exception ignored) {}
                        }
                        if (codecStatus == null) {
                            try {
                                codecStatus = intent.getParcelableExtra("android.bluetooth.extra.CODEC_STATUS");
                            } catch (Exception ignored) {}
                        }
                        if (codecStatus == null) {
                            try {
                                codecStatus = intent.getParcelableExtra("android.bluetooth.a2dp.extra.CODEC_STATUS");
                            } catch (Exception ignored) {}
                        }
                        if (codecStatus == null && intent.getExtras() != null) {
                            try {
                                android.os.Bundle bundle = intent.getExtras();
                                for (String key : bundle.keySet()) {
                                    Object val = bundle.get(key);
                                    if (val != null && (val.getClass().getName().contains("CodecStatus") || String.valueOf(val).contains("mCodecConfig") || String.valueOf(val).contains("codecConfig"))) {
                                        codecStatus = val;
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (codecStatus != null) {
                        AudioOutputHelper.parseCodecStatus(codecStatus);
                    } else {
                        AudioOutputHelper.refreshBluetoothCodecStatus(context);
                    }
                } catch (Exception ignored) {
                    AudioOutputHelper.refreshBluetoothCodecStatus(context);
                }
                refreshExternalPlayersSafe();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(TAG, "Bluetooth device connected.");
                AudioOutputHelper.refreshBluetoothCodecStatus(context);
                refreshExternalPlayersSafe();
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.postDelayed(() -> {
                    AudioOutputHelper.refreshBluetoothCodecStatus(context);
                    refreshExternalPlayersSafe();
                }, 1000);
            }
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionChangeListener =
            controllers -> {
               // Log.d(TAG, "Active sessions changed: " + controllers.size());
                updateAvailableExternalPlayers(controllers);
            };

    public MusicMateServiceImpl( ) {
    }

    private void refreshExternalPlayersSafe() {
        List<MediaController> controllers = null;
        if (mediaSessionManager != null && PermissionUtils.isNotificationListenerEnabled(this)) {
            try {
                ComponentName notificationListener = new ComponentName(this, MediaNotificationListener.class);
                controllers = mediaSessionManager.getActiveSessions(notificationListener);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to get active media sessions: " + t.getMessage());
            }
        }
        updateAvailableExternalPlayers(controllers);
    }

    private PlaybackTarget getActivePlayer() {
        return currentPlayerFlow.getValue().orElse(null);
    }

    private boolean isLocalTarget() {
        PlaybackTarget player = getActivePlayer();
        return player != null && ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(player.getTargetId());
    }

    private void updateAvailableExternalPlayers(List<MediaController> controllers) {
        // Remove all existing external Players
        addLocalPlaybackTarget(null, true);

        // Always register default Local Audio Output target
        apincer.android.mmate.utils.AudioOutputHelper.Device outDev = 
                apincer.android.mmate.utils.AudioOutputHelper.getOutputDevice(getApplicationContext(), null);
        String localName = "Local Device";
        String localDesc = "System Audio Output";
        if (outDev != null) {
            if (outDev.getName() != null && !outDev.getName().isEmpty()) {
                localName = outDev.getName();
            }
            if (outDev.getDescription() != null && !outDev.getDescription().isEmpty()) {
                localDesc = outDev.getDescription();
            }
        }
        
        PlaybackTarget localTarget = ExternalAndroidPlayer.Factory.createLocalTarget(
                getApplicationContext(), localName, localDesc);
        if (localTarget != null) {
            addLocalPlaybackTarget(localTarget, false);
        }

        PlaybackTarget playingPlayer = null;
        Set<String> addedPackages = new HashSet<>();

        // Add external media session targets
        if (controllers != null) {
            String selfPackage = getPackageName();
            for (MediaController controller : controllers) {
                if (controller == null) continue;
                String packageName = controller.getPackageName();
                if (selfPackage != null && selfPackage.equalsIgnoreCase(packageName)) {
                    continue; // Skip self — MusicMate is already registered as the primary local player target
                }
                PlaybackTarget player = ExternalAndroidPlayer.Factory.create(getApplicationContext(), packageName);
                if (player != null) {
                    addLocalPlaybackTarget(player, false);
                    addedPackages.add(packageName);
                    android.media.session.PlaybackState state = controller.getPlaybackState();
                    if (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                        playingPlayer = player;
                    }
                }
            }
        }

        // Add installed external music players even if not currently playing
        for (String pkg : ExternalAndroidPlayer.SUPPORTED_PLAYERS) {
            if (!addedPackages.contains(pkg) && ExternalAndroidPlayer.Factory.isPackageInstalled(getApplicationContext(), pkg)) {
                PlaybackTarget player = ExternalAndroidPlayer.Factory.create(getApplicationContext(), pkg);
                if (player != null) {
                    addLocalPlaybackTarget(player, false);
                    addedPackages.add(pkg);
                }
            }
        }

        // If an external player is playing or if no player is selected, auto-select!
        if (playingPlayer != null) {
            switchPlayer(playingPlayer, false);
        } else if (!currentPlayerFlow.getValue().isPresent()) {
            String lastPlayerId = apincer.music.core.Settings.getLastPlayerTargetId(getApplicationContext());
            if (lastPlayerId != null && !lastPlayerId.isEmpty()) {
                switchPlayer(lastPlayerId, true);
                if (!currentPlayerFlow.getValue().isPresent() && lastPlayerId.startsWith("uuid:")) {
                    // It's a DLNA target that hasn't been discovered yet.
                    // Create a placeholder target so we don't fallback to localTarget immediately.
                    PlaybackTarget dummy = apincer.music.core.playback.DMRPlayer.Factory.create(lastPlayerId, "Scanning for players…", "");
                    switchPlayer(dummy, true);

                    // Schedule safety fallback to local target if previous DLNA renderer does not appear
                    if (dmrStartupTimeoutTask != null) {
                        dmrStartupTimeoutTask.cancel(false);
                    }
                    dmrStartupTimeoutTask = scheduler.schedule(() -> {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            if (currentPlayerFlow.getValue().isPresent()) {
                                PlaybackTarget cur = currentPlayerFlow.getValue().get();
                                if (cur.isStreaming() && "Scanning for players…".equals(cur.getDisplayName())) {
                                    Log.i(TAG, "DLNA target discovery timed out (8s) → falling back to local playback target");
                                    switchPlayer(localTarget, true);
                                }
                            }
                        });
                    }, 8, TimeUnit.SECONDS);
                }
            }
            if (!currentPlayerFlow.getValue().isPresent()) {
                switchPlayer(localTarget, true);
            }
        }

        Log.d(TAG, "Updated available targets: " + getPlaybackTargets().size());
    }

    // ==================== Service Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Only call it ONCE based on the Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_ID, createInitialNotification());
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

        statusObserver = status -> {
            PlaybackTarget activeTarget = getActivePlayer();
            // Only update notification if NOT using the local Media3 ExoPlayer (Media3 handles its own)
            if (activeTarget == null || !ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(activeTarget.getTargetId())) {
                Track currentTrack = getNowPlayingSong();
                MediaServerHub.ServerStatus s = (status != null) ? status : mediaHub.getStatus().getValue();
                updateNotification(getApplicationContext(), currentTrack, activeTarget, s, tagRepos.getTotalSongs(), isPlaying());
            }
        };
        getStatusLiveData().observeForever(statusObserver);

        // external player controller
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
                androidPlayer = new AndroidPlayerController(getApplicationContext(), mediaSessionManager);
        
        // Initialize MediaLibrarySession for Media3
        if (androidPlayer.getInternalExoPlayer() != null) {
            mediaLibrarySession = new MediaLibrarySession.Builder(this, androidPlayer.getInternalExoPlayer(), new MediaLibrarySession.Callback() {})
                .build();
                
            DefaultMediaNotificationProvider notificationProvider = new DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(SERVICE_ID)
                .build();
            notificationProvider.setSmallIcon(R.drawable.ic_notification_default);
            setMediaNotificationProvider(notificationProvider);
        }
        ComponentName notificationListener = new ComponentName(this, MediaNotificationListener.class);
        if (PermissionUtils.isNotificationListenerEnabled(this)) {
            try {
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(notificationListener);
                // initial with external player
                updateAvailableExternalPlayers(controllers);
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionChangeListener, notificationListener);
            } catch (Throwable e) {
                Log.e(TAG, "Failed to query active media sessions", e);
                updateAvailableExternalPlayers(null);
            }
        } else {
            Log.w(TAG, "Notification listener permission not granted.");
            updateAvailableExternalPlayers(null);
        }

        // Load queue from database
        if(queueManager != null) {
            playingQueueFlow.setValue(queueManager.getSongs());
        }

        // Init WebUI assets in background to avoid blocking UI thread during service creation
        Executors.newSingleThreadExecutor().execute(() -> initWebUIAssets(getApplicationContext()));

        // Hook up live DLNA renderer discovery listener to automatically reconcile placeholder targets
        if (mediaHub != null) {
            mediaHub.setOnRenderersChangedListener(this::handleDiscoveredRenderers);
        }

        // Initialize Bluetooth A2DP proxy for real-time codec telemetry
        AudioOutputHelper.initializeBluetooth(getApplicationContext());

        // Register receiver for headphone / Bluetooth disconnect auto-pause & codec configuration changes
        IntentFilter audioNoisyFilter = new IntentFilter();
        audioNoisyFilter.addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        audioNoisyFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        audioNoisyFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        audioNoisyFilter.addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, audioNoisyFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(becomingNoisyReceiver, audioNoisyFilter);
        }

        Optional<PlaybackTarget> bestChoice = autoSelectBestPlayer();
        if (bestChoice.isPresent()) {
            // We found a player, now switch to it
            PlaybackTarget selectedPlayer = bestChoice.get();
            switchPlayer(selectedPlayer, true);
        } else {
            // No players are available, maybe switch to local playback or show a message
            Log.w(TAG, "No players available to auto-select.");
        }
    }

    public static final String ACTION_SKIP_PREVIOUS = "apincer.android.mmate.action.SKIP_PREVIOUS";
    public static final String ACTION_TOGGLE_PLAYBACK = "apincer.android.mmate.action.TOGGLE_PLAYBACK";
    public static final String ACTION_SKIP_NEXT = "apincer.android.mmate.action.SKIP_NEXT";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                // --- Remote Control Actions ---
                case ACTION_SKIP_PREVIOUS:
                    skipToPrevious();
                    break;
                case ACTION_TOGGLE_PLAYBACK:
                    if (isPlaying()) {
                        pausePlayer();
                    } else {
                        Track current = getNowPlayingSong();
                        if (current != null) {
                            resumePlayer();
                        }
                    }
                    break;
                case ACTION_SKIP_TO_NEXT:
                case ACTION_SKIP_NEXT:
                case ACTION_PLAY_NEXT:
                    long deletedId = intent.getLongExtra(EXTRA_MUSIC_ID, -1);
                    if (deletedId != -1) {
                        onTrackDeleted(deletedId);
                    } else {
                        skipToNextInQueue();
                    }
                    break;

                // --- Server Actions ---
                case MediaServerManager.ACTION_START_SERVER:
                    startServers();
                    break;
                case MediaServerManager.ACTION_STOP_SERVER:
                    stopServers();
                    break;
            }
        }
        return START_STICKY;
    }

    public void startServers() {
        if (!NetworkUtils.isServerNetworkAvailable(this)) {
            statusLiveData.postValue(MediaServerHub.ServerStatus.ERROR);
            Log.d(TAG, TAG+" - Error, Required WiFi or Hotspot network");
            return;
        }

        mediaHub.start();
        statusLiveData.postValue(MediaServerHub.ServerStatus.RUNNING);
    }

    public void stopServers() {
        mediaHub.stop();
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
        return playbackStateFlow.getValue().currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
    }

    @Override
        public void onDestroy() {
        if (mediaLibrarySession != null) {
            mediaLibrarySession.release();
            mediaLibrarySession = null;
        }
        // 1. Cancel the pending "Next Track", preload, and DLNA startup timeout timers
        if (nextTrackTask != null) {
            nextTrackTask.cancel(true);
        }
        if (preloadTask != null) {
            preloadTask.cancel(true);
        }
        if (dmrStartupTimeoutTask != null) {
            dmrStartupTimeoutTask.cancel(true);
            dmrStartupTimeoutTask = null;
        }

        // 2. Shut down the scheduler
        scheduler.shutdownNow();

        // 3. Remove LiveData observer
        if (statusObserver != null) {
            getStatusLiveData().removeObserver(statusObserver);
        }

        // 4. Cancel coroutines job
        serviceJob.cancel(null);

        // Unregister renderer listener to prevent Singleton from holding reference to destroyed service
        if (mediaHub != null) {
            mediaHub.setOnRenderersChangedListener(null);
        }

        // Ensure everything is cleaned up if the service is destroyed.
        stopServers();

        try {
            unregisterReceiver(becomingNoisyReceiver);
        } catch (Exception ignored) {}

        AudioOutputHelper.cleanupBluetooth(getApplicationContext());

        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionChangeListener);
        }
        if (androidPlayer != null) {
            androidPlayer.release();
        }
        deactivatePlayer(getActivePlayer());
        super.onDestroy();
    }

    private void deactivatePlayer(PlaybackTarget player) {
        if(player != null) {
            // Cancel any pending gapless fallback timer to prevent it firing on the wrong player
            resetGaplessState();

            try {
                if (player.isStreaming()){
                    // Stop playback before deactivating to prevent audio continuing in background
                    mediaHub.playerStop(player.getTargetId());
                    mediaHub.playerDeactivate(player.getTargetId());
                }else {
                    androidPlayer.stopPlaying();
                    androidPlayer.unregisterCallback();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error deactivating player: " + player.getDisplayName(), e);
            }
        }
    }

    // ==================== Playback Control (Unified) ====================

    @Override
    public void playSong(Track song) {
        if (song != null) {
            if (!queueManager.containsTrack(song.getId())) {
                queueManager.addPlayingQueue(song.getId());
            }
            queueManager.setCurrentTrack(song);
            AudioStreamCacheManager.getInstance().preloadTrack(song);
        }
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            runningMode = RUNNING_MODE.CONTROL;
            if (isControllable(playbackTarget)) {
                internalPlayOnDMRPlayer(playbackTarget, song);
            } else {
                androidPlayer.play(song);
                currentTrackFlow.setValue(Optional.ofNullable(song));
                if (song != null) {
                    apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
                    state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
                    state.currentTrack = song;
                    playbackStateFlow.setValue(state);
                }
            }
        });
    }

    @Override
    public void skipToNextInQueue() {
        if (sleepTimerEndOfTrack) {
            sleepTimerEndOfTrack = false;
            sleepTimerEndTimeMs = 0;
            pausePlayer();
            return;
        }
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalSkipToNextOnDMRPlayer(playbackTarget);
            } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTarget.getTargetId())) {
                // Local ExoPlayer: advance MusicMate's queue
                Track current = getNowPlayingSong();
                if (current != null) {
                    queueManager.setCurrentTrack(current);
                }
                Track nextSong = queueManager.getNextTrack();
                if (nextSong != null) {
                    queueManager.setPlaybackTrack(nextSong);
                    playSong(nextSong);
                } else {
                    stopPlaying();
                }
            } else {
                // External music app: MediaSession IPC event
                androidPlayer.skipToNext();
            }
        });
    }

    private void internalSkipToNextOnDMRPlayer(PlaybackTarget playbackTarget) {
        Track current = getNowPlayingSong();
        if (current != null) {
            queueManager.setCurrentTrack(current);
        }
        Track song = queueManager.getNextTrack();
        if (song != null) {
            queueManager.setPlaybackTrack(song);
            internalPlayOnDMRPlayer(playbackTarget, song);
        } else {
            Log.d(TAG, "internalSkipToNextOnDMRPlayer: Queue ended, stopping DMR");
            internalStopOnDMRPlayer(playbackTarget);
        }
    }

    @Override
    public void skipToPrevious() {
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalPreviousOnDMRPlayer(playbackTarget);
            } else if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(playbackTarget.getTargetId())) {
                // Local ExoPlayer: advance MusicMate's queue backwards
                Track current = getNowPlayingSong();
                if (current != null) {
                    queueManager.setCurrentTrack(current);
                }
                Track prevSong = queueManager.getPreviousTrack();
                if (prevSong != null) {
                    queueManager.setPlaybackTrack(prevSong);
                    playSong(prevSong);
                }
            } else {
                // External music app: MediaSession IPC event
                androidPlayer.skipToPrevious();
            }
        });
    }

    private void internalPreviousOnDMRPlayer(PlaybackTarget playbackTarget) {
        Track current = getNowPlayingSong();
        if (current != null) {
            queueManager.setCurrentTrack(current);
        }
        Track song = queueManager.getPreviousTrack();
        if (song != null) {
            queueManager.setPlaybackTrack(song);
            internalPlayOnDMRPlayer(playbackTarget, song);
        }
    }

    @Override
    public void pausePlayer() {
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                InternalPauseDMRPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.pause();
            }
        });
    }

    private void InternalPauseDMRPlayer(PlaybackTarget playbackTarget) {
        try {
            mediaHub.playerPause(playbackTarget.getTargetId());
        } catch (Exception e) {
            Log.w(TAG, "Failed to pause DMR: " + playbackTarget.getDisplayName(), e);
        }
        if (nextTrackTask != null) {
            nextTrackTask.cancel(true);
            nextTrackTask = null;
        }
        if (preloadTask != null) {
            preloadTask.cancel(true);
            preloadTask = null;
        }
        if (trackStartTask != null) {
            trackStartTask.cancel(true);
            trackStartTask = null;
        }
        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PAUSED;
        state.currentTrack = getNowPlayingSong();
        if (playbackStateFlow.getValue() != null) {
            state.currentPositionSecond = playbackStateFlow.getValue().currentPositionSecond;
        }
        onPlaybackStateChanged(state);
    }

    @Override
    public void resumePlayer() {
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                try {
                    mediaHub.playerResume(playbackTarget.getTargetId());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to resume DMR: " + playbackTarget.getDisplayName(), e);
                }
                apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
                state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
                Track currentSong = getNowPlayingSong();
                state.currentTrack = currentSong;
                long curPos = 0;
                if (playbackStateFlow.getValue() != null) {
                    curPos = playbackStateFlow.getValue().currentPositionSecond;
                    state.currentPositionSecond = curPos;
                }
                onPlaybackStateChanged(state);

                // Reschedule fallback timer based on remaining duration
                if (currentSong != null && currentSong.getAudioDuration() > 0) {
                    long remainingSec = Math.max(1, Math.round(currentSong.getAudioDuration()) - curPos);
                    long delayMs = (remainingSec * 1000L) + 1500L;
                    if (nextTrackTask != null) {
                        nextTrackTask.cancel(true);
                    }
                    nextTrackTask = scheduler.schedule(() -> {
                        Log.w(TAG, "Track end fallback triggered after resumed duration!");
                        currentPlayerFlow.getValue().ifPresentOrElse(
                                this::fallbackToNextTrack,
                                () -> Log.w(TAG, "Fallback transition skipped: no active playback target")
                        );
                    }, delayMs, TimeUnit.MILLISECONDS);
                }
            } else {
                androidPlayer.resume();
            }
        });
    }

    @Override
    public void stopPlaying() {
        currentPlayerFlow.getValue().ifPresent(playbackTarget -> {
            if (isControllable(playbackTarget)) {
                internalStopOnDMRPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.stopPlaying();
            }
        });
    }

    private void internalStopOnDMRPlayer(PlaybackTarget playbackTarget) {
        try {
            mediaHub.playerStop(playbackTarget.getTargetId());
        } catch (Exception e) {
            Log.w(TAG, "Failed to stop DMR: " + playbackTarget.getDisplayName(), e);
        }
        resetGaplessState();
        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.STOPPED;
        state.currentTrack = null;
        onPlaybackStateChanged(state);
    }

    @Override
    public QueueManager getQueueManager() {
        return queueManager;
    }

    @Override
    public void setShuffleMode(boolean enabled) {
        queueManager.setShuffle(enabled);
        triggerPlaybackStateUpdate();
    }

    @Override
    public void setRepeatMode(String mode) {
        try {
            if ("0".equals(mode)) {
                queueManager.setRepeatMode(apincer.music.core.repository.QueueManager.RepeatMode.OFF);
            } else if ("1".equals(mode)) {
                queueManager.setRepeatMode(apincer.music.core.repository.QueueManager.RepeatMode.ALL);
            } else if ("2".equals(mode)) {
                queueManager.setRepeatMode(apincer.music.core.repository.QueueManager.RepeatMode.ONE);
            } else {
                queueManager.setRepeatMode(apincer.music.core.repository.QueueManager.RepeatMode.valueOf(mode));
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unknown repeat mode: " + mode + ", defaulting to OFF");
            queueManager.setRepeatMode(apincer.music.core.repository.QueueManager.RepeatMode.OFF);
        }
        triggerPlaybackStateUpdate();
    }

    private void triggerPlaybackStateUpdate() {
        apincer.music.core.playback.PlaybackState state = playbackStateFlow.getValue();
        if (state != null) {
            apincer.music.core.playback.PlaybackState updated = state.copy();
            onPlaybackStateChanged(updated);
        }
    }

    // ==================== Streaming Player Management ====================

    private void internalPlayOnDMRPlayer(PlaybackTarget player, Track song) {
        if (song == null) return;

        // Cancel any existing gapless task first
        resetGaplessState();

        // 1. Start playback
        try {
            mediaHub.playerPlaySong(player.getTargetId(), song);
        } catch (Exception e) {
            Log.w(TAG, "Failed to play on DMR: " + player.getDisplayName(), e);
            return;
        }
        currentTrackFlow.setValue(Optional.of(song));

        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionSecond = 0;

        onPlaybackStateChanged(state);

        // C-2: Delay handleTrackStartEvent by 1.5s to allow the async Stop→SetURI→Play
        // handshake to complete before the preload and fallback timers start.
        // Firing these timers immediately after playerPlaySong() (which only queues the command)
        // means the timers run from the wrong base time and the preload can fire before the
        // renderer is even playing. onMediaTrackChanged() from GENA/polling remains the
        // authoritative real-time trigger for subsequent track-start events.
        trackStartTask = scheduler.schedule(() -> handleTrackStartEvent(song), 1500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void seekTo(long positionMs) {
        PlaybackTarget currentTarget = getPlayer();
        if (currentTarget != null) {
            try {
                Track currentTrack = getNowPlayingSong();
                long targetPos = Math.max(0, positionMs);
                if (currentTrack != null && currentTrack.getAudioDuration() > 0) {
                    long durationMs = (long) (currentTrack.getAudioDuration() * 1000.0);
                    targetPos = Math.min(targetPos, durationMs);
                }
                if (currentTarget instanceof ExternalAndroidPlayer || !currentTarget.isStreaming()) {
                    androidPlayer.seekTo(targetPos);
                } else {
                    mediaHub.playerSeek(currentTarget.getTargetId(), targetPos);
                    // Reschedule fallback timer based on seek position
                    if (currentTrack != null && currentTrack.getAudioDuration() > 0) {
                        long durationMs = (long) (currentTrack.getAudioDuration() * 1000.0);
                        long remainingMs = Math.max(1000L, durationMs - targetPos);
                        if (nextTrackTask != null) {
                            nextTrackTask.cancel(true);
                        }
                        nextTrackTask = scheduler.schedule(() -> {
                            Log.w(TAG, "Track end fallback triggered after seek remaining duration!");
                            currentPlayerFlow.getValue().ifPresentOrElse(
                                    this::fallbackToNextTrack,
                                    () -> Log.w(TAG, "Fallback transition skipped: no active playback target")
                            );
                        }, remainingMs + 1500L, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to seek", e);
            }
        }
    }

    private int dmrVolume = 50;

    @Override
    public void setVolume(int volumePercent) {
        PlaybackTarget currentTarget = getPlayer();
        if (currentTarget != null && currentTarget.isStreaming()) {
            dmrVolume = Math.max(0, Math.min(100, volumePercent));
            try {
                mediaHub.playerSetVolume(currentTarget.getTargetId(), dmrVolume);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set DMR volume", e);
            }
        }
    }

    @Override
    public void adjustVolume(int direction) {
        PlaybackTarget currentTarget = getPlayer();
        if (currentTarget != null && currentTarget.isStreaming()) {
            int step = 5 * (direction > 0 ? 1 : -1);
            setVolume(dmrVolume + step);
        }
    }

    private java.util.Timer sleepTimer;
    private long sleepTimerEndTimeMs = 0;
    private boolean sleepTimerEndOfTrack = false;

    @Override
    public void setSleepTimer(long minutes, boolean endOfTrack) {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        sleepTimerEndOfTrack = endOfTrack;
        if (minutes <= 0 && !endOfTrack) {
            sleepTimerEndTimeMs = 0;
            return;
        }
        if (endOfTrack) {
            sleepTimerEndTimeMs = -1;
            return;
        }
        long durationMs = minutes * 60 * 1000L;
        sleepTimerEndTimeMs = System.currentTimeMillis() + durationMs;
        sleepTimer = new java.util.Timer("SleepTimer", true);
        sleepTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                fadeOutAndPause();
            }
        }, durationMs);
    }

    @Override
    public long getSleepTimerRemainingMs() {
        if (sleepTimerEndOfTrack) return -1;
        if (sleepTimerEndTimeMs <= 0) return 0;
        return Math.max(0, sleepTimerEndTimeMs - System.currentTimeMillis());
    }

    private void fadeOutAndPause() {
        new Thread(() -> {
            try {
                for (int i = 4; i >= 1; i--) {
                    adjustVolume(-1);
                    Thread.sleep(500);
                }
                pausePlayer();
                sleepTimerEndTimeMs = 0;
                sleepTimerEndOfTrack = false;
            } catch (Exception ignored) {}
        }).start();
    }

    private void fallbackToNextTrack(PlaybackTarget player) {
        Track current = getNowPlayingSong();
        Track expectedNext = queueManager.getNextTrack();

        if (expectedNext == null) {
            Log.w(TAG, "Fallback: No next track (queue ended), stopping playback");
            if (player != null && player.isStreaming()) {
                internalStopOnDMRPlayer(player);
            } else {
                stopPlaying();
            }
            return;
        }

        // Check if renderer already moved
        if (current != null && current.getId() == expectedNext.getId()) {
            Log.d(TAG, "Fallback skipped: renderer already advanced");
            return;
        }

        Log.w(TAG, "Fallback: Forcing next → " + expectedNext.getTitle());
        queueManager.setPlaybackTrack(expectedNext);
        if (player != null && player.isStreaming() && isControllable(player)) {
            internalPlayOnDMRPlayer(player, expectedNext);
        } else {
            playSong(expectedNext);
        }
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
    public void refreshPlayerDiscovery() {
        refreshExternalPlayersSafe();
        if (mediaHub != null) {
            mediaHub.refreshDiscovery();
        }
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

            boolean wasPlaying = isPlaying();
            Track activeTrack = getNowPlayingSong();
            long currentPositionMs = 0;
            apincer.music.core.playback.PlaybackState lastState = playbackStateFlow.getValue();
            if (lastState != null && lastState.currentPositionSecond > 0) {
                currentPositionMs = lastState.currentPositionSecond * 1000L;
            }

            final boolean isSameTarget = currentPlayerFlow.getValue().isPresent() 
                && currentPlayerFlow.getValue().get().getTargetId().equals(resolvedTarget.getTargetId());

            // If this is an unprompted / uncontrolled HTTP streaming request (controlled == false)
            // and we ALREADY have an active controlled DMR player, do NOT switch or deactivate the current player!
            if (!controlled && currentPlayerFlow.getValue().isPresent() && isControllable(currentPlayerFlow.getValue().get())) {
                return;
            }

            // 2. Deactivate current player IF DIFFERENT
            currentPlayerFlow.getValue().ifPresent(oldTarget -> {
                if (!oldTarget.getTargetId().equals(resolvedTarget.getTargetId())) {
                    deactivatePlayer(oldTarget);
                }
            });

            apincer.music.core.Settings.setLastPlayerTargetId(getApplicationContext(), resolvedTarget.getTargetId());

            // 3. Activate the new player
            if (resolvedTarget instanceof ExternalAndroidPlayer externalPlayer) {
                // Register callback to ensure we are listening to this session
                androidPlayer.registerCallback(externalPlayer, playbackCallback);
                if (controlled && activeTrack != null && !isSameTarget && wasPlaying) {
                    playSong(activeTrack);
                    if (currentPositionMs > 1000) {
                        seekTo(currentPositionMs);
                    }
                }
            } else if (resolvedTarget.isStreaming()) {
                startServers();
                if (controlled && activeTrack != null && !isSameTarget && wasPlaying) {
                    mediaHub.playerActivateWithHandoff(resolvedTarget.getTargetId(), playbackCallback, activeTrack, currentPositionMs);
                } else {
                    mediaHub.playerActivate(resolvedTarget.getTargetId(), playbackCallback);
                }
            }

            if (controlled || resolvedTarget.isStreaming()) {
                this.controlledPlayerTargetId = resolvedTarget.getTargetId();
            }

            currentPlayerFlow.setValue(Optional.of(resolvedTarget));
            Track active = getNowPlayingSong();
            updateNotification(getApplicationContext(), active, resolvedTarget, mediaHub.getStatus().getValue(), tagRepos.getTotalSongs(), isPlaying());
        }
    }

    @Override
    public void setNextSongInQueue() {
        queueManager.setPlaybackTrack(getNowPlayingSong());
        preloadNextTrackSafe();
    }

    private void handleDiscoveredRenderers(List<PlaybackTarget> renderers) {
        if (renderers == null || renderers.isEmpty()) return;

        Optional<PlaybackTarget> currentOpt = currentPlayerFlow.getValue();
        if (currentOpt.isPresent()) {
            PlaybackTarget current = currentOpt.get();
            if (current.isStreaming()) {
                String targetId = current.getTargetId();
                for (PlaybackTarget live : renderers) {
                    if (live != null && live.getTargetId().equals(targetId)) {
                        // Reconcile placeholder with live device data
                        if ("Scanning for players…".equals(current.getDisplayName())
                                || !live.getDisplayName().equals(current.getDisplayName())
                                || !live.getDescription().equals(current.getDescription())) {
                            Log.i(TAG, "Reconciling live DLNA renderer: " + live.getDisplayName() + " [" + targetId + "]");
                            if (dmrStartupTimeoutTask != null) {
                                dmrStartupTimeoutTask.cancel(false);
                                dmrStartupTimeoutTask = null;
                            }
                            currentPlayerFlow.setValue(Optional.of(live));
                        }
                        break;
                    }
                }
            }
        }
    }

    private PlaybackTarget resolveStreamingPlayerTarget(PlaybackTarget player) {
        if (!player.isStreaming()) return player;

        String incomingIp = NetworkUtils.extractIpAddress(player.getDescription());
        if (incomingIp.isEmpty()) return player;

        for (PlaybackTarget dev : getPlaybackTargets()) {
            if (dev != null && dev.isStreaming() && dev != player) {
                String devIp = NetworkUtils.extractIpAddress(dev.getDescription());
                if (!devIp.isEmpty() && incomingIp.equals(devIp)) {
                    return dev;
                }
            }
        }
        return player;
    }

    public boolean isControllable(PlaybackTarget player) {
        if (player == null || !player.isStreaming()) {
            return false;
        }
        if (controlledPlayerTargetId == null) {
            controlledPlayerTargetId = player.getTargetId();
        }
        return controlledPlayerTargetId.equals(player.getTargetId());
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
        return currentTrackFlow.getValue().orElse(null);
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

        // Avoid duplicate events unless the fallback task has already completed or repeat mode is active
        boolean fallbackActive = nextTrackTask != null && !nextTrackTask.isDone();
        boolean isRepeatOne = queueManager != null && queueManager.getRepeatMode() == apincer.music.core.repository.QueueManager.RepeatMode.ONE;
        if (trackId == lastPlaybackTrackId && fallbackActive && !isRepeatOne) {
            Log.d(TAG, "Event ignored (duplicate): " + track.getTitle());
            return;
        }

        lastPlaybackTrackId = trackId;

        Log.d(TAG, "Event-driven: Track started → " + track.getTitle());

        // 1. Sync queue with actual renderer state
        queueManager.setPlaybackTrack(track);

        // 2. Preload next track with 5-second stabilization delay (prevents initial playback stutter)
        schedulePreloadNextTrack(track);

        // 3. Setup fallback timer
        scheduleFallback(track);
    }

    private void schedulePreloadNextTrack(Track track) {
        if (preloadTask != null && !preloadTask.isDone()) {
            preloadTask.cancel(false);
        }

        PlaybackTarget activePlayer = getActivePlayer();
        boolean isHiBy = (activePlayer instanceof apincer.music.core.playback.DMRPlayer && ((apincer.music.core.playback.DMRPlayer) activePlayer).isHiBy())
                || mediaHub.isCurrentRendererHiBy();
        // SAFE-BY-DEFAULT: Only DMRPlayer instances that explicitly report supportsPreload() = true
        // may use UPnP SetNextAVTransportURI. Non-DMR players (WebStreamingPlayer) and unknown
        // targets must NEVER default to supportsPreload = true.
        boolean supportsPreload = (activePlayer instanceof apincer.music.core.playback.DMRPlayer)
                && ((apincer.music.core.playback.DMRPlayer) activePlayer).supportsPreload();

        if (isHiBy || !supportsPreload) {
            Log.d(TAG, "Renderer (" + (activePlayer != null ? activePlayer.getDisplayName() : "Unknown") + ") does not support UPnP SetNextAVTransportURI. Pre-caching stream in memory for discrete handover.");
            Track next = queueManager.getNextTrack();
            if (next != null) {
                AudioStreamCacheManager.getInstance().preloadTrack(next);
            }
            return;
        }

        // Compute a dynamic preload delay:
        // Fire SetNextAVTransportURI at 60% of the track's duration so the renderer
        // has time to confirm playback is fully established before the next URI is queued.
        // Use max(deviceGaplessDelayMs, 60% duration) but never more than the last 30s
        // of the track, to maintain gapless crossfade behaviour on long tracks.
        long deviceGaplessDelayMs = (activePlayer instanceof apincer.music.core.playback.DMRPlayer)
                ? ((apincer.music.core.playback.DMRPlayer) activePlayer).getDeviceProfile().getGaplessDelayMs()
                : 5000;

        long trackDurationMs = track != null && track.getAudioDuration() > 0
                ? (long) (track.getAudioDuration() * 1000)
                : 0;

        long preloadDelayMs;
        if (trackDurationMs > 0) {
            long sixtyPctMs = (long) (trackDurationMs * 0.60);
            long thirtySecFromEnd = Math.max(0, trackDurationMs - 30_000);
            // Clamp: at least deviceGaplessDelay, at most 30s before end of track
            preloadDelayMs = Math.min(Math.max(sixtyPctMs, deviceGaplessDelayMs), thirtySecFromEnd);
            Log.d(TAG, "Gapless preload scheduled in " + (preloadDelayMs / 1000) + "s"
                    + " (60% of " + (trackDurationMs / 1000) + "s track)");
        } else {
            preloadDelayMs = deviceGaplessDelayMs;
            Log.d(TAG, "Gapless preload scheduled in " + (preloadDelayMs / 1000) + "s (no duration metadata)");
        }

        preloadTask = scheduler.schedule(() -> {
            preloadNextTrackSafe();
        }, preloadDelayMs, TimeUnit.MILLISECONDS);
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
        AudioStreamCacheManager.getInstance().preloadTrack(next);

        PlaybackTarget activePlayer = getActivePlayer();
        boolean isHiBy = (activePlayer instanceof apincer.music.core.playback.DMRPlayer && ((apincer.music.core.playback.DMRPlayer) activePlayer).isHiBy())
                || mediaHub.isCurrentRendererHiBy();
        boolean supportsPreload = (activePlayer instanceof apincer.music.core.playback.DMRPlayer)
                && ((apincer.music.core.playback.DMRPlayer) activePlayer).supportsPreload();

        if (isLocalTarget()) {
            androidPlayer.setNextTrack(next);
        } else if (activePlayer != null && isControllable(activePlayer) && !isHiBy && supportsPreload) {
            mediaHub.setNextTrack(next); // SetNextAVTransportURI
        }
    }

    private void scheduleFallback(Track song) {
        PlaybackTarget activePlayer = getActivePlayer();
        if (activePlayer == null || !activePlayer.isStreaming() || !isControllable(activePlayer)) {
            return; // Safety fallback timer is strictly for controllable DLNA/DMR streaming renderers
        }

        if (nextTrackTask != null && !nextTrackTask.isDone()) {
            nextTrackTask.cancel(false);
        }

        long durationMs = (long) (song.getAudioDuration() * 1000);
        if (durationMs <= 0) return;

        // Schedule safety fallback ONLY after 100% track duration + 1.5s grace period
        // so current track plays completely to the end without getting cut off
        long delay = durationMs + 1500;

        Log.d(TAG, "Fallback transition scheduled in " + (delay / 1000) + " sec (after full track completion)");

        nextTrackTask = scheduler.schedule(() -> {
            Log.w(TAG, "Track end fallback triggered after full duration!");

            currentPlayerFlow.getValue().ifPresentOrElse(
                    this::fallbackToNextTrack,
                    () -> Log.w(TAG, "Fallback transition skipped: no active playback target")
            );

        }, delay, TimeUnit.MILLISECONDS);
    }

    private void resetGaplessState() {
        lastPreloadedTrackId = -1;
        lastPlaybackTrackId = -1;

        if (preloadTask != null) {
            preloadTask.cancel(true);
            preloadTask = null;
        }

        if (nextTrackTask != null) {
            nextTrackTask.cancel(true);
            nextTrackTask = null;
        }

        if (trackStartTask != null) {
            trackStartTask.cancel(true);
            trackStartTask = null;
        }

        AudioStreamCacheManager.getInstance().cancelPendingPreloads();
    }

    @Override
    public void onMediaTrackChanged(Track song) {
        currentTrackFlow.setValue(Optional.ofNullable(song));
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
        // Guard: Do NOT let passive HTTP access requests hijack an active controlled playback session.
        // When a DLNA renderer pre-fetches the next track (triggered by SetNextAVTransportURI),
        // it issues an HTTP GET for the next song. That request must NOT reset currentTrack,
        // timers, or preload state — the DMR's GENA events and polling handle transitions.
        PlaybackTarget activePlayer = getActivePlayer();
        if (activePlayer != null && isControllable(activePlayer)) {
            Log.d(TAG, "onAccessMediaTrack: Ignoring passive HTTP request for '"
                    + (song != null ? song.getTitle() : "null")
                    + "' — active controlled DMR session in progress.");
            return;
        }

        Track current = getNowPlayingSong();
        if (current != null && song != null && current.getId() == song.getId()) {
            // Track is already active, do not interrupt playback state
            return;
        }
        currentTrackFlow.setValue(Optional.ofNullable(song));
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
    public void onTrackDeleted(long trackId) {
        Track current = getNowPlayingSong();
        boolean isCurrent = (current != null && current.getId() == trackId);
        if (queueManager != null) {
            queueManager.removeTrackById(trackId);
        }
        if (isCurrent) {
            Log.i(TAG, "Currently playing track deleted (ID " + trackId + "). Advancing to next track in queue.");
            Track next = (queueManager != null) ? queueManager.getCurrentTrack() : null;
            if (next != null) {
                playSong(next);
            } else {
                stopPlaying();
            }
        }
    }

    @Override
    public void onTrackDeleted(Track tag) {
        if (tag != null) {
            onTrackDeleted(tag.getId());
        }
    }

    @Override
    public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
        playbackStateFlow.setValue(state);
        apincer.music.core.playback.spi.PlaybackTarget target = currentPlayerFlow.getValue().orElse(null);
        // Only manually update notification if NOT using the local Media3 ExoPlayer
        if (target == null || !ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(target.getTargetId())) {
            boolean isPlaying = state != null && state.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
            updateNotification(getApplicationContext(), state != null ? state.currentTrack : null, target, mediaHub.getStatus().getValue(), tagRepos.getTotalSongs(), isPlaying);
        }
    }

    @Override
    public void onPlaybackStateElapsedTime(long elapsedTimeMS) {
        apincer.music.core.playback.PlaybackState state = playbackStateFlow.getValue();
        if (state != null) {
            // Create a copy — StateFlow uses reference equality and silently drops
            // setValue() calls with the same object instance, even if fields changed.
            apincer.music.core.playback.PlaybackState updated = state.copy();
            updated.currentPositionSecond = elapsedTimeMS;
            playbackStateFlow.setValue(updated);
        }
    }

    @Override
    public AutoCloseable subscribePlaybackState(Consumer<PlaybackState> consumer, Consumer<Throwable> onErrorConsumer) {
        return flowSubscribe(playbackStateFlow, consumer, onErrorConsumer);
    }

    @Override
    public AutoCloseable subscribeNowPlayingSong(
            Consumer<Optional<Track>> onNextConsumer,
            Consumer<Throwable> onErrorConsumer
    ) {
        return flowSubscribe(currentTrackFlow, onNextConsumer, onErrorConsumer);
    }

    @Override
    public AutoCloseable subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer, Consumer<Throwable> onErrorConsumer) {
        return flowSubscribe(currentPlayerFlow, consumer, onErrorConsumer);
    }

    /** Lightweight Java-compatible StateFlow subscriber using the single-thread scheduler. */
    private <T> AutoCloseable flowSubscribe(
            StateFlow<T> flow,
            Consumer<T> onNext,
            Consumer<Throwable> onError) {
        // Emit current value immediately
        try { onNext.accept(flow.getValue()); } catch (Throwable t) {
            try { onError.accept(t); } catch (Exception ignored) {}
        }
        final java.util.concurrent.atomic.AtomicReference<T> lastRef =
                new java.util.concurrent.atomic.AtomicReference<>(flow.getValue());

        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            try {
                T current = flow.getValue();
                if (current != lastRef.get()) {
                    lastRef.set(current);
                    onNext.accept(current);
                }
            } catch (Throwable t) {
                try { onError.accept(t); } catch (Exception ignored) {}
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        return () -> task.cancel(false);
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

    @androidx.annotation.Nullable
    @Override
    public MediaLibrarySession onGetSession(androidx.media3.session.MediaSession.ControllerInfo controllerInfo) {
        return mediaLibrarySession;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && MediaLibraryService.SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return new MusicMateServiceImplBinder();
    }
}
