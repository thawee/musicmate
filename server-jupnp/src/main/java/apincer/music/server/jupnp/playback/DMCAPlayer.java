package apincer.music.server.jupnp.playback;

import android.content.Context;

import org.jupnp.model.meta.RemoteDevice;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.server.spi.MediaServerHub;

public class DMCAPlayer implements PlaybackTarget {

    private final Context context;
    private final MediaServerHub mediaServer;
    private final String udn;
    private final String displayName;
    private final String location;

    private DMCAPlayer(Context context, MediaServerHub mediaServer, RemoteDevice renderer) {
        this.context = context;
        this.mediaServer = mediaServer;
        this.udn = renderer.getIdentity().getUdn().getIdentifierString();
        this.displayName = renderer.getDetails().getFriendlyName();
        this.location = renderer.getIdentity().getDescriptorURL().getHost();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getTargetId() {
        return udn;
    }

    @Override
    public boolean play(MediaTrack song) {
        mediaServer.stopPlaying(udn);
        mediaServer.playSong(udn, song);
        return true;
    }

    @Override
    public boolean next() {
        return false;
    }

    @Override
    public boolean seekTo(long positionMs) {
        return false;
    }

    @Override
    public boolean setVolume(float volume) {
        return false;
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public boolean pause() {
        // Not implemented yet for DLNA, requires specific UPnP action
        return false;
    }

    @Override
    public boolean resume() {
        // Not implemented yet for DLNA, requires specific UPnP action
        return false;
    }

    @Override
    public boolean stop() {
        mediaServer.stopPlaying(udn);
        return true;
    }

    @Override
    public String getDescription() {
        return location;
    }

    @Override
    public void onSelected() {
        mediaServer.fetchPlaybackState(udn);
    }

    public static class Factory {
        public static PlaybackTarget create(Context context, MediaServerHub mediaServer, RemoteDevice renderer) {
            return new DMCAPlayer(context, mediaServer, renderer);
        }
    }
}