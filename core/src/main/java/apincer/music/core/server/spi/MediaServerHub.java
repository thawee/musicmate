package apincer.music.core.server.spi;

import java.util.List;

import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public interface MediaServerHub {

    String getLibraryName();

    List<PlaybackTarget> getAvailablePlaybackTargets();

    enum ServerStatus {
        RUNNING,
        STOPPED,
        STARTING,
        ERROR;

        public boolean isOnline() {
            return this == RUNNING;
        }
    }

    boolean activatePlayer(String udn, Callback callback);

    void deactivatePlayer(String udn);

    void startServers();

    void stopServers();

    boolean isInitialized();

    BehaviorSubject<ServerStatus> getServerStatus();

    void stopPlaying(String udn);
    void pause(String rendererUdn);

    //void play(String rendererUdn);

    void playSong(String rendererUdn, MediaTrack song);


   // void fetchPlayerState(String rendererUdn);

    // Add a method for the hosting service to call on destroy
    void onDestroy();

    public abstract static class Callback {
        public Callback() {
        }

        public void onMediaTrackChanged(MediaTrack metadata) {
            throw new RuntimeException("Stub!");
        }

        public void onPlaybackStateChanged(PlaybackState state) {
            throw new RuntimeException("Stub!");
        }

        public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
            throw new RuntimeException("Stub!");
        }
    }
}