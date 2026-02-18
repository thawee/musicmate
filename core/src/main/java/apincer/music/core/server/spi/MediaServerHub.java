package apincer.music.core.server.spi;

import java.util.List;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackCallback;
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

    void activatePlayer(String udn, PlaybackCallback callback);

    void deactivatePlayer(String udn);

    void startServers();

    void stopServers();

    boolean isInitialized();

    BehaviorSubject<ServerStatus> getServerStatus();

    void stopPlaying(String udn);
    void pause(String rendererUdn);

    //void play(String renderer);

    void playSong(String rendererUdn, MediaTrack song);


   // void fetchPlayerState(String renderer);

    // Add a method for the hosting service to call on destroy
    void onDestroy();

}