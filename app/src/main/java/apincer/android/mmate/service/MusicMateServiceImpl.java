package apincer.android.mmate.service;

import static apincer.android.mmate.service.MediaNotificationBuilder.updateNotification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.PlayingQueue;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.service.spi.MusicMateServiceBinder;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * Media session monitoring service that:
 * 1. Discovers all active media sessions (external apps + streaming players)
 * 2. Controls external players (Spotify, YouTube Music, etc.) and streaming players (DmcPlayer)
 * 3. Bridges external playback state to streaming player targets
 * 4. Manages queue for streaming player playback
 *
 * NO internal ExoPlayer - only monitors and controls external apps and streaming targets.
 */
@AndroidEntryPoint
public class MusicMateServiceImpl extends Service implements PlaybackService {
    private static final String TAG = "MusicMateServiceImpl";
    public static final String CHANNEL_ID = "musicmate_service_channel";
    public static final int SERVICE_ID = 1;

    public static final String ACTION_PLAY_PAUSE = "apincer.musicmate.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "apincer.musicmate.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "apincer.musicmate.ACTION_PREVIOUS";
    public static final String ACTION_STOP = "apincer.musicmate.ACTION_STOP";

    public static final String ONLINE_STATUS = "Online"; // Green circle for Online
    public static final String OFFLINE_STATUS = "Offline"; // Red circle for Offline

    @Inject
    TagRepository tagRepos;

    @Inject
    MediaServerHub mediaServer;

    private AndroidPlayerController androidPlayer;

    // -- SERVICE --
    private MediaSessionManager mediaSessionManager;

    private WifiManager wifiManager;
    private PowerManager powerManager;
    private ConnectivityManager connectivityManager;

    // Locks to keep the CPU and WiFi active for stable streaming
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;

    // Network monitoring for the 30-minute auto-stop failsafe
    private final Handler networkHandler = new Handler(Looper.getMainLooper());
    private Runnable stopServerRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    // -- STATE -->
    private List<MusicTag> playingQueue = new CopyOnWriteArrayList<>();

    private final BehaviorSubject<apincer.music.core.playback.PlaybackState> playbackStateSubject =
            BehaviorSubject.createDefault(new apincer.music.core.playback.PlaybackState());
    private final BehaviorSubject<Optional<MediaTrack>> currentTrackSubject =
            BehaviorSubject.createDefault(Optional.empty());
    private final BehaviorSubject<Optional<PlaybackTarget>> currentPlayerSubject =
            BehaviorSubject.createDefault(Optional.empty());
    private final BehaviorSubject<List<MusicTag>> playingQueueSubject =
            BehaviorSubject.createDefault(new ArrayList<>());

    private String controlledPlayerTargetId;

    // The Service is now the single source of truth for its status.
    private final MutableLiveData<MediaServerHub.ServerStatus> statusLiveData = new MutableLiveData<>(MediaServerHub.ServerStatus.STOPPED);

