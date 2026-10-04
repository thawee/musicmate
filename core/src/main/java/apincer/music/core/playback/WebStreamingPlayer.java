package apincer.music.core.playback;

import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.utils.PlayerNameUtils;

public class WebStreamingPlayer implements PlaybackTarget {

    private final String targetId;
    private final String displayName;
    private final String location;

    private WebStreamingPlayer(String targetId, String userAgent, String location) {
        this.targetId = targetId;
        this.displayName = PlayerNameUtils.getFriendlyNameFromUserAgent(userAgent);
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

    @Override
    public boolean canReadSate() {
        return false;
    }

    public static class Factory {
        public static PlaybackTarget create(String ipAddress, String userAgent,String location) {
            return new WebStreamingPlayer(ipAddress, userAgent, location);
        }
    }
}