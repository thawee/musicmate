package apincer.music.core.playback.spi;

import java.util.List;
import java.util.Optional;

import apincer.music.core.playback.PlaybackState;
import io.reactivex.rxjava3.functions.Consumer;

public interface PlaybackService {
    // Define custom action strings for the Intent
   // String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    String ACTION_PLAY_NEXT = "apincer.android.mmate.playback.ACTION_PLAY_NEXT";
   // String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";
    String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";


 void stopPlaying();

 void setShuffleMode(boolean enabled);

    void setRepeatMode(String mode);

    void switchPlayer(PlaybackTarget newTarget, boolean controlled);

    void loadPlayingQueue(MediaTrack song);

    void onPlaybackStateChanged(PlaybackState state);

    void onPlaybackStateElapsedTime(long elapsedTimeMS);

    void subscribePlaybackState(Consumer<PlaybackState> consumer);

    void subscribeNowPlayingSong(Consumer<Optional<MediaTrack>> consumer);

    void subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer);

    void playPrevious();

    void play(MediaTrack song);

    void playNext();

    void switchPlayer(String targetId, boolean controlled);

    String getServerLocation();

    List<PlaybackTarget> getAvailablePlaybackTargets();

    MediaTrack getNowPlayingSong();

    PlaybackTarget getPlayer();


    void onMediaTrackChanged(MediaTrack tag);

}
