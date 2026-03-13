package apincer.android.jupnp.server.httpcore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WebSocketFrameDecoder {
    public static String decodeTextFrame(ByteBuffer buffer) {
        if (buffer.remaining() < 2) return null;

        byte b1 = buffer.get(); // FIN + Opcode
        byte b2 = buffer.get(); // Mask bit + Payload length

        boolean isMasked = (b2 & 0x80) != 0;
        int payloadLen = b2 & 0x7F;

        // Handle extended lengths if necessary (skipping for simplicity here)
        if (payloadLen == 126) buffer.getShort();
        else if (payloadLen == 127) buffer.getLong();

        // RFC 6455: Client-to-Server frames MUST be masked
        byte[] maskingKey = new byte[4];
        if (isMasked) {
            buffer.get(maskingKey);
        }

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        // Unmask the data
        if (isMasked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }
}
