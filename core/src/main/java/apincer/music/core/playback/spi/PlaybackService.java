package apincer.music.core.playback.spi;

import java.util.List;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.NowPlaying;
import apincer.music.core.playback.Player;
import apincer.music.core.server.RendererDevice;
import io.reactivex.rxjava3.functions.Consumer;

public interface PlaybackService {
    // Define custom action strings for the Intent
    public static final String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    public static final String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    public static final String ACTION_PLAY_NEXT = "apincer.android.mmate.playback.ACTION_PLAY_NEXT";
    public static final String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";
    public static final String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";
    public static final String EXTRA_UDN = "EXTRA_UDN";

    void play(MusicTag tag);

    void setShuffleMode(boolean enabled);

    void setRepeatMode(String mode);

    void loadPlayingQueue(MusicTag song);

    void playPrevious();

    void playNext();

    Player getActivePlayer();

    NowPlaying getNowPlaying();

    void setActiveDlnaPlayer(String udn);

    void subscribeNowPlaying(Consumer<NowPlaying> consumer);

    void onNewTrackPlaying(Player player, MusicTag tag, long time);

    void setNowPlayingElapsedTime(long time);

    void setNowPlayingState(String currentSpeed, String value);

    void setNowPlaying(MusicTag song);

    String getServerLocation();

    MusicTag getNowPlayingSong();

    List<RendererDevice> getRenderers();

    void setActivePlayer(Player player);

    void skipToNext(MusicTag tag);

    RendererDevice getRendererByIpAddress(String clientIp);
}
