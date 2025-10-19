package apincer.music.core.playback.spi;

public interface PlaybackTarget {
   // boolean play(MediaTrack track);
   // boolean pause();
   // boolean resume();
   // boolean stop();

   // boolean next();

   // boolean seekTo(long positionMs);
   // boolean setVolume(float volume); // Volume from 0.0 to 1.0

    boolean isStreaming();
    //boolean isControllable();

    // To report status back to the service/UI
   // PlaybackState getPlaybackState();

    // To identify the player
    String getTargetId();
    String getDisplayName();
    String getDescription();

    //void activate();

    //void deactivate();

    //boolean previous();
}
