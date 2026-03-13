package apincer.android.jupnp.server.httpcore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WebSocketFrameEncoder {
    /**
     * Encodes a String into a standard WebSocket Text Frame (Opcode 0x1)
     */
    public static ByteBuffer encodeTextFrame(String data) {
        byte[] payload = data.getBytes(StandardCharsets.UTF_8);
        int length = payload.length;

        // Basic frame header: 1 byte for FIN/Opcode + 1-9 bytes for length
        int headerSize = (length <= 125) ? 2 : (length <= 65535) ? 4 : 10;
        ByteBuffer frame = ByteBuffer.allocate(headerSize + length);

        // FIN = 1, Opcode = 1 (Text) -> 10000001 = 0x81
        frame.put((byte) 0x81);

        if (length <= 125) {
            frame.put((byte) length);
        } else if (length <= 65535) {
            frame.put((byte) 126);
            frame.putShort((short) length);
        } else {
            frame.put((byte) 127);
            frame.putLong(length);
        }

        frame.put(payload);
        frame.flip();
        return frame;
    }
}