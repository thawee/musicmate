package apincer.music.core.playback.spi;

import java.util.List;
import java.util.Optional;

import apincer.music.core.model.Track;
import apincer.music.core.playback.PlaybackState;
import java.util.function.Consumer;

public interface PlaybackService {
    // Define custom action strings for the Intent
   // String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    String ACTION_PLAY_NEXT = "apincer.android.mmate.playback.ACTION_PLAY_NEXT";
   // String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";
    String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";


    void skipToPrevious();

    void stopPlaying();

    void setShuffleMode(boolean enabled);

    void setRepeatMode(String mode);

    void switchPlayer(PlaybackTarget newTarget, boolean controlled);

    void switchPlayer(String targetId, boolean controlled);

    void playSong(Track song);
    void pausePlayer();

   void skipToNextInQueue();

   void setNextSongInQueue();

    void onPlaybackStateChanged(PlaybackState state);

    void onPlaybackStateElapsedTime(long elapsedTimeMS);

    AutoCloseable subscribePlaybackState(Consumer<PlaybackState> consumer, Consumer<Throwable> onErrorConsumer);

    AutoCloseable subscribeNowPlayingSong(
            Consumer<Optional<Track>> onNextConsumer,
            Consumer<Throwable> onErrorConsumer
    );

    AutoCloseable subscribePlaybackTarget(
            Consumer<Optional<PlaybackTarget>> consumer,
            Consumer<Throwable> onErrorConsumer);

    List<PlaybackTarget> getPlaybackTargets();

    void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting);

    Track getNowPlayingSong();

    PlaybackTarget getPlayer();

    apincer.music.core.repository.QueueManager getQueueManager();

    void onMediaTrackChanged(Track tag);

    void onAccessMediaTrack(Track tag);

    /** Trigger an immediate UPnP M-SEARCH to rediscover all DLNA/UPnP renderers on the network. */
    void refreshPlayerDiscovery();
}
