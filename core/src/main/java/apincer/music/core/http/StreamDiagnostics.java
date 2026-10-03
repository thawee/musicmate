package apincer.music.core.http;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded transport diagnostics. Completion means bytes sent, not audio played by a renderer. */
public final class StreamDiagnostics {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("NioHttpServer");
    private static final int HISTORY_LIMIT = 64;
    private final ArrayDeque<ResponseEvent> responses = new ArrayDeque<>();
    private final ArrayDeque<ConnectionEvent> closes = new ArrayDeque<>();
    private long completed, interrupted, maxSelectorTurnNanos, maxWorkerWaitNanos;
    private int maxWorkerQueueDepth;

    public enum CloseReason {
        PEER_CLOSED, IO_FAILURE, SERVER_ERROR, HEADER_TIMEOUT, BODY_TIMEOUT,
        HANDLER_TIMEOUT, WRITE_STALL, PRODUCER_STALL, IDLE_TIMEOUT,
        REQUESTED_CLOSE, EVICTED, SHUTDOWN, SELECTOR_FAILURE, OVERLOADED
    }

    public record ResponseEvent(long connectionId, long requestId, String method, String path,
                                int status, long expectedBodyBytes, long bodyBytesSent,
                                long firstByteMillis, long durationMillis, String outcome,
                                String failure) { }

    public record ConnectionEvent(long connectionId, CloseReason reason) { }

    public record Snapshot(long completed, long interrupted, long maxSelectorTurnNanos,
                           long maxWorkerWaitNanos, int maxWorkerQueueDepth,
                           List<ResponseEvent> responses, List<ConnectionEvent> closes) { }

    synchronized void response(ResponseEvent event) {
        if ("completed".equals(event.outcome())) completed++; else interrupted++;
        if (responses.size() == HISTORY_LIMIT) responses.removeFirst();
        responses.addLast(event);
        LOG.fine(() -> "Response " + event);
        if (event.outcome().endsWith("STALL") || event.outcome().equals("HANDLER_TIMEOUT")) {
            LOG.info("Response stalled: " + event);
        }
    }

    synchronized void closed(long connectionId, CloseReason reason) {
        if (closes.size() == HISTORY_LIMIT) closes.removeFirst();
        closes.addLast(new ConnectionEvent(connectionId, reason));
        LOG.fine(() -> "Connection " + connectionId + " closed: " + reason);
    }

    synchronized void selectorTurn(long nanos) {
        maxSelectorTurnNanos = Math.max(maxSelectorTurnNanos, nanos);
    }

    synchronized void workerWait(long nanos) {
        maxWorkerWaitNanos = Math.max(maxWorkerWaitNanos, nanos);
    }

    synchronized void workerQueue(int depth) {
        maxWorkerQueueDepth = Math.max(maxWorkerQueueDepth, depth);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(completed, interrupted, maxSelectorTurnNanos, maxWorkerWaitNanos,
                maxWorkerQueueDepth, Collections.unmodifiableList(new ArrayList<>(responses)),
                Collections.unmodifiableList(new ArrayList<>(closes)));
    }
}