    private final PlaybackCallback playbackCallback = new PlaybackCallback() {

        @Override
        public void onMediaTrackChanged(MediaTrack metadata) {
            MusicMateServiceImpl.this.onMediaTrackChanged(metadata);
        }

        @Override
        public void onMediaTrackChanged(String title, String artist, String album, long duration) {
            MediaTrack song = tagRepos.findMediaItem(title, artist, album);
            if(song != null) {
                MusicMateServiceImpl.this.onMediaTrackChanged(song);
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
                Log.d(TAG, "Active sessions changed: " + controllers.size());
                updateAvailableExternalPlayers(controllers);
            };

    private PlaybackTarget getActivePlayer() {
        return currentPlayerSubject.getValue().orElse(null);
    }

    private void updateAvailableExternalPlayers(List<MediaController> controllers) {
        // Remove all existing external Players
        if (getAvailablePlaybackTargets() != null && !getAvailablePlaybackTargets().isEmpty()) {
            getAvailablePlaybackTargets().removeIf(target -> target instanceof ExternalAndroidPlayer);
        }

        // Add external media session targets
        for (MediaController controller : controllers) {
            String packageName = controller.getPackageName();
           // String sessionTag = controller.getTag();
            PlaybackTarget player = ExternalAndroidPlayer.Factory.create(getApplicationContext(), packageName);
            addPlaybackTarget(player);
        }

        // Streaming players will be added via registerStreamingPlayer()
        Log.d(TAG, "Updated available targets: " + getAvailablePlaybackTargets().size());
    }

    private void addPlaybackTarget(PlaybackTarget player) {
        // 1. Safety check for the new player and its ID
        if (player == null || player.getTargetId() == null) {
            return;
        }

        String newTargetId = player.getTargetId();
        List<PlaybackTarget> targets = getAvailablePlaybackTargets();

        // 2. Check for an existing player with the same ID
        for (PlaybackTarget existingPlayer : targets) {
            if (existingPlayer != null && newTargetId.equals(existingPlayer.getTargetId())) {
                // A player with this ID already exists, so don't add.
                return;
            }
        }

        // 3. If the loop completes, no match was found. Add the new player.
        targets.add(player);
    }

    // ==================== Service Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();
        // must create notification within 5-10 seconds
        // Create the channel (it's safe to call this every time)
        //if(!isNotificationActive) {
         //   createNotificationChannel();

        // Start foreground service immediately with the *initial* notification
        startForeground(SERVICE_ID, createInitialNotification());
        //    isNotificationActive = true;
       // }

        this.wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        this.powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
       // this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        getStatusLiveData().observeForever(status -> updateNotification(getApplicationContext(),null, null, mediaServer.getServerStatus().getValue(), tagRepos.getTotalSongs()));

        // initial dmr/local player player
        //getAvailablePlaybackTargets().addAll(mediaServer.getAvailablePlaybackTargets());
        // Iterate and add using the duplicate-checking method
        List<PlaybackTarget> serverTargets = mediaServer.getAvailablePlaybackTargets();
        for (PlaybackTarget player : serverTargets) {
            addPlaybackTarget(player);
        }

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
            loadPlayingQueue();
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
                    skipToNext();
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
        return START_STICKY;
    }

    private void handlePlayPause() {
    }

    public void startServers() {
        if (mediaServer.isInitialized()) return;

      /*  if(!isNotificationActive) {
          //  createNotificationChannel();

            // Start foreground service immediately with the *initial* notification
            startForeground(SERVICE_ID, createInitialNotification());
            isNotificationActive = true;
        } */

        if (!NetworkUtils.isWifiConnected(this)) {
            statusLiveData.postValue(MediaServerHub.ServerStatus.ERROR);
            Log.d(TAG, TAG+" - Error, Required WiFi network");
            return;
        }

        // --- ACQUIRE RESOURCES ---
        // Wake lock keeps the CPU from sleeping. A timeout is used as a safeguard.
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusixMate:MediaServerWakeLock");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        // WifiLock keeps the WiFi radio from turning off, crucial for streaming.
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MusixMate:WifiLock");
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        // MulticastLock is required for device discovery (DLNA/UPnP).
        multicastLock = wifiManager.createMulticastLock("MusixMate:MulticastLock");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();
        Log.d(TAG, "CPU, Wi-Fi, and Multicast locks acquired.");

        // --- START SERVICES ---
        //showNotification(null);
        mediaServer.startServers();
        startNetworkMonitoring();

        // Report that the server is now running.
        statusLiveData.postValue(MediaServerHub.ServerStatus.RUNNING);
    }

    public void stopServers() {
        if (!mediaServer.isInitialized()) return;

        // --- RELEASE RESOURCES ---
        // Always cancel any pending stop command first
        networkHandler.removeCallbacks(stopServerRunnable);
        stopNetworkMonitoring();
        mediaServer.stopServers();
        //  cancelNotification();

        // Report that the server has stopped.
        statusLiveData.postValue(MediaServerHub.ServerStatus.STOPPED);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        Log.d(TAG, "All locks released and server stopped.");
    }

    /**
     * Sets up network monitoring. This will not start the server, but will
     * stop it after 30 minutes if the WiFi connection is lost.
     */
    @SuppressLint("MissingPermission")
    private void startNetworkMonitoring() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        stopServerRunnable = () -> {
            Log.w(TAG, "WiFi has been disconnected for 30 minutes. Stopping server to save battery.");
            if (mediaServer.isInitialized()) {
                stopServers();
            }
        };

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // WiFi is connected. Cancel any pending shutdown command.
                Log.d(TAG, "WiFi connection available. Cancelling stop timer.");
                networkHandler.removeCallbacks(stopServerRunnable);
            }

