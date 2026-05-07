package apincer.music.core.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WebSocket {
    // --- WebSocket Opcode Constants ---
    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;

    // --- WebSocket Close Constants ---
    public static final int CLOSE_NORMAL = 1000;
    public static final int CLOSE_GOING_AWAY = 1001;
    public static final int CLOSE_PROTOCOL_ERROR = 1002;
    public static final int CLOSE_ABNORMAL = 1006;
    public static final int CLOSE_SERVER_FULL = 1008;
    public static final int CLOSE_TOO_LARGE = 1009;

    public interface Connection {
        void send(String message);
        void send(byte[] message);
        void send(Frame frame);
        void close(int code, String reason);
        void forceClose();
        boolean isClosed();
    }

    public interface Handler {
        String getNamespace();
        void onOpen(Connection connection);
        void onMessage(Connection connection, String message);
        void onMessage(Connection connection, byte[] message);
        void onClose(Connection connection, int code, String reason);
        void onError(Connection connection, Exception ex);
    }

    public static class Frame {
        private final boolean isFin;
        private final int opcode;
        private final byte[] payload;

        public Frame(boolean isFin, int opcode, byte[] payload) {
            this.isFin = isFin; this.opcode = opcode; this.payload = payload;
        }

        public int getOpcode() { return opcode; }
        public byte[] getPayload() { return payload; }
        public String getPayloadAsText() { return new String(payload, StandardCharsets.UTF_8); }

        public ByteBuffer toByteBuffer() {
            int payloadLength = payload.length;
            int headerSize = 2;
            if (payloadLength > 65535) headerSize += 8;
            else if (payloadLength > 125) headerSize += 2;
            ByteBuffer buffer = ByteBuffer.allocate(headerSize + payloadLength);

            byte b0 = 0;
            if (isFin) b0 |= (byte) 0x80;
            b0 |= (byte) (opcode & 0x0F);
            buffer.put(b0);

            byte b1 = 0; // Mask bit is 0 for server-to-client
            if (payloadLength <= 125) {
                b1 |= (byte) payloadLength;
                buffer.put(b1);
            } else if (payloadLength <= 65535) {
                b1 |= 126;
                buffer.put(b1);
                buffer.putShort((short) payloadLength);
            } else {
                b1 |= 127;
                buffer.put(b1);
                buffer.putLong(payloadLength);
            }

            buffer.put(payload);
            buffer.flip();
            return buffer;
        }
    }

    public static class FrameParser {
        // Callback interface for streaming frame data
        public interface FrameDataHandler {
            void onFrameStart(boolean isFin, int opcode, long payloadLength);
            void onFramePayloadData(ByteBuffer payloadChunk);
            void onFrameEnd();
        }

        private enum State { READING_HEADER, READING_PAYLOAD_LEN_16, READING_PAYLOAD_LEN_64, READING_MASK, READING_PAYLOAD }
        private State state = State.READING_HEADER;
        private final byte[] smallHeaderBuffer = new byte[2];
        private int bytesRead = 0;
        private long payloadLength;
        private long payloadBytesRemaining; 
        private byte[] maskKey;
        private byte[] lengthBytes;

        public void parse(ByteBuffer buffer, FrameDataHandler handler) {
            while (buffer.hasRemaining()) {
                try {
                    switch (state) {
                        case READING_HEADER:
                            if (readBytes(buffer, smallHeaderBuffer, 2)) {
                                processHeader(handler);
                            }
                            break;
                        case READING_PAYLOAD_LEN_16:
                            if (lengthBytes == null) lengthBytes = new byte[2];
                            if (readBytes(buffer, lengthBytes, 2)) {
                                payloadLength = ByteBuffer.wrap(lengthBytes).getShort() & 0xFFFF;
                                payloadBytesRemaining = payloadLength;
                                bytesRead = 0;
                                state = State.READING_MASK;
                                lengthBytes = null;
                                handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
                            }
                            break;
                        case READING_PAYLOAD_LEN_64:
                            if (lengthBytes == null) lengthBytes = new byte[8];
                            if (readBytes(buffer, lengthBytes, 8)) {
                                payloadLength = ByteBuffer.wrap(lengthBytes).getLong();
                                payloadBytesRemaining = payloadLength;
                                bytesRead = 0;
                                state = State.READING_MASK;
                                lengthBytes = null;
                                handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
                            }
                            break;
                        case READING_MASK:
                            if (maskKey == null) maskKey = new byte[4];
                            if (readBytes(buffer, maskKey, 4)) {
                                state = State.READING_PAYLOAD;
                                bytesRead = 0;
                                if (payloadLength == 0) { // Handle empty frames
                                    handler.onFrameEnd();
                                    reset();
                                }
                            }
                            break;
                        case READING_PAYLOAD:
                            long toRead = Math.min(buffer.remaining(), payloadBytesRemaining);
                            if (toRead == 0) break; 

                            int originalLimit = buffer.limit();
                            buffer.limit(buffer.position() + (int)toRead);

                            unmaskAndProcessChunk(buffer, handler);

                            buffer.limit(originalLimit);
                            payloadBytesRemaining -= toRead;

                            if (payloadBytesRemaining == 0) {
                                handler.onFrameEnd();
                                reset();
                            }
                            break;
                    }
                } catch (RuntimeException e) {
                    handler.onFrameEnd(); 
                    throw e; 
                }
            }
        }

        private void unmaskAndProcessChunk(ByteBuffer chunk, FrameDataHandler handler) {
            ByteBuffer unmaskedChunk = ByteBuffer.allocate(chunk.remaining());
            int startPos = chunk.position();
            int len = chunk.remaining();

            for (int i = 0; i < chunk.remaining(); i++) {
                int payloadIndex = (int)((payloadLength - payloadBytesRemaining) + i);
                byte masked = chunk.get(chunk.position() + i);
                byte unmasked = (byte)(masked ^ maskKey[payloadIndex % 4]);
                unmaskedChunk.put(unmasked);
            }

            unmaskedChunk.flip();
            handler.onFramePayloadData(unmaskedChunk);
            chunk.position(startPos + len);
        }

        private void processHeader(FrameDataHandler handler) {
            payloadLength = smallHeaderBuffer[1] & 0x7F;
            if ((smallHeaderBuffer[1] & 0x80) == 0) throw new RuntimeException("Client frame must be masked");

            if (payloadLength <= 125) {
                payloadBytesRemaining = payloadLength;
                state = State.READING_MASK;
                handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
            } else if (payloadLength == 126) {
                state = State.READING_PAYLOAD_LEN_16;
            } else {
                state = State.READING_PAYLOAD_LEN_64;
            }
            bytesRead = 0;
        }

        public void reset() {
            state = State.READING_HEADER;
            bytesRead = 0;
            maskKey = null;
            lengthBytes = null;
            payloadLength = 0;
            payloadBytesRemaining = 0;
        }

        private boolean readBytes(ByteBuffer buffer, byte[] dest, int length) {
            int needed = length - bytesRead;
            int canRead = Math.min(buffer.remaining(), needed);
            buffer.get(dest, bytesRead, canRead);
            bytesRead += canRead;
            return bytesRead == length;
        }
    }
}
