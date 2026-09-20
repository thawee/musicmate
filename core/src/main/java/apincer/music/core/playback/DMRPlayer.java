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

    public boolean supportsPreload() {
        return getDeviceProfile().isSupportsPreload();
    }

    public enum DeviceProfile {
        WIIM("WiiM Audio Streamer", 0, false),
        EVERSOLO("Eversolo Master Streamer", 0, false),
        LINN("Linn DS Player", 0, false),
        AURALIC("AURALiC Streamer", 0, false),
        HIBY("HiBy DAP Renderer", 0, false),
        SHANLING("Shanling DAP Renderer", 0, false),
        FIIO("FiiO DAP Renderer", 0, false),
        SONOS("Sonos Speaker", 0, false),
        GENERIC("Standard DLNA Renderer", 0, false);

        private final String label;
        private final long gaplessDelayMs; // positive = after start
        private final boolean supportsPreload;

        DeviceProfile(String label, long gaplessDelayMs, boolean supportsPreload) {
            this.label = label;
            this.gaplessDelayMs = gaplessDelayMs;
            this.supportsPreload = supportsPreload;
        }

        public String getLabel() { return label; }
        public long getGaplessDelayMs() { return gaplessDelayMs; }
        public boolean isSupportsPreload() { return supportsPreload; }
    }

    public DeviceProfile getDeviceProfile() {
        String name = displayName != null ? displayName.toLowerCase() : "";
        String id = udn != null ? udn.toLowerCase() : "";

        if (name.contains("wiim") || id.contains("wiim") || name.contains("linkplay") || name.contains("audiopro")) {
            return DeviceProfile.WIIM;
        } else if (name.contains("eversolo") || id.contains("eversolo") || name.contains("zidoo")) {
            return DeviceProfile.EVERSOLO;
        } else if (name.contains("linn") || id.contains("linn")) {
            return DeviceProfile.LINN;
        } else if (name.contains("auralic") || id.contains("auralic") || name.contains("aries") || name.contains("altair")) {
            return DeviceProfile.AURALIC;
        } else if (name.contains("hiby") || id.contains("hiby") || name.startsWith("r3 ") || name.equals("r3") || id.startsWith("hiby")) {
            return DeviceProfile.HIBY;
        } else if (name.contains("shanling") || id.contains("shanling")) {
            return DeviceProfile.SHANLING;
        } else if (name.contains("fiio") || id.contains("fiio")) {
            return DeviceProfile.FIIO;
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