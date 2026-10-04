package apincer.music.core.http;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Counts allocated generated-audio bytes, including queued, pending and producer-held chunks. */
final class AudioBufferBudget {
    private long limit, used, peak;

    AudioBufferBudget(long limit) { setLimit(limit); }

    synchronized void setLimit(long bytes) {
        if (bytes < StreamingResponse.CHUNK) throw new IllegalArgumentException("Budget must fit one audio chunk");
        if (used != 0) throw new IllegalStateException("Cannot change an occupied buffer budget");
        limit = bytes;
    }

    synchronized void acquire(int bytes, BooleanSupplier closed) throws IOException {
        try {
            while (used + bytes > limit && !closed.getAsBoolean()) wait(200);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Audio producer interrupted", interrupted);
        }
        if (closed.getAsBoolean()) throw new IOException("Response closed");
        used += bytes;
        peak = Math.max(peak, used);
    }

    synchronized void release(int bytes) {
        used -= bytes;
        notifyAll();
    }

    synchronized long used() { return used; }
    synchronized long peak() { return peak; }
}
