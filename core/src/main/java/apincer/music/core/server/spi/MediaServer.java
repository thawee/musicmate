package apincer.music.core.server.spi;

import java.util.List;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.RendererDevice;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public interface MediaServer {

    TagRepository getTagReRepository();

    FileRepository getFileRepository();

    PlaybackService getPlaybackService();

    void setPlaybackService(PlaybackService playbackService);

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

    List<RendererDevice> getRenderers();

    RendererDevice getRendererByUDN(String udn);

    RendererDevice getRendererByIpAddress(String ipAddress);

    void pause(String rendererUdn);

    void play(String rendererUdn);

    void playSong(String rendererUdn, MusicTag song);

    void getCurrentSong(String rendererUdn);

    void startPolling(String rendererUdn);

    void stopPolling();

    // Add a method for the hosting service to call on destroy
    void onDestroy();
}