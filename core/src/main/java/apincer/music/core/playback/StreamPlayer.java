package apincer.music.core.playback;

import android.content.Context;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;

public class StreamPlayer implements PlaybackTarget {

    private final Context context;
    private final String ipAddress;
    private final String displayName;
    private final String description;

    private StreamPlayer(Context context, String ipAddress, String userAgent, String localtion) {
        this.context = context;
        this.ipAddress = ipAddress;
        this.displayName = userAgent;
        this.description = localtion;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getTargetId() {
        return ipAddress;
    }

    @Override
    public boolean play(MediaTrack song) {
        return false;
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
    public PlaybackState getPlaybackState() {
        return null;
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
        // Not implemented yet for DLNA, requires specific UPnP action
        return false;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void refreshPlayerState() {

    }

    public static class Factory {
        public static PlaybackTarget create(Context context, String ipAddress, String userAgent,String location) {
            return new StreamPlayer(context, ipAddress, userAgent, location);
        }
    }
}