package apincer.music.core.http;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileResponseTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void metadataEncoding_preservesLegacyValidatorsAndDates() throws Exception {
        File file = temporary.newFile("metadata.flac");
        FileResponse response = new FileResponse(file, new NioHttpServer.HttpRequest(), noLimit());
        try {
            Method etag = FileResponse.class.getDeclaredMethod("generateETag", File.class, long.class, long.class);
            Method date = FileResponse.class.getDeclaredMethod("formatHttpDate", long.class);
            etag.setAccessible(true);
            date.setAccessible(true);
            for (int size : new int[] {0, 1, 255, 4096, 65537}) {
                Files.write(file.toPath(), new byte[size]);
                String value = file.getAbsolutePath() + "-" + file.length() + "-" + file.lastModified();
                byte[] hash = MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                assertEquals("\"" + hex.substring(0, 16) + "-" + Long.toHexString(file.length()) + "\"",
                        etag.invoke(response, file, file.length(), file.lastModified()));
                FileResponse actual = new FileResponse(file, new NioHttpServer.HttpRequest(), noLimit());
                try {
                    assertEquals(etag.invoke(response, file, file.length(), file.lastModified()), actual.headers.get("ETag"));
                    assertEquals(legacyDate(file.lastModified()), actual.headers.get("Last-Modified"));
                } finally { actual.close(); }
            }
            for (long timestamp : dateCases()) {
                assertEquals("timestamp " + timestamp, legacyDate(timestamp), date.invoke(response, timestamp));
            }
        } finally { response.close(); }
    }

    @Test
    public void replacedFile_hasFreshValidatorsAndRangeLengthWithoutCachedMetadata() throws Exception {
        File file = temporary.newFile("replace.flac");
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        FileResponse first = new FileResponse(file, request(""), noLimit());
        String oldTag = first.headers.get("ETag");
        first.close();
        Files.write(file.toPath(), new byte[] {4, 5, 6, 7, 8, 9, 10});
        Files.setLastModifiedTime(file.toPath(), java.nio.file.attribute.FileTime.fromMillis(1704067200000L));
        FileResponse next = new FileResponse(file, request("If-None-Match: " + oldTag), noLimit());
        String newTag;
        try {
            assertEquals(200, next.statusCode);
            newTag = next.headers.get("ETag");
            assertNotEquals(oldTag, newTag);
            assertEquals(legacyDate(1704067200000L), next.headers.get("Last-Modified"));
            assertEquals("7", next.headers.get("Content-Length"));
        } finally { next.close(); }
        FileResponse conditional = new FileResponse(file, request("If-None-Match: " + newTag), noLimit());
        try { assertEquals(304, conditional.statusCode); }
        finally { conditional.close(); }
        FileResponse range = new FileResponse(file, request("Range: bytes=1-4"), noLimit());
        try {
            assertEquals(206, range.statusCode);
            assertEquals("bytes 1-4/7", range.headers.get("Content-Range"));
            assertEquals("4", range.headers.get("Content-Length"));
        } finally { range.close(); }
    }

    @Test
    public void missingMetadata_doesNotAcquireOrReleaseAStreamSlot() throws Exception {
        AtomicInteger acquired = new AtomicInteger(), released = new AtomicInteger();
        StreamSlots slots = new StreamSlots() {
            public boolean acquire() { acquired.incrementAndGet(); return true; }
            public void release() { released.incrementAndGet(); }
        };
        try {
            new FileResponse(new File(temporary.getRoot(), "absent.flac"), request(""), slots);
            fail("missing file must fail");
        } catch (java.io.IOException expected) { }
        assertEquals(0, acquired.get());
        assertEquals(0, released.get());
    }

    private static NioHttpServer.HttpRequest request(String header) {
        byte[] data = ("GET /file HTTP/1.1\r\n" + header + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        NioHttpServer.HttpRequest request = new NioHttpServer.HttpRequest();
        request.parse(data, data.length, "127.0.0.1");
        return request;
    }

    @Test
    public void metadataDateFormatter_isSafeAcrossWorkers() throws Exception {
        FileResponse response = new FileResponse(temporary.newFile("concurrent.flac"),
                new NioHttpServer.HttpRequest(), noLimit());
        Method date = FileResponse.class.getDeclaredMethod("formatHttpDate", long.class);
        date.setAccessible(true);
        var workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) jobs.add(() -> {
                for (int repeat = 0; repeat < 50; repeat++) {
                    for (long timestamp : dateCases())
                        assertEquals(legacyDate(timestamp), date.invoke(response, timestamp));
                }
                return null;
            });
            for (var result : workers.invokeAll(jobs, 20, TimeUnit.SECONDS)) result.get();
        } finally {
            workers.shutdownNow();
            response.close();
        }
    }

    private static long[] dateCases() {
        return new long[] {Long.MIN_VALUE, -12219292800001L, -12219292800000L, -1, 0,
                951782399999L, 951782400000L, 1704067199999L, 1704067200000L,
                4107542400000L, 253402300799999L, 253402300800000L, Long.MAX_VALUE};
    }

    private static String legacyDate(long timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        return formatter.format(new Date(timestamp));
    }

    private static StreamSlots noLimit() {
        return new StreamSlots() {
            public boolean acquire() { return true; }
            public void release() { }
        };
    }

    @Test
    public void changingTurnBudget_preservesFileOffsetsAndExactTail() throws Exception {
        byte[] body = new byte[1280 * 1024 + 123];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
        File file = temporary.newFile("body.flac");
        Files.write(file.toPath(), body);
        AtomicInteger slots = new AtomicInteger();
        StreamSlots admission = new StreamSlots() {
            public boolean acquire() { slots.incrementAndGet(); return true; }
            public void release() { slots.decrementAndGet(); }
        };
        FileResponse response = new FileResponse(file, new NioHttpServer.HttpRequest(), admission);
        try {
            StreamingResponseTest.AcceptingChannel channel = new StreamingResponseTest.AcceptingChannel();
            response.headersSent = true;
            response.write(channel, 1024 * 1024);
            assertEquals(1024 * 1024, response.bodyBytesSent());
            response.write(channel, 256 * 1024);
            assertEquals(1280 * 1024, response.bodyBytesSent());
            response.write(channel, 256 * 1024);
            assertTrue(response.isFullySent());
            assertArrayEquals(body, channel.bytes.toByteArray());
        } finally { response.close(); }
        assertEquals(0, slots.get());
    }
}
