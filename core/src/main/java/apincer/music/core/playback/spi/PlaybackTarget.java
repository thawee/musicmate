package apincer.music.core.playback.spi;

public interface PlaybackTarget {
    boolean isStreaming();
    String getTargetId();
    String getDisplayName();
    String getDescription();
    boolean canReadSate();
}
