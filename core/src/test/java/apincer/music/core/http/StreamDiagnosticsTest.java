package apincer.music.core.http;

import static org.junit.Assert.*;
import org.junit.Test;

public class StreamDiagnosticsTest {
    @Test
    public void historyIsBoundedWhileTotalsKeepCounting() {
        StreamDiagnostics diagnostics = new StreamDiagnostics();
        for (int i = 0; i < 200; i++) {
            diagnostics.response(new StreamDiagnostics.ResponseEvent(1, i, "GET", "/track", 200,
                    1000, 1000, 1, 2, "completed", ""));
            diagnostics.closed(i, StreamDiagnostics.CloseReason.PEER_CLOSED);
        }
        StreamDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(200, snapshot.completed());
        assertEquals(64, snapshot.responses().size());
        assertEquals(64, snapshot.closes().size());
        assertEquals(136, snapshot.responses().get(0).requestId());
        diagnostics.closed(200, StreamDiagnostics.CloseReason.IO_FAILURE);
        assertEquals(199, snapshot.closes().get(63).connectionId());
    }
}
