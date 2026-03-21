package apincer.music.core.playback;

import apincer.music.core.playback.spi.PlaybackTarget;

public class DMRPlayer implements PlaybackTarget {
    private final String udn;
    private final String displayName;
    private final String location;

    // Capability Flags
    private boolean supportsFlac = false;
    private boolean supportsSeek = true; // Most support it, but we'll verify
    private boolean supports24Bit = false;

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
        // You can now return a more detailed description based on capabilities
        if (supports24Bit) return location + " (Hi-Res Lossless)";
        return location;
    }

    @Override
    public boolean canReadSate() {
        return true;
    }

    // --- Capability Getters & Setters ---

    public boolean isSupportsFlac() {
        return supportsFlac;
    }

    public void setSupportsFlac(boolean supportsFlac) {
        this.supportsFlac = supportsFlac;
    }

    public boolean isSupportsSeek() {
        return supportsSeek;
    }

    public void setSupportsSeek(boolean supportsSeek) {
        this.supportsSeek = supportsSeek;
    }

    public boolean isSupports24Bit() {
        return supports24Bit;
    }

    public void setSupports24Bit(boolean supports24Bit) {
        this.supports24Bit = supports24Bit;
    }

    public static class Factory {
        public static PlaybackTarget create(String urn, String friendlyName, String host) {
            return new DMRPlayer( urn,  friendlyName, host);
        }
    }
}