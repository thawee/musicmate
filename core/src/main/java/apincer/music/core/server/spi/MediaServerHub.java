package apincer.music.core.server.spi;

import apincer.music.core.playback.spi.MediaTrack;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public interface MediaServerHub {
    void fetchPlaybackState(String udn);

    void stopPlaying(String udn);

    enum ServerStatus {
        RUNNING,
        STOPPED,
        STARTING,
        ERROR
    }

    void startServers();

    void stopServers();

    boolean isInitialized();

    BehaviorSubject<ServerStatus> getServerStatus();

    void pause(String rendererUdn);

    //void play(String rendererUdn);

    void playSong(String rendererUdn, MediaTrack song);


   // void fetchPlayerState(String rendererUdn);

    // Add a method for the hosting service to call on destroy
    void onDestroy();
}