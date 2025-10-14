package apincer.android.mmate.service;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import apincer.android.mmate.core.server.IMediaServer;
import apincer.android.mmate.core.server.RendererDevice;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.database.QueueItem;
import apincer.android.mmate.core.playback.IPlaybackService;
import apincer.android.mmate.core.playback.NowPlaying;
import apincer.android.mmate.core.playback.Player;
import apincer.android.mmate.core.repository.TagRepository;
import apincer.android.mmate.worker.PlayNextSongWorker;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

// This service is a Started Service, meaning it runs in the background
// to handle playback actions and then stops itself when the work is done.
// It is a single-purpose service for managing playback commands.

@AndroidEntryPoint
public class PlaybackService extends Service implements IPlaybackService {
    private static final String TAG = "PlaybackManagerService";
    private static final String NOTIFICATION_CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 1;

    // Define custom action strings for the Intent
    public static final String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    public static final String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    public static final String ACTION_PLAY_NEXT = "apincer.android.mmate.playback.ACTION_PLAY_NEXT";
    public static final String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";

    // Intent Extra keys
    public static final String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";
    public static final String EXTRA_UDN = "EXTRA_UDN";
    private static final String UNIQUE_PLAYBACK_WORK_NAME = "PLAYBACK_PLAY_NEXT_WORK_NAME";

    private final BehaviorSubject<NowPlaying> nowPlayingSubject = BehaviorSubject.createDefault(new NowPlaying());
    private transient boolean controlledPlayback = false;
    private long currentTrackId = -1;

    private final BehaviorSubject<List<MusicTag>> playingQueueSubject = BehaviorSubject.createDefault(new CopyOnWriteArrayList<>());
    private boolean isShuffleMode = false;

    private int playingQueueIndex = -1;
    // The original, unshuffled queue from your Subject
    private List<MusicTag> originalQueue;

    // A list to hold the shuffled order of indices
    private List<Integer> shuffledIndices;

    // The current position in the playback sequence (either sequential or shuffled)
    private int currentPlayPosition = -1; // Start at -1 so the first song is at index 0

    // New fields for service binding
    private IMediaServer mediaServer;
    private boolean isBound = false;

    private boolean isNextTrackScheduled = false;

