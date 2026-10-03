package apincer.music.core.http;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class HttpRequestTest {
    @Test public void identicalLengths_areNormalizedAcrossLinesAndLists() {
        for (String fields : new String[] {"Content-Length: 5", "Content-Length: 5, 005",
                "Content-Length: 005\r\ncOnTeNt-LeNgTh: 5"}) {
            var request = parse(fields, "hello");
            assertEquals(5, request.contentLength());
            assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), request.getBody());
        }
    }

    @Test public void invalidOrConflictingLengths_areRejected() {
        for (String value : new String[] {"", "-1", "+5", "5,6", "5,", ",5", "5x", "9223372036854775808"}) {
            try { parse("Content-Length: " + value, "").contentLength(); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
        try { parse("Content-Length: 5\r\nContent-Length: 6", "").contentLength(); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void manyIdenticalLengths_remainOneCanonicalValue() {
        var request = parse("Content-Length: 5\r\n".repeat(2000) + "Content-Length: 005", "hello");
        assertEquals(5, request.contentLength());
        assertEquals("5", request.getHeader("content-length", null));
    }

    @Test public void reusedRequest_doesNotKeepPriorFramingOrBody() {
        var request = parse("Content-Length: 5", "hello");
        byte[] next = "GET /next HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        request.parseHeaders(next, next.length, "host");
        assertEquals(0, request.contentLength());
        assertNull(request.getBody());
        assertEquals("/next", request.getPath());
    }

    @Test public void rangeCopy_isExactIndependentAndBoundedByWrittenBytes() {
        var buffer = new BoundedByteArrayOutputStream(128, 128);
        buffer.write(new byte[] {1, 2, 3, 4}, 0, 4);
        byte[] copy = buffer.copyRange(1, 3);
        assertArrayEquals(new byte[] {2, 3}, copy);
        copy[0] = 99;
        assertArrayEquals(new byte[] {2, 3}, buffer.copyRange(1, 3));
        try { buffer.copyRange(0, 5); fail(); } catch (IndexOutOfBoundsException expected) { }
    }

    @Test public void everySplitPoint_preservesHeaderBoundaryAndExactBodySlice() {
        byte[] header = "POST /ctl HTTP/1.1\r\nHost: test\r\nContent-Length: 5\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] packet = (new String(header, StandardCharsets.US_ASCII) + "helloGET /next HTTP/1.1\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        for (int split = 0; split <= packet.length; split++) {
            var buffer = new BoundedByteArrayOutputStream(8, 1024);
            buffer.write(packet, 0, split);
            int end = buffer.indexOfHeaderEnd(0);
            int cursor = end < 0 ? Math.max(0, split - 3) : 0;
            buffer.write(packet, split, packet.length - split);
            assertEquals(header.length, buffer.indexOfHeaderEnd(cursor));
            var request = new NioHttpServer.HttpRequest();
            request.parseHeaders(buffer.copyRange(0, header.length), header.length, "host");
            assertEquals(5, request.contentLength());
            assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), buffer.copyRange(header.length, header.length + 5));
            assertArrayEquals(Arrays.copyOfRange(packet, header.length + 5, packet.length),
                    buffer.copyRange(header.length + 5, buffer.size()));
        }
    }

    private static NioHttpServer.HttpRequest parse(String fields, String body) {
        String header = "POST /ctl HTTP/1.1\r\nHost: test\r\n" + fields + "\r\n\r\n";
        byte[] packet = (header + body).getBytes(StandardCharsets.US_ASCII);
        var request = new NioHttpServer.HttpRequest();
        request.parse(packet, header.length(), "host");
        return request;
    }
}
