package apincer.music.core.playback;

import java.util.UUID;
import java.util.function.Consumer;

/** Counts observed playback, not a player's absolute seek position or time spent paused.
 * Callers supply monotonic time for accounting and wall time only for persisted dates.
 * Missing/very sparse telemetry is deliberately under-counted rather than guessed.
 */
public final class ListeningHistoryTracker {
    public enum Kind { STARTED, COMPLETED, SKIPPED }
    public record Event(long trackId, String sessionId, Kind kind, long occurredAtMs) { }

    private final Consumer<Event> sink;
    private long trackId;
    private String sessionId;
    private long durationMs;
    private long listenedMs;
    private long positionMs = -1;
    private long sampleTimeMs;
    private long lastWallTimeMs;
    private boolean playing, started, completed, sought;

    public ListeningHistoryTracker(Consumer<Event> sink) { this.sink = sink; }

    public synchronized void onState(long id, long duration, boolean isPlaying, long now, long wall) {
        if (id <= 0) { end(false); return; }
        if (trackId != id) {
            end(false);
            trackId = id;
            sessionId = UUID.randomUUID().toString();
            durationMs = Math.max(0, duration);
        } else if (duration > 0) {
            durationMs = duration;
        }
        if (playing != isPlaying) positionMs = -1;
        playing = isPlaying;
        lastWallTimeMs = wall;
    }

    public synchronized void onPosition(long id, long position, long now, long wall) {
        if (id != trackId || trackId <= 0 || !playing || position < 0) return;
        lastWallTimeMs = wall;
        if (positionMs >= 0) {
            long advance = position - positionMs;
            long elapsed = now - sampleTimeMs;
            // Keep the prior baseline for quantized/duplicate position samples.
            if (advance == 0 && elapsed <= 30_000) return;
            if (advance > 0 && elapsed > 0 && elapsed <= 30_000 && advance <= elapsed + 2_000) {
                if (!started) { started = true; emit(Kind.STARTED, wall); }
                listenedMs += Math.min(advance, elapsed);
                if (!completed && durationMs > 0 && listenedMs >= Math.ceil(durationMs * 0.90)) {
                    completed = true;
                    emit(Kind.COMPLETED, wall);
                }
            } else if (advance < 0 || advance > elapsed + 2_000) {
                sought = true;
            }
        }
        positionMs = position;
        sampleTimeMs = now;
    }

    /** Explicit seek: next position is a fresh baseline, not listening credit. */
    public synchronized void seek() { positionMs = -1; sought = true; }

    /** Suspend telemetry across an output handoff without starting another session. */
    public synchronized void suspend() { playing = false; positionMs = -1; }

    /** Only a trustworthy natural-end event may complete a track of unknown duration. */
    public synchronized void naturalEnd(long now, long wall) {
        if (trackId <= 0) return;
        if (durationMs > 0) onPosition(trackId, durationMs, now, wall);
        if (started && !completed && durationMs == 0 && !sought) {
            completed = true;
            emit(Kind.COMPLETED, wall);
        }
        end(false);
    }

    /** Explicit skips are recorded separately; a skipped partial listen stays unplayed. */
    public synchronized void end(boolean skipped) {
        if (trackId > 0 && started && skipped) emit(Kind.SKIPPED, lastWallTimeMs);
        trackId = 0;
        durationMs = listenedMs = 0;
        positionMs = -1;
        playing = started = completed = sought = false;
    }

    private void emit(Kind kind, long wall) { sink.accept(new Event(trackId, sessionId, kind, wall)); }
}