    @Inject
    TagRepository tagRepos;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MediaServerHostingService.MediaServerBinder binder = (MediaServerHostingService.MediaServerBinder) service;
            mediaServer = binder.getMediaServer();
            mediaServer.setPlaybackService(PlaybackService.this);
            isBound = true;
            Log.i(TAG, "MediaServerService bound successfully.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            mediaServer = null;
            Log.w(TAG, "MediaServerService disconnected unexpectedly.");
        }
    };

    @SuppressLint("CheckResult")
    @Override
    public void onCreate() {
        super.onCreate();
        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(this, MediaServerHostingService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

/*
        Object singletonComponent = EntryPoints.get(getApplicationContext(), RepositoryEntryPoint.class);
        Log.d("HiltDebug", "Component class: " + singletonComponent.getClass().getName());

        RepositoryEntryPoint entryPoint = EntryPointAccessors.fromApplication(
                getApplicationContext(),
                RepositoryEntryPoint.class
        );
        tagRepos = entryPoint.tagRepository(); */

        startForeground(NOTIFICATION_ID, buildForegroundNotification().build());
        loadPlayingQueue();
        nowPlayingSubject.subscribe(this::monitorNowPlaying);
    }

    /**
     * Loads a new playing queue and sets the starting position to the provided song.
     * @param song The song to start playing from within the new queue.
     */
    public void loadPlayingQueue(MusicTag song) {
        // 1. Load the entire queue of songs.
        // This overloaded method should fetch the full list and update your playingQueueSubject.
        loadPlayingQueue();

        // 2. Get the newly loaded list from the subject.
        List<MusicTag> newQueue = playingQueueSubject.getValue();

        // 3. Check if the queue is valid.
        if (newQueue == null || newQueue.isEmpty()) {
            Log.e("PlaybackService", "Failed to load a valid playing queue.");
            // Handle this error state appropriately, maybe by stopping playback.
            currentPlayPosition = -1;
            return;
        }

        // 4. Find the position of the starting song within the new queue.
        int startingPosition = newQueue.indexOf(song);

        // 5. Set the current play position.
        if (startingPosition != -1) {
            // The song was successfully found in the list.
            currentPlayPosition = startingPosition;
        } else {
            // Fallback: If for some reason the song isn't in the new queue,
            // it's safest to just start from the beginning.
            Log.w("PlaybackService", "Starting song not found in the new queue. Defaulting to position 0.");
            currentPlayPosition = 0;
        }

        Log.d("PlaybackService", "New queue loaded. Size: " + newQueue.size() + ", Starting at position: " + currentPlayPosition);
    }

    private void loadPlayingQueue() {
        try {
           // List<QueueItem> queues = MusixMateApp.getInstance().getOrmLite().getQueueItemDao().queryForAll();
            playingQueueSubject.getValue().clear();
            List<QueueItem> queues = tagRepos.getQueueItems();
            queues.forEach(queueItem -> playingQueueSubject.getValue().add(queueItem.getTrack()));
            originalQueue = playingQueueSubject.getValue();
            setShuffleMode(false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Playback Notifications",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder buildForegroundNotification() {
        createNotificationChannel();
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Where music is more than just a sound.")
                //.setContentText("Monitoring song playback.")
                .setSmallIcon(R.drawable.round_play_circle_outline_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY) // Assign the group ID
                .setGroupSummary(true); // This makes it the summary notification
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received action: " + intent.getAction());

        // Check if the Intent is null, which can happen if the service is restarted
        // by the system (e.g., after being killed in low memory situations).

        // Use a switch statement to handle different playback actions based on the Intent's action string.
        String action = intent.getAction();
        if (action == null) {
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_PLAY:
                MusicTag song = getSong(intent);
                if (song != null) {
                    play(song);
                }
                break;
            case ACTION_SKIP_TO_NEXT:
                MusicTag oldSong = getSong(intent);
                if (oldSong != null) {
                    skipToNext(oldSong);
                }
                break;
            case ACTION_PLAY_NEXT:
                playNext();
                break;
            case ACTION_SET_DLNA_PLAYER:
                String udn = intent.getStringExtra(EXTRA_UDN);
                if (udn != null) {
                    setActiveDlnaPlayer(udn);
                }
                break;
            default:
                Log.w(TAG, "Unknown action received: " + action);
                break;
        }

        // The service should stop itself after handling the command.
        // We use stopSelf(startId) to ensure we only stop the service once the
        // work for the current start command is complete.
        stopSelf(startId);

        // We return START_NOT_STICKY because we don't want the service to be
        // automatically recreated and re-delivered a null intent if the system
        // kills it.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This is a "started" service, not a "bound" one, so we return null.
        // There is no component that needs to bind to this service for direct communication.
        return new PlaybackServiceBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unbind from the MediaServerService to prevent a service connection leak.
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    /**
     * Skips to the next song in the playback queue.
     * @param song The MusicTag object representing the song to play next.
     */
    public void skipToNext(MusicTag song) {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if (nowPlaying != null && nowPlaying.isPlaying(song)) {
                if (nowPlaying.isLocalPlayer()) {
                    nowPlaying.skipToNext(song);
                } else {
                    playNext();
                }
        }
    }

    /**
     * Plays a specific song.
     * This method is used when playing a single song, not a queue
     * @param song The MusicTag object representing the song to play.
     */
    public void play(MusicTag song) {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if (nowPlaying != null) {
            controlledPlayback = true;
            nowPlaying.play(song);
            nowPlaying.setElapsed(0);
            nowPlayingSubject.onNext(nowPlaying);
            mediaServer.startPolling(nowPlaying.getPlayer().getId());
        }
        // This is the single line of code to cancel the work.
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(UNIQUE_PLAYBACK_WORK_NAME);
    }

    /**
     * Plays the next song in the current queue.
     */
    public void playNext() {
        Log.d(TAG, "play next song: controlledPlayback="+controlledPlayback+", playingQueueIndex="+playingQueueIndex);
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        List<MusicTag> currentQueue = playingQueueSubject.getValue();

        // Ensure we have something to play
        if (nowPlaying == null || currentQueue == null || currentQueue.isEmpty()) {
            Log.w(TAG, "Cannot play next song, nowPlaying or queue is null/empty.");
            return;
        }

        if (controlledPlayback) {
            int queueSize = originalQueue.size();

            // Move to the next position in the sequence
            currentPlayPosition++;

            // If we've reached the end of the queue, reset and re-shuffle if needed
            if (currentPlayPosition >= queueSize) {
                currentPlayPosition = 0;
                if (isShuffleMode) {
                    // Re-shuffle for the next cycle
                    generateShuffledList();
                }
            }

            int songIndexToPlay;
            if (isShuffleMode) {
                // On the first play or if list changed, shuffledIndices might be null/invalid
                if (shuffledIndices == null || shuffledIndices.size() != queueSize) {
                    generateShuffledList();
                    currentPlayPosition = 0;
                }
                // Get the song index from our shuffled list
                songIndexToPlay = shuffledIndices.get(currentPlayPosition);
            } else {
                // In sequential mode, the position is the song index
                songIndexToPlay = currentPlayPosition;
            }

            // Final check to prevent crash if index is somehow out of bounds
            if (songIndexToPlay >= 0 && songIndexToPlay < queueSize) {
                MusicTag song = originalQueue.get(songIndexToPlay);
                nowPlaying.play(song);
                nowPlaying.setElapsed(0);
                nowPlayingSubject.onNext(nowPlaying);
                mediaServer.startPolling(nowPlaying.getPlayer().getId());
            } else {
                Log.e(TAG, "Error: songIndexToPlay is out of bounds!");
            }

        } else {
            nowPlaying.next();
        }
    }

    /**
     * Generates a new shuffled order of indices based on the originalQueue.
     */
    private void generateShuffledList() {
        if (originalQueue == null || originalQueue.isEmpty()) {
            shuffledIndices = new ArrayList<>();
            return;
        }
        // Create a list of indices, e.g., [0, 1, 2, 3] for a 4-song queue
        shuffledIndices = java.util.stream.IntStream.range(0, originalQueue.size())
                .boxed()
                .collect(java.util.stream.Collectors.toList());

        // Shuffle this list of indices
        Collections.shuffle(shuffledIndices);
        Log.d(TAG, "Generated new shuffled list.");
    }

    /**
     * Sets the active DLNA player using its Unique Device Name (UDN).
     * @param udn The UDN of the DLNA renderer.
     */
    @Override
    public void setActiveDlnaPlayer(String udn) {
        if(isBound) {
            RendererDevice remoteDevice = mediaServer.getRendererByUDN(udn);
            if (remoteDevice != null) {
                Player player = Player.Factory.create(getApplicationContext(), remoteDevice);
                setActivePlayer(player);
                mediaServer.startPolling(remoteDevice.getUdn());
            }
        }
    }

    @Override
    public void setNowPlayingElapsedTime(long elapsedTime) {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if(nowPlaying != null) {
            nowPlaying.setElapsed(elapsedTime);
            nowPlayingSubject.onNext(nowPlaying);
        }
    }

    @Override
    public void setNowPlayingState(String currentSpeed, String playingState) {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if (nowPlaying != null) {
            nowPlaying.setPlayingSpeed(currentSpeed);
            nowPlaying.setPlayingState(playingState);
            nowPlayingSubject.onNext(nowPlaying);
        }
    }

    @Override
    public void setNowPlaying(MusicTag song) {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if(nowPlaying != null && song != null) {
            nowPlaying.setSong(song);
            nowPlayingSubject.onNext(nowPlaying);
        }
    }

    @Override
    public String getServerLocation() {
        return mediaServer.getIpAddress();
    }

    public Player getActivePlayer() {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        return  nowPlaying.getPlayer();
    }

    @Override
    public NowPlaying getNowPlaying() {
        return nowPlayingSubject.getValue();
    }

    public void setActivePlayer(Player player) {
        nowPlayingSubject.onNext(new NowPlaying(player, null, 0));
        //onNewTrackPlaying(player, null, 0);
    }

    public void onNewTrackPlaying(Player player, MusicTag song, long elapsedTime) {
         nowPlayingSubject.onNext(new NowPlaying(player, song, elapsedTime));
    }

    private void monitorNowPlaying(NowPlaying nowPlaying) {
        if(isNextTrackScheduled) return;
        if(!controlledPlayback) return;

        MusicTag song = nowPlaying.getSong();
        if (song != null) {

            // --- MODIFIED STATE MANAGEMENT ---
            // A track is considered "new" if its ID changes OR if it just started playing (elapsed < 1s).
            if (song.getId() != currentTrackId || nowPlaying.getElapsed() < 1) {
                // Only reset the flag if it was previously set OR if it's a genuinely new track ID.
                // This prevents resetting the flag unnecessarily on every progress update at the start of a song.
                if (isNextTrackScheduled || song.getId() != currentTrackId) {
                    this.isNextTrackScheduled = false;
                }
                this.currentTrackId = song.getId();
                this.currentTrackId = song.getId();
            }

            int delaySecond = 30; // 30 seconds
            int gracePeriod = 5; // seconds

           // Log.d(TAG, "check for schedule to play next song, isNextTrackScheduled="+isNextTrackScheduled+", elapsed="+nowPlaying.getElapsed()+", delaySecond="+delaySecond+", song.getAudioDuration()="+song.getAudioDuration());
            // Check if the song is about to end AND we haven't already scheduled the next one.
            if (!isNextTrackScheduled && (nowPlaying.getElapsed() + delaySecond) >= song.getAudioDuration()) {
              //  Log.d(TAG, "schedule to play next song...");
                isNextTrackScheduled = true; // Set flag to prevent re-entry

                Log.d(TAG, "play next song by schedule in next "+(delaySecond-gracePeriod)+" seconds ...");
               // scheduler.schedule(this::next, delaySecond-gracePeriod, TimeUnit.SECONDS);
                OneTimeWorkRequest myWorkRequest =
                        new OneTimeWorkRequest.Builder(PlayNextSongWorker.class)
                                .setInitialDelay((delaySecond-gracePeriod), TimeUnit.SECONDS)
                                .build();
                WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                        UNIQUE_PLAYBACK_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        myWorkRequest);
            }
        }
    }

    public MusicTag getSong(Intent intent) {
        if(intent.getExtras() != null) {
            long id = intent.getExtras().getLong(EXTRA_MUSIC_ID);
            return tagRepos.findById(id);
        }
        return null;
    }

    public BehaviorSubject<NowPlaying> getNowPlayingSubject() {
        return nowPlayingSubject;
    }

    public List<RendererDevice> getRenderers() {
        if(isBound) {
            return mediaServer.getRenderers();
        }
        return new ArrayList<>();
    }

    public MusicTag getNowPlayingSong() {
        return nowPlayingSubject.getValue().getSong();
    }

    public RendererDevice getRendererByIpAddress(String clientIp) {
        if(isBound) {
            return mediaServer.getRendererByIpAddress(clientIp);
        }
        return null;
    }

    public RendererDevice getRendererByUDN(String rendererUdn) {
        if(isBound) {
            return mediaServer.getRendererByUDN(rendererUdn);
        }
        return null;
    }

    public void playPrevious() {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if (nowPlaying != null) {
            if(controlledPlayback) {
                playingQueueIndex--;
                if(playingQueueIndex < 0 || playingQueueIndex >= playingQueueSubject.getValue().size()) {
                    playingQueueIndex =0;
                }
                if(playingQueueIndex < playingQueueSubject.getValue().size()) {
                    MusicTag song = playingQueueSubject.getValue().get(playingQueueIndex);
                    nowPlaying.play(song);
                    nowPlaying.setElapsed(0);
                    nowPlayingSubject.onNext(nowPlaying);
                    //onNewTrackPlaying(nowPlaying.getPlayer(), song, 0);
                    playingQueueIndex++;
                }
            }else {
                nowPlaying.skipToPrevious();
            }
        }
    }

    public void setShuffleMode(boolean enabled) {
        isShuffleMode = enabled;
        if (enabled) {
            // When shuffle is turned on, create the first shuffled list
            originalQueue = playingQueueSubject.getValue();
            generateShuffledList();
        }
    }

    public void setRepeatMode(String mode) {
    }

    public class PlaybackServiceBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    public static void startPlaybackService(Application application) {
        Log.d(TAG, "Start PlaybackService requested");

        Intent intent = new Intent(application, PlaybackService.class);
        try {
            application.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting PlaybackService ", e);
        }
    }
}