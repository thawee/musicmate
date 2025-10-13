package apincer.android.mmate.core.playback;

import apincer.android.mmate.core.database.MusicTag;

public interface IPlaybackService {
    void play(MusicTag tag);

    void setShuffleMode(boolean enabled);

    void setRepeatMode(String mode);

    void loadPlayingQueue(MusicTag song);

    void playPrevious();

    void playNext();

    Player getActivePlayer();

    NowPlaying getNowPlaying();

    void setActiveDlnaPlayer(String udn);

    void onNewTrackPlaying(Player player, MusicTag tag, long time);

    void setNowPlayingElapsedTime(long time);

    void setNowPlayingState(String currentSpeed, String value);

    void setNowPlaying(MusicTag song);

    String getServerLocation();
}
