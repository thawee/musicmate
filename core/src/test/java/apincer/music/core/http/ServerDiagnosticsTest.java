package apincer.music.core.http;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerDiagnosticsTest {

    private static NioHttpServer.Stats stats(int streams, long requests, long bytes, long evictions, long rejected) {
        return new NioHttpServer.Stats(streams + 1, streams, requests, bytes, evictions, rejected, 0, 0);
    }

    @Test
    public void firstCall_hasNoRateYet() {
        ServerDiagnostics d = new ServerDiagnostics();
        assertEquals("1 stream • 12 requests", d.line(stats(1, 12, 5_000_000, 0, 0), 0, 1_000));
    }

    @Test
    public void rate_comesFromBytesSinceTheLastCall() {
        ServerDiagnostics d = new ServerDiagnostics();
        d.line(stats(2, 10, 0, 0, 0), 0, 0);
        // 18 MiB in 2 s
        assertEquals("2 streams • 9.0 MB/s • 1,204 requests",
                d.line(stats(2, 1_204, 18L * 1024 * 1024, 0, 0), 0, 2_000));
    }

    @Test
    public void idle_saysSo() {
        ServerDiagnostics d = new ServerDiagnostics();
        d.line(stats(0, 3, 100, 0, 0), 0, 0);
        assertEquals("No active streams • 3 requests", d.line(stats(0, 3, 100, 0, 0), 0, 2_000));
    }

    @Test
    public void problems_areListedOnlyWhenPresent() {
        ServerDiagnostics d = new ServerDiagnostics();
        assertEquals("1 stream • 50 requests • 2 evicted • 1 refused • 7 rate-limited",
                d.line(stats(1, 50, 0, 2, 1), 7, 0));
    }

    @Test
    public void counterReset_afterRestart_doesNotShowANegativeRate() {
        ServerDiagnostics d = new ServerDiagnostics();
        d.line(stats(1, 100, 50_000_000, 0, 0), 0, 0);
        assertEquals("1 stream • 1 request", d.line(stats(1, 1, 1_000, 0, 0), 0, 2_000));
    }
}
