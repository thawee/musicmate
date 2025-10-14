package apincer.android.mmate.core.server;

import java.util.List;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.playback.IPlaybackService;
import apincer.android.mmate.core.repository.FileRepository;
import apincer.android.mmate.core.repository.TagRepository;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public interface IMediaServer {
    String getWebUIPort();

    TagRepository getTagReRepository();

    FileRepository getFileRepository();

    IPlaybackService getPlaybackService();

    void setPlaybackService(IPlaybackService playbackService);

    enum ServerStatus {
        RUNNING,
        STOPPED,
        STARTING,
        ERROR
    }

    void startServers();

    void stopServers();

    boolean isInitialized();

    String getIpAddress();

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