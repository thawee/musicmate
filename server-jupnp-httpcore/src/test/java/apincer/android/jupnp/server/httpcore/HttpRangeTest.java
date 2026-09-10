package apincer.android.jupnp.server.httpcore;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpRangeTest {

    private static final long FILE_SIZE = 10_000_000L; // 10MB

    @Test
    public void testNullOrInvalidHeader_returnsFullFile() {
        HttpCoreWebServerImpl.HttpRange range1 = HttpCoreWebServerImpl.parseRange(null, FILE_SIZE);
        assertFalse(range1.partial);
        assertTrue(range1.satisfiable);
        assertEquals(0, range1.start);
        assertEquals(FILE_SIZE - 1, range1.end);
        assertEquals(FILE_SIZE, range1.getContentLength());

        HttpCoreWebServerImpl.HttpRange range2 = HttpCoreWebServerImpl.parseRange("invalid", FILE_SIZE);
        assertFalse(range2.partial);
        assertTrue(range2.satisfiable);
        assertEquals(0, range2.start);
        assertEquals(FILE_SIZE - 1, range2.end);
    }

    @Test
    public void testStandardRange_parsesCorrectly() {
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=0-1023", FILE_SIZE);
        assertTrue(range.partial);
        assertTrue(range.satisfiable);
        assertEquals(0, range.start);
        assertEquals(1023, range.end);
        assertEquals(1024, range.getContentLength());
    }

    @Test
    public void testOpenEndedRange_parsesToEndOfFile() {
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=1000-", FILE_SIZE);
        assertTrue(range.partial);
        assertTrue(range.satisfiable);
        assertEquals(1000, range.start);
        assertEquals(FILE_SIZE - 1, range.end);
        assertEquals(FILE_SIZE - 1000, range.getContentLength());
    }

    @Test
    public void testSuffixRange_parsesLastBytes() {
        // bytes=-500 -> last 500 bytes of the file
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=-500", FILE_SIZE);
        assertTrue(range.partial);
        assertTrue(range.satisfiable);
        assertEquals(FILE_SIZE - 500, range.start);
        assertEquals(FILE_SIZE - 1, range.end);
        assertEquals(500, range.getContentLength());
    }

    @Test
    public void testUnboundedRange_clampsToFileLength() {
        // DLNA renderers and browsers often send bytes=0-2147483647
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=0-2147483647", FILE_SIZE);
        assertTrue(range.partial);
        assertTrue(range.satisfiable);
        assertEquals(0, range.start);
        assertEquals(FILE_SIZE - 1, range.end);
        assertEquals(FILE_SIZE, range.getContentLength());
    }

    @Test
    public void testUnsatisfiableRange_marksUnsatisfiable() {
        // bytes=20000000- on 10MB file -> 416
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=20000000-", FILE_SIZE);
        assertTrue(range.partial);
        assertFalse(range.satisfiable);
    }

    @Test
    public void testMultiRange_usesFirstRange() {
        HttpCoreWebServerImpl.HttpRange range = HttpCoreWebServerImpl.parseRange("bytes=0-499, 1000-1499", FILE_SIZE);
        assertTrue(range.partial);
        assertTrue(range.satisfiable);
        assertEquals(0, range.start);
        assertEquals(499, range.end);
        assertEquals(500, range.getContentLength());
    }
}