            @Override
            public void onLost(@NonNull Network network) {
                // WiFi is lost. Schedule a shutdown in 30 minutes.
                Log.d(TAG, "WiFi connection lost. Server will stop in 30 minutes.");
                networkHandler.postDelayed(stopServerRunnable, 1800000L); // 30 minutes
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private void stopNetworkMonitoring() {
        // Unregister the callback to prevent leaks.
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null; // Clear the reference
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering network callback", e);
            }
        }
    }

    // It just creates the *first* notification shown before anything is loaded.
    private Notification createInitialNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Monitoring media sessions")
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Change to your app's icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }


    private boolean isPlaying() {
        return playbackStateSubject.getValue().currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
    }

    @Override
    public void onDestroy() {
        // Ensure everything is cleaned up if the service is destroyed.
        stopServers();
        mediaServer.onDestroy();

        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionChangeListener);
        }
        deactivatePlayer(getActivePlayer());
        super.onDestroy();
    }

    private void deactivatePlayer(PlaybackTarget player) {
        if(player != null) {
            if (player.isStreaming()){
                mediaServer.deactivatePlayer(player.getTargetId());
            }else {
                androidPlayer.unregisterCallback();
            }
        }
    }

    // ==================== Playback Control (Unified) ====================

    @Override
    public void play(MediaTrack song) {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                playOnStreamingPlayer(playbackTarget, song);
            } else {
                androidPlayer.play(song);
            }
        });
    }


    @Override
    public void skipToNext() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                androidPlayer.skipToNext();
            } else {
                // external player
                playNextOnExternalPlayer(playbackTarget);
            }
        });
    }

    private void playNextOnExternalPlayer(PlaybackTarget playbackTarget) {
        if(playbackTarget instanceof ExternalAndroidPlayer player) {
            androidPlayer.skipToNext();
        }
    }

    @Override
    public void skipToPrevious() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                playPreviousOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.skipToPrevious();
            }
        });
    }

    private void playPreviousOnStreamingPlayer(PlaybackTarget playbackTarget) {
    }

    public void pause() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                pauseOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.pause();
            }
        });
    }

    private void pauseOnStreamingPlayer(PlaybackTarget playbackTarget) {
    }

    @Override
    public void stopPlaying() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                stopOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                androidPlayer.stopPlaying();
            }
        });
    }

    private void stopOnStreamingPlayer(PlaybackTarget playbackTarget) {
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

    private void playOnStreamingPlayer(PlaybackTarget player, MediaTrack song) {
        if (song != null) {
            mediaServer.playSong(player.getTargetId(), song);
            onMediaTrackChanged(song);
        }
    }

    // ==================== Player Switching ====================

    @Override
    public List<PlaybackTarget> getAvailablePlaybackTargets() {
        return mediaServer.getAvailablePlaybackTargets();
    }

    @Override
    public PlaybackTarget getPlayer() {
        return getActivePlayer();
    }

    @Override
    public void switchPlayer(String targetId, boolean controlled) {
        //if (targetId.startsWith(STREAMING_PLAYER_PREFIX)) {
        // Find and activate streaming player
        PlaybackTarget newTarget = getAvailablePlaybackTargets().stream()
                .filter(target -> target.getTargetId().equals(targetId))
                .findFirst()
                .orElse(null);

        switchPlayer(newTarget, controlled);
    }

    @Override
    public void switchPlayer(PlaybackTarget newTarget, boolean controlled) {
        if (newTarget != null) {
            Log.d(TAG, "Switched to player: " + newTarget.getTargetId());

            // --- TODO IMPLEMENTED ---
            // Get the current (soon-to-be-previous) player and unregister
            // the callback if it's an external one.
            //Optional<PlaybackTarget> oldTargetOpt = currentPlayerSubject.getValue();
           // oldTargetOpt.ifPresent(oldTarget -> {
            //    if (oldTarget instanceof ExternalPlayer oldExternalPlayer) {
            //        Log.d(TAG, "Unregistering callback from previous player: " + oldTarget.getTargetId());
                    androidPlayer.unregisterCallback();
            //    }
           // });
            // --- END ---

            if(newTarget instanceof ExternalAndroidPlayer externalPlayer){
                // Register callback to the new external player
                Log.d(TAG, "Registering callback to new ExternalPlayer: " + newTarget.getTargetId());
               // MediaController mediaController = getMediaController(newTarget.getTargetId());
                androidPlayer.registerCallback(externalPlayer, playbackCallback);
            }else if (newTarget.isStreaming()) {
                // replace http stream with real streaming device
                newTarget = resolveStreamingPlayerTarget(newTarget);
                mediaServer.activatePlayer(newTarget.getTargetId(), playbackCallback);
            }

            if(controlled) {
                this.controlledPlayerTargetId = newTarget.getTargetId();
            }

            // This existing call will handle other deactivation logic (like for streaming)
            currentPlayerSubject.getValue().ifPresent(this::deactivatePlayer);

            currentPlayerSubject.onNext(Optional.of(newTarget));
            updateNotification(getApplicationContext(), null, newTarget, mediaServer.getServerStatus().getValue(), tagRepos.getTotalSongs());
        }
    }

    private PlaybackTarget resolveStreamingPlayerTarget(PlaybackTarget player) {
        if(!player.isStreaming()) return player;
        if(player.getDescription() == null) return player;

        //Log.d(TAG, "resolve streaming player: " + player.getTargetId() +" :: "+getAvailablePlaybackTargets().size());
        for(PlaybackTarget dev: getAvailablePlaybackTargets()) {
            if(dev!=null && dev.isStreaming()
                    && player.getDescription().equals(dev.getDescription())) {
               // Log.d(TAG, "resolve player:"+ dev.getTargetId()+" - "+dev.getDisplayName());
                return dev;
            }
        }
        return player;
    }

    public boolean isControllable() {
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

    // ==================== Queue Management ====================

    private void loadPlayingQueue() {
        try {
            List<MusicTag> queueList = new CopyOnWriteArrayList<>();
            List<PlayingQueue> queues = tagRepos.getQueueItems();
            for (PlayingQueue queueItem : queues) {
                queueList.add(queueItem.getTrack());
            }

            playingQueue = queueList;
            playingQueueSubject.onNext(playingQueue);
            Log.d(TAG, "Loaded queue from DB with size: " + playingQueue.size());
        } catch (Exception e) {
            Log.e(TAG, "Error loading playing queue", e);
        }
    }

    @Override
    public void loadPlayingQueue(MediaTrack song) {
        loadPlayingQueue();

        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable() && song != null) {
                // Find song in queue and play
                int startIndex = 0;
                for (int i = 0; i < playingQueue.size(); i++) {
                    if (playingQueue.get(i).getId() == song.getId()) {
                        startIndex = i;
                        break;
                    }
                }

                if (startIndex < playingQueue.size()) {
                    playOnStreamingPlayer(playbackTarget, playingQueue.get(startIndex));
                }
            }
        });
    }

    /**
     * Automatically selects the best available player based on a predefined priority.
     *
     * Priority Order:
     * 1. DMRPlayer (DLNA/UPnP, target.isStreaming() and target.canReadSate())
     * 2. WebStreaming Player (target.isStreaming())
     * 3. ExternalPlayer (MediaSession-based)
     *
     * @return An Optional containing the highest-priority player found, or Optional.empty() if no suitable player is available.
     */
    public Optional<PlaybackTarget> autoSelectBestPlayer() {
        if (getAvailablePlaybackTargets() == null || getAvailablePlaybackTargets().isEmpty()) {
            return Optional.empty();
        }

        PlaybackTarget webStreamingFallback = null;
        PlaybackTarget externalPlayerFallback = null;

        // We iterate once to find the best match
        for (PlaybackTarget target : getAvailablePlaybackTargets()) {
            if(target == null) continue;

            // --- Priority 1: DMCA / DMR Player ---
            // (Replace 'DMRPlayer' with your actual DLNA/UPnP player class name)
            if (target.isStreaming() && target.canReadSate()) {
                Log.d(TAG, "Auto-select: Found high-priority DMRPlayer: " + target.getTargetId());
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
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy web assets", e);
        }
    }

    //
    @Override
    public MediaTrack getNowPlayingSong() {
        return currentTrackSubject.getValue().orElse(null);
    }

    public String getLibraryName() {
        return mediaServer.getLibraryName();
    }

    public boolean isRunning() {
        return mediaServer.isInitialized();
    }

    /**
     * Allows clients (like MediaServerManager) to observe the service's status.
     */
    public LiveData<MediaServerHub.ServerStatus> getStatusLiveData() {
        return statusLiveData;
    }

    // ==================== State Notifications ====================

    @Override
    public void onMediaTrackChanged(MediaTrack song) {
        currentTrackSubject.onNext(Optional.ofNullable(song));

        apincer.music.core.playback.PlaybackState state = new apincer.music.core.playback.PlaybackState();
        state.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionSecond = 0;
        onPlaybackStateChanged(state);
    }

    @Override
    public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
        playbackStateSubject.onNext(state);
        updateNotification(getApplicationContext(), state.currentTrack, currentPlayerSubject.getValue().orElse(null), mediaServer.getServerStatus().getValue(), tagRepos.getTotalSongs());
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
    public void subscribePlaybackState(Consumer<PlaybackState> consumer, Consumer<Throwable> onErrorConsumer) {
        playbackStateSubject.subscribe(consumer, onErrorConsumer);
    }

    @Override
    public void subscribeNowPlayingSong(
            Consumer<Optional<MediaTrack>> onNextConsumer,
            Consumer<Throwable> onErrorConsumer
    ) {
        // Use the subscribe overload that takes both
        currentTrackSubject.subscribe(onNextConsumer, onErrorConsumer);
    }

    @Override
    public void subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer, Consumer<Throwable> onErrorConsumer) {
        currentPlayerSubject.subscribe(consumer, onErrorConsumer);
    }

    @Override
    public String getServerLocation() {
        return ""; // Not needed for monitoring/control service
    }

    // ==================== Binder ====================

    public class MusicMateServiceImplBinder extends Binder implements MusicMateServiceBinder {
        @Override
        public PlaybackService getPlaybackService() {
            return MusicMateServiceImpl.this;
        }
        @Override
        public MediaServerHub getMediaServerHub() {
            return mediaServer;
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
