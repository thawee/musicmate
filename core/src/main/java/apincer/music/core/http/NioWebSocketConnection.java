package apincer.music.core.http;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server side of one WebSocket connection. Any thread may send or close; frames are queued here
 * and written by the selector thread, which also performs the actual close (ADR-036).
 */
final class NioWebSocketConnection implements WebSocket.Connection {
    private final NioHttpServer server;
    final SelectionKey key;
    final Queue<WebSocket.Frame> outgoingQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean closed = false;
    volatile boolean hasOutgoingQueue = false; // Volatile flag for safe wake-up
    final AtomicBoolean writeInterestQueued = new AtomicBoolean(false);

    NioWebSocketConnection(NioHttpServer server, SelectionKey key) {
        this.server = server;
        this.key = key;
    }

    public void send(String message) {
        if (closed) return;
        send(new WebSocket.Frame(true, WebSocket.OPCODE_TEXT, message.getBytes(StandardCharsets.UTF_8)));
    }

    public void send(byte[] message) {
        if (closed) return;
        send(new WebSocket.Frame(true, WebSocket.OPCODE_BINARY, message));
    }

    public void send(WebSocket.Frame frame) {
        if (closed) return;
        outgoingQueue.add(frame);
        hasOutgoingQueue = true; // Set volatile flag for thread-safe wake-up
        server.requestWebSocketWrite(this);
    }

    /**
     * Closes the WebSocket connection gracefully.
     *
     * @param code   Close status code (e.g., 1000 for normal closure)
     * @param reason Close reason message
     */
    public void close(int code, String reason) {
        if (closed) return;
        closed = true;

        try {
            // Send WebSocket close frame (opcode 0x8)
            ByteBuffer payload = ByteBuffer.allocate(2 + reason.getBytes(StandardCharsets.UTF_8).length);
            payload.putShort((short) code);
            payload.put(reason.getBytes(StandardCharsets.UTF_8));

            WebSocket.Frame closeFrame = new WebSocket.Frame(true, WebSocket.OPCODE_CLOSE, payload.array());
            outgoingQueue.add(closeFrame);
            hasOutgoingQueue = true; // Wake up selector
            // Same path as send(): without OP_WRITE the close frame is never written
            server.requestWebSocketWrite(this);
        } catch (Exception e) {
            forceClose();
        }
    }

    /**
     * Closes without sending a close frame.
     */
    public void forceClose() {
        if (closed) return;
        closed = true;
        outgoingQueue.clear();
        // Close through closeConnection() on the selector thread; cancelling the key here skipped
        // the attachment release and the connection counter, and is unsafe from a worker.
        server.requestClose(key);
    }

    public boolean isClosed() {
        return closed;
    }

    Queue<WebSocket.Frame> getOutgoingQueue() {
        return outgoingQueue;
    }
}
