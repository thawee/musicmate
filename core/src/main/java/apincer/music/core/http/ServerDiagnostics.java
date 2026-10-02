package apincer.music.core.http;

import java.util.Locale;

/**
 * Turns {@link NioHttpServer.Stats} snapshots into the one-line summary shown on the Music
 * Center Server tab, e.g. "2 streams • 9.0 MB/s • 1,204 requests • 1 evicted". The rate is the
 * bytes sent since the previous call, so call it at a steady interval.
 */
public final class ServerDiagnostics {
    private NioHttpServer.Stats previous;
    private long previousAtMs;

    public synchronized String line(NioHttpServer.Stats now, long rateLimited, long nowMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(now.streams == 0 ? "No active streams"
                : now.streams + (now.streams == 1 ? " stream" : " streams"));

        if (previous != null && nowMs > previousAtMs) {
            long delta = now.bytesSent - previous.bytesSent;
            // A negative delta means the server restarted and its counters began again
            if (delta > 0 || (delta == 0 && now.streams > 0)) {
                double mbPerSecond = delta / ((nowMs - previousAtMs) / 1000.0) / (1024 * 1024);
                sb.append(String.format(Locale.US, " • %.1f MB/s", mbPerSecond));
            }
        }
        sb.append(String.format(Locale.US, " • %,d %s", now.requests, now.requests == 1 ? "request" : "requests"));

        if (now.evictions > 0) sb.append(" • ").append(now.evictions).append(" evicted");
        if (now.rejected > 0) sb.append(" • ").append(now.rejected).append(" refused");
        if (rateLimited > 0) sb.append(" • ").append(rateLimited).append(" rate-limited");

        previous = now;
        previousAtMs = nowMs;
        return sb.toString();
    }
}
