package apincer.music.core.server.spi;

import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.playback.spi.PlaybackTarget;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public interface MediaServerHub {

    String getLibraryNames();

    List<PlaybackTarget> getPlaybackTargets();

    void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting);

    void setNextTrack(Track nextSong);

    enum ServerStatus {
        RUNNING,
        STOPPED,
        STARTING,
        ERROR, CAST;

        public boolean isOnline() {
            return this == RUNNING;
        }
    }

    void playerActivate(String udn, PlaybackCallback callback);

    void playerDeactivate(String udn);

    void start();

    void stop();

   // boolean isInitialized();

    BehaviorSubject<ServerStatus> getStatus();

    void playerStop(String udn);
    //void pause(String rendererUdn);

    //void play(String renderer);

    void playerPlaySong(String rendererUdn, Track song);
    void playerPlaySong(Track song);

}