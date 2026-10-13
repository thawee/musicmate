package apincer.music.core.http;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.ByteBuffer;

public class WebSocketFrameParserTest {

    /** A 64-bit length with the top bit set is negative; it must be rejected before any payload is buffered. */
    @Test(timeout = 5000)
    public void negativeExtendedLengthIsRejected() {
        ByteBuffer frame = ByteBuffer.allocate(14);
        frame.put((byte) 0x89);          // FIN + PING
        frame.put((byte) 0xFF);          // masked, 64-bit length follows
        frame.putLong(-1L);              // high bit set -> negative length
        frame.putInt(0);                 // mask key
        frame.flip();

        assertRejectsWithoutStart(frame);
    }

    /** RFC 6455 §5.5: a control frame may carry at most 125 bytes. */
    @Test(timeout = 5000)
    public void oversizedControlFrameIsRejected() {
        ByteBuffer frame = ByteBuffer.allocate(4);
        frame.put((byte) 0x89);          // FIN + PING
        frame.put((byte) (0x80 | 126));  // masked, 16-bit extended length (illegal for control)
        frame.putShort((short) 200);     // 200 bytes > 125
        frame.flip();

        assertRejectsWithoutStart(frame);
    }

    private void assertRejectsWithoutStart(ByteBuffer frame) {
        boolean[] started = {false};
        WebSocket.FrameParser parser = new WebSocket.FrameParser();
        WebSocket.FrameParser.FrameDataHandler handler = new WebSocket.FrameParser.FrameDataHandler() {
            @Override public void onFrameStart(boolean isFin, int opcode, long payloadLength) { started[0] = true; }
            @Override public void onFramePayloadData(ByteBuffer payloadChunk) { }
            @Override public void onFrameEnd() { }
        };

        try {
            parser.parse(frame, handler);
            fail("malformed frame length must be rejected");
        } catch (RuntimeException expected) {
            assertFalse("frame was started before rejection", started[0]);
        }
    }
}
