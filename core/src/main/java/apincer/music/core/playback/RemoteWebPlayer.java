package apincer.music.core.playback;

import apincer.music.core.playback.spi.PlaybackTarget;

public class RemoteWebPlayer implements PlaybackTarget {

    private final String targetId;
    private final String displayName;
    private final String location;

    private RemoteWebPlayer(String targetId, String userAgent, String location) {
        this.targetId = targetId;
        this.displayName = userAgent;
        this.location = location;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getTargetId() {
        return targetId;
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public String getDescription() {
        return location;
    }

    public static class Factory {
        public static PlaybackTarget create(String ipAddress, String userAgent,String location) {
            return new RemoteWebPlayer(ipAddress, userAgent, location);
        }
    }
}