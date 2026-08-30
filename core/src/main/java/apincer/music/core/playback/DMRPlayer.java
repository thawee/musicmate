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

    public boolean isHiBy() {
        return getDeviceProfile() == DeviceProfile.HIBY;
    }

    public enum DeviceProfile {
        WIIM("WiiM Audio Streamer", 3000),
        EVERSOLO("Eversolo Master Streamer", 3000),
        HIBY("HiBy DAP Renderer", -20000),
        SHANLING("Shanling DAP Renderer", -15000),
        SONOS("Sonos Speaker", 5000),
        GENERIC("Standard DLNA Renderer", 5000);

        private final String label;
        private final long gaplessDelayMs; // positive = after start; negative = before track end

        DeviceProfile(String label, long gaplessDelayMs) {
            this.label = label;
            this.gaplessDelayMs = gaplessDelayMs;
        }

        public String getLabel() { return label; }
        public long getGaplessDelayMs() { return gaplessDelayMs; }
    }

    public DeviceProfile getDeviceProfile() {
        String name = displayName != null ? displayName.toLowerCase() : "";
        String id = udn != null ? udn.toLowerCase() : "";

        if (name.contains("wiim") || id.contains("wiim") || name.contains("linkplay")) {
            return DeviceProfile.WIIM;
        } else if (name.contains("eversolo") || id.contains("eversolo") || name.contains("zidoo")) {
            return DeviceProfile.EVERSOLO;
        } else if (name.contains("hiby") || id.contains("hiby")) {
            return DeviceProfile.HIBY;
        } else if (name.contains("shanling") || id.contains("shanling")) {
            return DeviceProfile.SHANLING;
        } else if (name.contains("sonos") || id.contains("sonos")) {
            return DeviceProfile.SONOS;
        }
        return DeviceProfile.GENERIC;
    }

    public static class Factory {
        public static PlaybackTarget create(String urn, String friendlyName, String host) {
            return new DMRPlayer( urn,  friendlyName, host);
        }
    }
}