package apincer.music.server.jupnp;

/** Skips tracks a renderer refuses, but stops after a few in a row instead of racing through the queue. */
final class TrackFailureGuard {
    static final int MAX_CONSECUTIVE_FAILURES = 3;
    private int failures;

    /** True when the refused track should be skipped; false once too many failed in a row. */
    synchronized boolean shouldSkip() {
        if (++failures <= MAX_CONSECUTIVE_FAILURES) return true;
        failures = 0;
        return false;
    }

    synchronized void playbackProgressed() {
        failures = 0;
    }
}
