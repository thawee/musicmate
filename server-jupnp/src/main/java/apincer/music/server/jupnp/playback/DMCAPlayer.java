package apincer.music.server.jupnp.playback;

import org.jupnp.model.meta.RemoteDevice;

import apincer.music.core.playback.spi.PlaybackTarget;

public class DMCAPlayer implements PlaybackTarget {
    private final String udn;
    private final String displayName;
    private final String location;

    private DMCAPlayer(RemoteDevice renderer) {
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
    public boolean isStreaming() {
        return true;
    }

    @Override
    public String getDescription() {
        return location;
    }

    @Override
    public boolean canReadSate() {
        return true;
    }

    public static class Factory {
        public static PlaybackTarget create(RemoteDevice renderer) {
            return new DMCAPlayer(renderer);
        }
    }
}