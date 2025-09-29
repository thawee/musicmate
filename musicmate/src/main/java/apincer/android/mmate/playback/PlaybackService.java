package apincer.android.mmate.playback;
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

import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.RendererController;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.database.QueueItem;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

// This service is a Started Service, meaning it runs in the background
// to handle playback actions and then stops itself when the work is done.
// It is a single-purpose service for managing playback commands.
public class PlaybackService extends Service {
    private static final String TAG = "PlaybackManagerService";
    private static final String NOTIFICATION_CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 1;

    // Define custom action strings for the Intent
    public static final String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    public static final String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    public static final String ACTION_NEXT = "apincer.android.mmate.playback.ACTION_NEXT";
    public static final String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";

    // Intent Extra keys
    public static final String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";
    public static final String EXTRA_UDN = "EXTRA_UDN";

    private final BehaviorSubject<NowPlaying> nowPlayingSubject = BehaviorSubject.createDefault(new NowPlaying());
    private transient boolean controlledPlayback = false;
    private long currentTrackId = -1;

    private final BehaviorSubject<List<MusicTag>> playingQueueSubject = BehaviorSubject.createDefault(new CopyOnWriteArrayList<>());

    public void setPlayingQueueIndex(int playingQueueIndex) {
        this.playingQueueIndex = playingQueueIndex;
    }

    private int playingQueueIndex = -1;

    // New fields for service binding
    private MediaServerService mediaServerService;
    private boolean isBound = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isNextTrackScheduled = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MediaServerService.MediaServerServiceBinder binder = (MediaServerService.MediaServerServiceBinder) service;
            mediaServerService = binder.getService();

            isBound = true;
            Log.i(TAG, "MediaServerService bound successfully.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            mediaServerService = null;
            Log.w(TAG, "MediaServerService disconnected unexpectedly.");
        }
    };

    @SuppressLint("CheckResult")
    @Override
    public void onCreate() {
        super.onCreate();
        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(this, MediaServerService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        startForeground(NOTIFICATION_ID, buildForegroundNotification().build());

        loadPlayingQueue();
        nowPlayingSubject.subscribe(this::monitorNowPlaying);
    }

    private void loadPlayingQueue() {
        try {
            List<QueueItem> queues = MusixMateApp.getInstance().getOrmLite().getQueueItemDao().queryForAll();
            queues.forEach(queueItem -> playingQueueSubject.getValue().add(queueItem.getTrack()));
        } catch (SQLException e) {
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
            case ACTION_NEXT:
                next();
                break;
            case ACTION_SET_DLNA_PLAYER:
                String udn = intent.getStringExtra(EXTRA_UDN);
                if (udn != null) {
                    setDlnaPlayer(udn);
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
                    next();
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
        }
    }

    /**
     * Plays the next song in the current queue.
     */
    public void next() {
        Log.d(TAG, "play next song: controlledPlayback="+controlledPlayback+", playingQueueIndex="+playingQueueIndex);
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        if(nowPlaying != null) {
            if (controlledPlayback) {
                // get song in next queue and play
                if(playingQueueIndex < 0 || playingQueueIndex >= playingQueueSubject.getValue().size()) {
                    playingQueueIndex =0;
                }
                if(playingQueueIndex < playingQueueSubject.getValue().size()) {
                    MusicTag song = playingQueueSubject.getValue().get(playingQueueIndex);
                    nowPlaying.play(song);
                    nowPlaying.setElapsed(0);
                    nowPlayingSubject.onNext(nowPlaying);
                    playingQueueIndex++;
                }
            } else {
                nowPlaying.next();
            }
        }
    }

    /**
     * Sets the active DLNA player using its Unique Device Name (UDN).
     * @param udn The UDN of the DLNA renderer.
     */
    public void setDlnaPlayer(String udn) {
        if(isBound) {
            RemoteDevice remoteDevice = mediaServerService.getRendererByUDN(udn);
            if (remoteDevice != null) {
                Player player = Player.Factory.create(MusixMateApp.getInstance(), remoteDevice);
                setActivePlayer(player);
            }
        }
    }

    public Player getActivePlayer() {
        NowPlaying nowPlaying = nowPlayingSubject.getValue();
        return  nowPlaying.getPlayer();
    }

    public void setActivePlayer(Player player) {
        nowPlayingSubject.onNext(new NowPlaying(player, null, 0));
        //onNewTrackPlaying(player, null, 0);
    }

    public void onNewTrackPlaying(Player player, MusicTag song, long elapsed) {
         nowPlayingSubject.onNext(new NowPlaying(player, song, elapsed));
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
                scheduler.schedule(this::next, delaySecond-gracePeriod, TimeUnit.SECONDS);
            }
        }
    }

    public MusicTag getSong(Intent intent) {
        if(intent.getExtras() != null) {
            long id = intent.getExtras().getLong(EXTRA_MUSIC_ID);
            return TagRepository.findById(id);
        }
        return null;
    }

    public BehaviorSubject<NowPlaying> getNowPlayingSubject() {
        return nowPlayingSubject;
    }

    public List<RemoteDevice> getRenderers() {
        if(isBound) {
            return mediaServerService.getRenderers();
        }
        return Collections.EMPTY_LIST;
    }

    public MusicTag getNowPlaying() {
        return nowPlayingSubject.getValue().getSong();
    }

    public LocalDevice getServerDevice() {
        if(isBound) {
            return mediaServerService.getServerDevice();
        }
        return null;
    }

    public RemoteDevice getRendererByIpAddress(String clientIp) {
        if(isBound) {
            return mediaServerService.getRendererByIpAddress(clientIp);
        }
        return null;
    }

    public RemoteDevice getRendererByUDN(String rendererUdn) {
        if(isBound) {
            return mediaServerService.getRendererByUDN(rendererUdn);
        }
        return null;
    }

    public RendererController getRendererController() {
        if(isBound) {
            return mediaServerService.getRendererController(this);
        }
        return null;
    }

    public BehaviorSubject<List<MusicTag>> getPlayingQueueSubject() {
        return playingQueueSubject;
    }

    public void previous() {
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