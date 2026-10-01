package apincer.music.core.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * One upgraded WebSocket connection. Frames are parsed and reassembled on the selector thread;
 * the handler's callbacks run one at a time, in order, on this session's {@link SerialExecutor}
 * (ADR-036). Also holds the write-side state the selector thread uses to send queued frames.
 */
final class WebSocketSession implements WebSocket.FrameParser.FrameDataHandler {
    final NioWebSocketConnection connection;
    private final WebSocket.Handler handler;
    private final SerialExecutor tasks;
    private final WebSocket.FrameParser parser = new WebSocket.FrameParser();
    private final int maxMessageSize;
    private final int bufferLimit;

    private BoundedByteArrayOutputStream reassemblyBuffer;
    private final BoundedByteArrayOutputStream controlFrameBuffer;
    private int fragmentedOpcode = 0;
    private boolean currentFrameIsFin;
    private int currentFrameOpcode;
    private boolean closeNotified = false;

    /** Set when the peer's CLOSE frame arrives; the selector thread then closes the connection. */
    boolean closeReceived = false;
    /** Frame currently being written (selector thread), and whether it is our CLOSE frame. */
    ByteBuffer pendingWriteBuffer;
    boolean closeAfterWrite;

    WebSocketSession(WebSocket.Handler handler, NioWebSocketConnection connection, SerialExecutor tasks,
                     int maxMessageSize, int bufferLimit) {
        this.handler = handler;
        this.connection = connection;
        this.tasks = tasks;
        this.maxMessageSize = maxMessageSize;
        this.bufferLimit = bufferLimit;
        this.reassemblyBuffer = new BoundedByteArrayOutputStream(8192, bufferLimit);
        this.controlFrameBuffer = new BoundedByteArrayOutputStream(125, maxMessageSize); // control frames max 125 bytes
    }

    /** Queues onOpen; call before parsing any frame so it runs ahead of every message. */
    void open() {
        tasks.execute(() -> {
            try {
                handler.onOpen(connection);
            } catch (Exception e) {
                handler.onError(connection, e);
            }
        });
    }

    /** Parses frames from {@code buffer} on the selector thread; throws on a protocol error. */
    void parse(ByteBuffer buffer) {
        parser.parse(buffer, this);
    }

    /** Queues onClose once, after any messages still pending for this connection. */
    void notifyClosed(int code, String reason) {
        if (closeNotified) return;
        closeNotified = true;
        tasks.execute(() -> {
            try {
                handler.onClose(connection, code, reason);
            } catch (Exception ignored) {
                // a failing onClose must not break the connection teardown
            }
        });
    }

    // --- FrameDataHandler (selector thread) ---

    @Override
    public void onFrameStart(boolean isFin, int opcode, long payloadLength) {
        this.currentFrameIsFin = isFin;
        this.currentFrameOpcode = opcode;

        // Control frames (0x8-0xF) cannot be fragmented and are handled separately
        if (opcode > 0x7) {
            controlFrameBuffer.reset();
            return;
        }

        if (opcode == WebSocket.OPCODE_CONTINUATION) {
            if (fragmentedOpcode == 0) {
                throw new RuntimeException("Continuation frame without initial frame");
            }
            if (reassemblyBuffer.size() + payloadLength > maxMessageSize) {
                connection.close(WebSocket.CLOSE_TOO_LARGE, "Message too large");
                throw new RuntimeException("WebSocket message exceeds size limit");
            }
        } else {
            // A new TEXT or BINARY message
            if (fragmentedOpcode != 0) {
                throw new RuntimeException("New frame started before previous fragmented message completed");
            }
            fragmentedOpcode = opcode;
            if (payloadLength > maxMessageSize) {
                connection.close(WebSocket.CLOSE_TOO_LARGE, "Message too large");
                throw new RuntimeException("WebSocket message exceeds size limit");
            }
        }
    }

    @Override
    public void onFramePayloadData(ByteBuffer payloadChunk) {
        byte[] chunkBytes = new byte[payloadChunk.remaining()];
        payloadChunk.get(chunkBytes);
        try {
            if (currentFrameOpcode > 0x7) {
                controlFrameBuffer.write(chunkBytes);
            } else {
                reassemblyBuffer.write(chunkBytes);
            }
        } catch (IOException e) {
            throw new RuntimeException(e); // in-memory stream: cannot happen
        }
    }

    @Override
    public void onFrameEnd() {
        if (currentFrameOpcode > 0x7) {
            byte[] controlPayload = controlFrameBuffer.toByteArray();
            processCompleteFrame(new WebSocket.Frame(true, currentFrameOpcode, controlPayload));
            controlFrameBuffer.reset();
            return;
        }
        if (currentFrameIsFin) {
            // Final frame of a message: dispatch the reassembled payload
            byte[] fullPayload = reassemblyBuffer.toByteArray();
            processCompleteFrame(new WebSocket.Frame(true, fragmentedOpcode, fullPayload));
            reassemblyBuffer.reset();
            fragmentedOpcode = 0;
        }
        // Not final: keep accumulating
    }

    private void processCompleteFrame(WebSocket.Frame frame) {
        switch (frame.getOpcode()) {
            case WebSocket.OPCODE_TEXT:
            case WebSocket.OPCODE_BINARY: {
                final String text = (frame.getOpcode() == WebSocket.OPCODE_TEXT) ? frame.getPayloadAsText() : null;
                final byte[] binary = (frame.getOpcode() == WebSocket.OPCODE_BINARY) ? frame.getPayload() : null;
                // Serial per connection, so commands are handled in the order they were sent
                tasks.execute(() -> {
                    try {
                        if (text != null) handler.onMessage(connection, text);
                        else handler.onMessage(connection, binary);
                    } catch (Exception e) {
                        handler.onError(connection, e);
                    }
                });
                // Drop a buffer that grew large, so one big message does not pin memory
                if (reassemblyBuffer.size() > 1024 * 1024) {
                    reassemblyBuffer = new BoundedByteArrayOutputStream(8192, bufferLimit);
                }
                break;
            }
            case WebSocket.OPCODE_CLOSE: {
                int closeCode = WebSocket.CLOSE_NORMAL;
                String closeReason = "";
                byte[] payload = frame.getPayload();
                if (payload.length >= 2) {
                    closeCode = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    if (!isValidCloseCode(closeCode)) {
                        connection.close(WebSocket.CLOSE_PROTOCOL_ERROR, "Invalid close code");
                        return;
                    }
                    if (payload.length > 2) {
                        closeReason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                    }
                }
                notifyClosed(closeCode, closeReason);
                connection.forceClose();
                // The read loop closes the connection once this parse returns
                closeReceived = true;
                break;
            }
            case WebSocket.OPCODE_PING:
                connection.send(new WebSocket.Frame(true, WebSocket.OPCODE_PONG, frame.getPayload()));
                break;
            default:
                break;
        }
    }

    /** RFC 6455 §7.4.1: codes a peer may send in a CLOSE frame. */
    private static boolean isValidCloseCode(int code) {
        if (code == 1004 || code == 1005 || code == 1006 || code == 1015 || (code >= 1012 && code <= 1014)) {
            return false;
        }
        return (code >= 1000 && code <= 1011) || (code >= 3000 && code <= 4999);
    }
}
