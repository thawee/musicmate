package apincer.android.mmate.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

import apincer.music.core.database.MusicTag;
import apincer.music.core.database.QueueItem;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.server.BaseServer;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.TagRepository;
import apincer.android.mmate.worker.PlayNextSongWorker;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

// This service is a Started Service, meaning it runs in the background
// to handle playback actions and then stops itself when the work is done.
// It is a single-purpose service for managing playback commands.

@AndroidEntryPoint
public class PlaybackServiceImpl extends Service implements PlaybackService {
    private static final String TAG = "PlaybackServiceImpl";
    private static final String NOTIFICATION_CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 1;

    // Intent Extra keys
    private static final String UNIQUE_PLAYBACK_WORK_NAME = "PLAYBACK_PLAY_NEXT_WORK_NAME";

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

    private boolean isNextTrackScheduled = false;
    private final List<PlaybackTarget> playbackTargets = new CopyOnWriteArrayList<>();

    @Inject
    TagRepository tagRepos;

    private PlaybackTarget currentPlayer;
    private MediaTrack currentTrack;
    private final BehaviorSubject<PlaybackState> playbackStateSubject = BehaviorSubject.createDefault(new PlaybackState());

    @SuppressLint("CheckResult")
    @Override
    public void onCreate() {
        super.onCreate();

        startForeground(NOTIFICATION_ID, buildForegroundNotification().build());
        loadPlayingQueue();
        subscribePlaybackState(this::monitorNowPlaying);
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
                    playNext();
                }
                break;
            case ACTION_PLAY_NEXT:
                playNext();
                break;
            case ACTION_SET_DLNA_PLAYER:
                String udn = intent.getStringExtra(EXTRA_UDN);
                if (udn != null) {
                    switchPlayer(udn);
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
    }

    /**
     * Plays a specific song.
     * This method is used when playing a single song, not a queue
     * @param song The MusicTag object representing the song to play.
     */
    @Override
    public void play(MediaTrack song) {

        if(currentPlayer != null) {
            currentTrack = song;
            currentPlayer.play(song);
        }

        // This is the single line of code to cancel the work.
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(UNIQUE_PLAYBACK_WORK_NAME);
    }

    /**
     * Plays the next song in the current queue.
     */
    public void playNext() {
        Log.d(TAG, "play next song: playingQueueIndex="+playingQueueIndex);
        if(currentPlayer == null) return;

        List<MusicTag> currentQueue = playingQueueSubject.getValue();

        // Ensure we have something to play
        if (currentPlayer == null || currentQueue == null || currentQueue.isEmpty()) {
            Log.w(TAG, "Cannot play next song, play or queue is null/empty.");
            return;
        }

        if (currentPlayer.isStreaming()) {
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
                play(song);
            } else {
                Log.e(TAG, "Error: songIndexToPlay is out of bounds!");
            }
        }else {
            // call play next for external player
            currentPlayer.next();
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

    @Override
    public void switchPlayer(String targetId) {
        PlaybackTarget player = findPlaybackTargetById(targetId);
        if (player != null) {
            switchPlayer(player);
        }
    }

    private PlaybackTarget findPlaybackTargetById(String targetId) {
        if(targetId == null) return null;

        for(PlaybackTarget dev: playbackTargets) {
            if(targetId.equals(dev.getTargetId())) {
                return dev;
            }
        }
        return null;
    }

    @Override
    public String getServerLocation() {
        return BaseServer.getIpAddress();
    }

    @Override
    public void switchPlayer(PlaybackTarget newTarget) {
        if (currentPlayer != null) {
            currentPlayer.stop();
        }
        this.currentPlayer = newTarget;
        this.currentPlayer.onSelected();
        // Start playing the current track on the new device, or wait for command
    }

    @Override
    public void subscribePlaybackState(Consumer<PlaybackState> consumer) {
        playbackStateSubject.subscribe(consumer);
    }

    private void monitorNowPlaying(PlaybackState state) {
        if(isNextTrackScheduled) return;
        if(currentPlayer == null) return;
        if(!currentPlayer.isStreaming()) return;

        MediaTrack song = state.currentTrack;
        if (song != null) {

            // --- MODIFIED STATE MANAGEMENT ---
            // A track is considered "new" if its ID changes OR if it just started playing (elapsed < 1s).
            if (song.getId() != currentTrackId || state.currentPositionMs < 1) {
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
            if (!isNextTrackScheduled && (state.currentPositionMs + delaySecond) >= song.getAudioDuration()) {
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

    @Override
    public List<PlaybackTarget> getAvaiablePlaybackTargets() {
        return playbackTargets;
    }

    @Override
    public MediaTrack getNowPlayingSong() {
        return currentTrack;
    }

    @Override
    public PlaybackTarget getPlayer() {
        return currentPlayer;
    }

    @Override
    public void notifyNewTrackPlaying(PlaybackTarget player, MediaTrack track) {
        currentPlayer = player;
        notifyNewTrackPlaying(track);

        // guess the playbackTarget from ip address in DMCA renderer list
        if(player.isStreaming()) {
            PlaybackTarget dmcaPlayer = findPlaybackTargetByLocation(player);
            if (dmcaPlayer != null) {
                currentPlayer = dmcaPlayer;
                currentPlayer.onSelected();
            }
        }
    }

    private PlaybackTarget findPlaybackTargetByLocation(PlaybackTarget player) {
        if(player==null || player.getTargetId() == null) return null;

        // description is the ip address
        for(PlaybackTarget dev: playbackTargets) {
            if(player.getDescription().equals(dev.getDescription())) {
                return dev;
            }
        }
        return null;
    }

    @Override
    public void notifyNewTrackPlaying(MediaTrack song) {
        currentTrack = song;

        PlaybackState state = new PlaybackState();
        state.currentState = PlaybackState.State.PLAYING;
        state.currentTrack = song;
        state.currentPositionMs =0;
        notifyPlaybackState(state);
    }

    @Override
    public void notifyPlaybackState(PlaybackState state) {
        playbackStateSubject.onNext(state);
    }

    @Override
    public void notifyPlaybackStateElapsedTime(long elapsedTimeMS) {
        PlaybackState state = playbackStateSubject.getValue();
        state.currentPositionMs = elapsedTimeMS;
        playbackStateSubject.onNext(state);
    }

    public void playPrevious() {
        Log.d(TAG, "play next song: playingQueueIndex="+playingQueueIndex);
        if(currentPlayer == null ) return;

        if(currentPlayer.isStreaming()) {
            List<MusicTag> currentQueue = playingQueueSubject.getValue();

            // Ensure we have something to play
            if (currentPlayer == null || currentQueue == null || currentQueue.isEmpty()) {
                Log.w(TAG, "Cannot play next song, play or queue is null/empty.");
                return;
            }

            playingQueueIndex--;
            if (playingQueueIndex < 0 || playingQueueIndex >= playingQueueSubject.getValue().size()) {
                playingQueueIndex = 0;
            }

            if (playingQueueIndex < playingQueueSubject.getValue().size()) {
                MusicTag song = playingQueueSubject.getValue().get(playingQueueIndex);
                play(song);
                playingQueueIndex++;
            }
        }else {
           // currentPlayer.previous();
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

    @Override
    public void loadPlayingQueue(MediaTrack song) {

    }

    public List<PlaybackTarget> getPlaybackTargets() {
        return playbackTargets;
    }

    public class PlaybackServiceBinder extends Binder implements apincer.music.core.playback.spi.PlaybackServiceBinder {
        public PlaybackService getService() {
            return PlaybackServiceImpl.this;
        }
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