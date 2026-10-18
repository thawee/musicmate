package apincer.music.core.server.spi;

import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.playback.spi.PlaybackTarget;
import kotlinx.coroutines.flow.StateFlow;

public interface MediaServerHub {

    String getLibraryNames();

    List<PlaybackTarget> getPlaybackTargets();

    void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting);

    /** Queues the gapless follower on the renderer; {@code null} clears a queued one. */
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

    default void playerActivateWithHandoff(String udn, PlaybackCallback callback, Track handoffTrack, long initialPositionMs) {
        playerActivate(udn, callback);
    }

    void playerDeactivate(String udn);

    void start();

    void stop();

   // boolean isInitialized();

    StateFlow<ServerStatus> getStatus();

    void playerStop(String udn);
    void playerPause(String udn);
    default void playerResume(String udn) {}
    void playerSeek(String udn, long positionMs);
    /**
     * Called with a renderer's target id when it leaves while the server runs: it said goodbye, its
     * advertisement expired, or it stopped answering. Not called while the server stops or restarts.
     */
    default void setOnRendererLostListener(java.util.function.Consumer<String> listener) {}

    /** Sets the renderer volume in percent of its own range. */
    void playerSetVolume(String udn, int volume);

    /** Reports whether the renderer acknowledged a volume command. */
    void playerSetVolume(String udn, int volume, java.util.function.Consumer<Boolean> onComplete);

    /** Reads the renderer volume in percent of its own range, waiting up to {@code timeoutMs}; -1 when unknown. Not for the main thread. */
    default int playerGetVolume(String udn, long timeoutMs) {
        return -1;
    }

    void playerPlaySong(String rendererUdn, Track song);
    default void playerPlaySong(String rendererUdn, Track song, long initialPositionMs) {
        playerPlaySong(rendererUdn, song);
    }
    void playerPlaySong(Track song);

    /** Trigger an immediate UPnP M-SEARCH to rediscover all renderers on the network. */
    void refreshDiscovery();

    /** Register a listener invoked whenever DLNA renderers are added, updated, or removed. */
    void setOnRenderersChangedListener(java.util.function.Consumer<List<PlaybackTarget>> listener);

    default boolean isCurrentRendererHiBy() {
        return false;
    }

    default boolean isCurrentRendererVerifiedGapless() {
        return false;
    }
}
