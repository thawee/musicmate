package apincer.android.mmate.service;

import static apincer.music.core.NotificationId.PLAYBACK_SERVICE;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import apincer.android.mmate.R;
import apincer.android.mmate.playback.ExternalPlayer;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.QueueItem;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.MediaServerHub;
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
public class PlaybackServiceImpl extends Service implements PlaybackService {
    private static final String TAG = "PlaybackServiceImpl";

    @Inject
    TagRepository tagRepos;

    @Inject
    MediaServerHub mediaServer;

    private MediaSessionManager mediaSessionManager;

    private final List<PlaybackTarget> availableTargets = new CopyOnWriteArrayList<>();
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

    private static final String CHANNEL_ID = "playback_service_channel";
    private NotificationManager notificationManager;

    // ==================== Media Session Monitoring ====================
    private final MediaController.Callback externalControllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@Nullable android.media.session.PlaybackState state) {
            if (state != null) {
                bridgeExternalStateToApp(state);
            }
        }

        @Override
        public void onMetadataChanged(@Nullable android.media.MediaMetadata metadata) {
            if (metadata != null) {
                bridgeExternalMetadataToApp(metadata);
            }
        }
    };

    private final MediaServerHub.Callback merderServerHubCallback = new MediaServerHub.Callback() {
        @Override
        public void onPlaybackTargetChanged(PlaybackTarget playbackTarget) {
            PlaybackServiceImpl.this.switchPlayer(playbackTarget, false);
        }

        @Override
        public void onMediaTrackChanged(MediaTrack metadata) {
            PlaybackServiceImpl.this.onMediaTrackChanged(metadata);
        }

        @Override
        public void onPlaybackStateChanged(apincer.music.core.playback.PlaybackState state) {
            PlaybackServiceImpl.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
            onPlaybackStateElapsedTime(elapsedSeconds);
        }

        @Override
        public void onPlaybackTargetAdded(PlaybackTarget player) {
            if (player == null || player.getTargetId() == null) {
                Log.w(TAG, "Attempted to add a null player or player with null ID.");
                return;
            }

            // Check if a player with the same ID already exists
            boolean alreadyExists = availableTargets.stream()
                    .anyMatch(target -> player.getTargetId().equals(target.getTargetId()));

            if (!alreadyExists) {
                availableTargets.add(player);
                Log.d(TAG, "Added new playback target: " + player.getDisplayName());
            } else {
                Log.d(TAG, "Playback target already exists, not adding: " + player.getDisplayName());
            }
        }

        @Override
        public void onPlaybackTargetRevoved(String targetId) {
            // Use removeIf to find and remove the item by its ID
            availableTargets.removeIf(target -> target.getTargetId().equals(targetId));

            // You also need to check if the removed player was the active one
            currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
                if (playbackTarget.getTargetId().equals(targetId)) {
                    currentPlayerSubject.onNext(Optional.empty());
                    Log.d(TAG, "Active player was removed: " + targetId);
                }
            });
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionChangeListener =
            controllers -> {
                Log.d(TAG, "Active sessions changed: " + controllers.size());
                updateAvailableTargets(controllers);
            };

    private PlaybackTarget getActivePlayer() {
        return currentPlayerSubject.getValue().orElse(null);
    }

    private void updateAvailableTargets(List<MediaController> controllers) {
        availableTargets.clear();

        // Add external media session targets
        for (MediaController controller : controllers) {
            String packageName = controller.getPackageName();
            String sessionTag = controller.getTag();
            PlaybackTarget player = ExternalPlayer.Factory.create(getApplicationContext(), controller, packageName, getAppName(packageName));
            availableTargets.add(player);
        }

        // Streaming players will be added via registerStreamingPlayer()
        Log.d(TAG, "Updated available targets: " + availableTargets.size());
    }


    private void bridgeExternalStateToApp(PlaybackState externalState) {
        apincer.music.core.playback.PlaybackState appState = new apincer.music.core.playback.PlaybackState();

        switch (externalState.getState()) {
            case PlaybackState.STATE_PLAYING:
                appState.currentState = apincer.music.core.playback.PlaybackState.State.PLAYING;
                break;
            case PlaybackState.STATE_PAUSED:
                appState.currentState = apincer.music.core.playback.PlaybackState.State.PAUSED;
                break;
            case PlaybackState.STATE_BUFFERING:
                appState.currentState = apincer.music.core.playback.PlaybackState.State.BUFFERING;
                break;
            default:
                appState.currentState = apincer.music.core.playback.PlaybackState.State.STOPPED;
        }

        appState.currentPositionSecond = externalState.getPosition();
        appState.currentTrack = currentTrackSubject.getValue().orElse(null);

        onPlaybackStateChanged(appState);
    }

    private void bridgeExternalMetadataToApp(android.media.MediaMetadata metadata) {
        String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
        long duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);

        MediaTrack track = tagRepos.findMediaItem(title, artist, album); //, duration);

        currentTrackSubject.onNext(Optional.of(track));
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    // ==================== Service Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();

        // Get the NotificationManager
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create the channel (it's safe to call this every time)
        createNotificationChannel();

        // Start foreground service immediately with the *initial* notification
        startForeground(PLAYBACK_SERVICE.getId(), createInitialNotification());

        // external player controller
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName notificationListener = new ComponentName(this, MediaNotificationListener.class);
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(notificationListener);
            updateAvailableTargets(controllers);
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionChangeListener, notificationListener);

            // Load queue from database
            loadPlayingQueue();

            // Auto-select first available controller if no streaming player
           // if (!controllers.isEmpty() && getActivePlayer() == null) {
           //     switchPlayer(controllers.get(0).getPackageName() + ":" + controllers.get(0).getTag());
           // }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing notification listener permission", e);
        }

        // media server/player controller
        if(mediaServer != null) {
            availableTargets.addAll(mediaServer.getAvailablePlaybackTargets());
        }
    }

    // It just creates the *first* notification shown before anything is loaded.
    private Notification createInitialNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Playback")
                .setContentText("Monitoring media sessions")
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Change to your app's icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Playback Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Manages media playback");
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Builds and displays the dynamic notification based on the current active player.
     */
    private void updateNotification(
            @Nullable MediaTrack track,
            @Nullable PlaybackTarget player) {

        NotificationCompat.Builder builder;

        // Check if we have an active player
        if (player == null) {
            // No player selected, show the initial generic notification
            builder = createInitialNotificationBuilder();

        } else if (player.isStreaming()) {
            // CASE 1: We are *controlling* a streaming player (e.g., DLNA/UPnP)
            // This notification should show track info and media controls.
            builder = createStreamingControlNotification(track, player);

        } else {
            // CASE 2: We are *monitoring* an external player (e.g., Spotify)
            // This notification *only* shows status to avoid conflicts.
            builder = createMonitoringNotification(player);
        }

        // Update the existing foreground notification
        notificationManager.notify(PLAYBACK_SERVICE.getId(), builder.build());
    }

    /**
     * Builds the simple, generic notification for when the service is
     * running but no player is selected.
     */
    private NotificationCompat.Builder createInitialNotificationBuilder() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Playback Service")
                .setContentText("Monitoring media sessions")
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Change to your app's icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
    }

    /**
     * Builds the notification for *monitoring* an external player.
     * This fulfills your request: it's rich with *player* info, not track info.
     */
    private NotificationCompat.Builder createMonitoringNotification(PlaybackTarget player) {

        // 1. Get Display Name
        String displayName = player.getDisplayName();

        // 2. Get Player Type
        String playerType = "External Player"; // Since it's not isStreaming()

        // 3. Get Control Status
        String controlStatus = "Monitoring only (App controls playback)";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Change to a "link" or "cast" icon
                .setContentTitle("Monitoring: " + displayName)
                .setContentText(playerType + " | " + controlStatus)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
    }

    /**
     * Builds the notification for *controlling* a streaming player.
     * This one shows track info and media controls.
     */
    private NotificationCompat.Builder createStreamingControlNotification(
            @Nullable MediaTrack track,
            PlaybackTarget player) {

        String title = player.getDisplayName();
        String text = "Ready to play";
        int smallIcon = R.drawable.ic_notification_default;

        if (track != null) {
            title = track.getTitle();
            text = "by "+track.getArtist() + " • on " + player.getDisplayName();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW) // You might want PRIORITY_DEFAULT
                .setOngoing(true);
        return builder;
    }

    @Override
    public void onDestroy() {
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionChangeListener);
        }
        deactivatePlayer(getActivePlayer());
        super.onDestroy();
    }

    private void deactivatePlayer(PlaybackTarget player) {
        if(player != null) {
            if(player instanceof ExternalPlayer externalPlayer) {
                externalPlayer.unregisterCallback(externalControllerCallback);
            }else if (player.isStreaming()){
                mediaServer.deactivatePlayer(player.getTargetId());
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
                // external player
                playOnExternalPlayer(playbackTarget, song);
            }
        });
    }

    private void playOnExternalPlayer(PlaybackTarget playbackTarget, MediaTrack song) {
    }

    @Override
    public void playNext() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                playNextOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                playNextOnExternalPlayer(playbackTarget);
            }
        });
    }

    private void playNextOnExternalPlayer(PlaybackTarget playbackTarget) {
        if(playbackTarget instanceof ExternalPlayer player) {
            player.getMediaController().getTransportControls().skipToNext();
        }
    }

    private void playNextOnStreamingPlayer(PlaybackTarget playbackTarget) {
       // mediaServer.skipToNext(playbackTarget.getTargetId());
    }

    @Override
    public void playPrevious() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                playPreviousOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                playPreviousOnExternalPlayer(playbackTarget);
            }
        });
    }

    private void playPreviousOnExternalPlayer(PlaybackTarget playbackTarget) {
    }

    private void playPreviousOnStreamingPlayer(PlaybackTarget playbackTarget) {
    }

    public void pause() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                pauseOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                pauseOnExternalPlayer(playbackTarget);
            }
        });
    }

    private void pauseOnExternalPlayer(PlaybackTarget playbackTarget) {
    }

    private void pauseOnStreamingPlayer(PlaybackTarget playbackTarget) {
    }

    @Override
    public void stop() {
        currentPlayerSubject.getValue().ifPresent(playbackTarget -> {
            if (isControllable()) {
                topOnStreamingPlayer(playbackTarget);
            } else {
                // external player
                stopOnExternalPlayer(playbackTarget);
            }
        });
    }

    private void stopOnExternalPlayer(PlaybackTarget playbackTarget) {
    }

    private void topOnStreamingPlayer(PlaybackTarget playbackTarget) {
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
        return new ArrayList<>(availableTargets);
    }

    @Override
    public PlaybackTarget getPlayer() {
       // if (getActivePlayer() != null) {
            return getActivePlayer();
       // }
       // return availableTargets.stream()
       //         //.filter(PlaybackTarget::isActive)
       //         .findFirst()
       //         .orElse(null);
    }

    @Override
    public void switchPlayer(String targetId, boolean controlled) {
        //if (targetId.startsWith(STREAMING_PLAYER_PREFIX)) {
        // Find and activate streaming player
        PlaybackTarget newTarget = availableTargets.stream()
                .filter(target -> target.getTargetId().equals(targetId))
                .findFirst()
                .orElse(null);

        switchPlayer(newTarget, controlled);
    }

    @Override
    public void switchPlayer(PlaybackTarget newTarget, boolean controlled) {
        if (newTarget != null) {
            if(newTarget instanceof ExternalPlayer externalPlayer){
                externalPlayer.registerCallback(externalControllerCallback);
            }else if (newTarget.isStreaming()) {
                // replace http stream with real streaming device
                newTarget = resolveStreamingPlayerTarget(newTarget);
                mediaServer.activatePlayer(newTarget.getTargetId(), merderServerHubCallback);
            }
            if(controlled) {
                this.controlledPlayerTargetId = newTarget.getTargetId();
            }
            currentPlayerSubject.getValue().ifPresent(this::deactivatePlayer);
            currentPlayerSubject.onNext(Optional.of(newTarget));
            updateNotification(null, newTarget);
            Log.d(TAG, "Switched to player: " + newTarget.getTargetId());
        }
    }

    private PlaybackTarget resolveStreamingPlayerTarget(PlaybackTarget player) {
        if(!player.isStreaming()) return player;

        for(PlaybackTarget dev: availableTargets) {
            if(dev.isStreaming()
                   // && dev.getTargetId().equals(dev.getDescription()) // default on from http request header
                    && player.getDescription().equals(dev.getDescription())) {
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
            List<QueueItem> queues = tagRepos.getQueueItems();
            for (QueueItem queueItem : queues) {
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

    @Override
    public MediaTrack getNowPlayingSong() {
        return currentTrackSubject.getValue().orElse(null);
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
        updateNotification(state.currentTrack, currentPlayerSubject.getValue().orElse(null));
    }

    @Override
    public void onPlaybackStateElapsedTime(long elapsedTimeMS) {
        apincer.music.core.playback.PlaybackState state = playbackStateSubject.getValue();
        if (state != null) {
            state.currentPositionSecond = elapsedTimeMS;
            playbackStateSubject.onNext(state);
        }
    }

    @SuppressLint("CheckResult")
    @Override
    public void subscribePlaybackState(Consumer<apincer.music.core.playback.PlaybackState> consumer) {
        playbackStateSubject.subscribe(consumer);
    }

    @SuppressLint("CheckResult")
    @Override
    public void subscribeNowPlayingSong(Consumer<Optional<MediaTrack>> consumer) {
        currentTrackSubject.subscribe(consumer);
    }

    @SuppressLint("CheckResult")
    @Override
    public void subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer) {
        currentPlayerSubject.subscribe(consumer);
    }

    @Override
    public String getServerLocation() {
        return null; // Not needed for monitoring/control service
    }

    // ==================== Binder ====================

    public class PlaybackServiceBinder extends Binder implements apincer.music.core.playback.spi.PlaybackServiceBinder {
        public PlaybackService getService() {
            return PlaybackServiceImpl.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlaybackServiceBinder();
    }

    public static void startPlaybackService(Context context) {
        Log.d(TAG, "Start PlaybackService requested");
        Intent intent = new Intent(context, PlaybackServiceImpl.class);
        try {
            context.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting PlaybackService ", e);
        }
    }
}
