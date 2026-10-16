package apincer.music.core.playback;

/**
 * Volume rules for network players (DLNA renderers): the states the slider can be in, the conversion
 * between percent and a player's own volume range, and the user's maximum.
 */
public final class RendererVolume {

    /** Not driving a network player: the slider shows the phone's volume. */
    public static final int NONE = -1;
    /** A player was just picked and its volume is being read: show nothing, send nothing. */
    public static final int READING = -2;
    /** The player gives no volume (no volume control, or reads failed): use its own control. */
    public static final int UNAVAILABLE = -3;

    public static final int DEFAULT_LIMIT = 80;

    private RendererVolume() {
    }

    /** A player's volume in its own range [min, max] as a percent (0 to 100 assumed when the range is unusable). */
    public static int toPercent(long value, long min, long max) {
        if (max <= min) return (int) Math.max(0, Math.min(100, value));
        double percent = (value - min) * 100.0 / (max - min);
        return (int) Math.round(Math.max(0, Math.min(100, percent)));
    }

    /** A percent in the player's own range [min, max]. */
    public static long toUnits(int percent, long min, long max) {
        int p = Math.max(0, Math.min(100, percent));
        if (max <= min) return p;
        return min + Math.round(p * (max - min) / 100.0);
    }

    /** The requested level, kept between 0 and the user's maximum (100 or more means no limit). */
    public static int clamp(int requested, int limit) {
        int top = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        return Math.max(0, Math.min(top, requested));
    }
}
