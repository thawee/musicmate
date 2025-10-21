package apincer.music.core.playback;

import apincer.music.core.playback.spi.PlaybackTarget;

public class DMRPlayer implements PlaybackTarget {
    private final String udn;
    private final String displayName;
    private final String location;

    //DLNA/UPnP
    private DMRPlayer(String urn, String friendlyName, String host) {
        this.udn = urn;
        this.displayName = friendlyName;
        this.location = host;
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
        public static PlaybackTarget create(String urn, String friendlyName, String host) {
            return new DMRPlayer( urn,  friendlyName, host);
        }
    }
}