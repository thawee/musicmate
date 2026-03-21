package apincer.music.core.playback.spi;

import java.util.List;
import java.util.Optional;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.PlaybackState;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;

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

    void playSong(MediaTrack song);
    void pausePlayer();

   void skipToNextInQueue();

   void setNextSongInQueue();

    void onPlaybackStateChanged(PlaybackState state);

    void onPlaybackStateElapsedTime(long elapsedTimeMS);

    @NonNull Disposable subscribePlaybackState(Consumer<PlaybackState> consumer, Consumer<Throwable> onErrorConsumer);

    @NonNull Disposable subscribeNowPlayingSong(
            Consumer<Optional<MediaTrack>> onNextConsumer,
            Consumer<Throwable> onErrorConsumer
    );

    @NonNull Disposable subscribePlaybackTarget(
            Consumer<Optional<PlaybackTarget>> consumer,
            Consumer<Throwable> onErrorConsumer);

    List<PlaybackTarget> getPlaybackTargets();

    void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting);

    MediaTrack getNowPlayingSong();

    PlaybackTarget getPlayer();

    void onMediaTrackChanged(MediaTrack tag);

    void onAccessMediaTrack(MusicTag tag);
}
