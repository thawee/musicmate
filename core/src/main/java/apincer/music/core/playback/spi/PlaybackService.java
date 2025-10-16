package apincer.music.core.playback.spi;

import java.util.List;

import apincer.music.core.playback.PlaybackState;
import io.reactivex.rxjava3.functions.Consumer;

public interface PlaybackService {
    // Define custom action strings for the Intent
    String ACTION_PLAY = "apincer.android.mmate.playback.ACTION_PLAY";
    String ACTION_SKIP_TO_NEXT = "apincer.android.mmate.playback.ACTION_SKIP_TO_NEXT";
    String ACTION_PLAY_NEXT = "apincer.android.mmate.playback.ACTION_PLAY_NEXT";
    String ACTION_SET_DLNA_PLAYER = "apincer.android.mmate.playback.ACTION_SET_DLNA_PLAYER";
    String EXTRA_MUSIC_ID = "EXTRA_MUSIC_ID";
    String EXTRA_UDN = "EXTRA_UDN";

    void setShuffleMode(boolean enabled);

    void setRepeatMode(String mode);

    void loadPlayingQueue(MediaTrack song);

    void playPrevious();

    void play(MediaTrack song);

    void playNext();

   // Player getActivePlayer();

   // NowPlaying getNowPlaying();

    void switchPlayer(PlaybackTarget newTarget);

    void switchPlayer(String targetId);

   // void setActiveDlnaPlayer(String udn);

   // void subscribeNowPlaying(Consumer<NowPlaying> consumer);

    void subscribePlaybackState(Consumer<PlaybackState> consumer);

   // void onNewTrackPlaying(PlaybackTarget player, MediaTrack tag, long time);

    // @Override
    /*
    public void setActiveDlnaPlayer(String udn) {
        if(isBound) {
            RendererDevice remoteDevice = mediaServer.getRendererByUDN(udn);
            if (remoteDevice != null) {
                Player player = Player.Factory.create(getApplicationContext(), remoteDevice);
                setActivePlayer(player);
                mediaServer.startPolling(remoteDevice.getUdn());
            }
        }
    } */


    //void setNowPlayingElapsedTime(long time);

    //void setNowPlayingState(String currentSpeed, String value);

   // void setNowPlaying(MusicTag song);

    String getServerLocation();

    List<PlaybackTarget> getAvaiablePlaybackTargets();

    MediaTrack getNowPlayingSong();

   // List<RendererDevice> getRenderers();

   // void setActivePlayer(Player player);

   // void skipToNext(MusicTag tag);

    //RendererDevice getRendererByIpAddress(String clientIp);

    PlaybackTarget getPlayer();

    void notifyNewTrackPlaying(PlaybackTarget player, MediaTrack track);

    void notifyNewTrackPlaying(MediaTrack song);

    void notifyPlaybackState(PlaybackState state);

    void notifyPlaybackStateElapsedTime(long elapsedTimeMS);
}
