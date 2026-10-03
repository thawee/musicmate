package apincer.music.core.http;

import com.sun.management.ThreadMXBean;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/** Host allocation/cost experiment; reflection and file metadata syscalls are included. */
public final class MetadataBenchmark {
    private static volatile long consumed;
    private static final int OPERATIONS = 10_000;
    private static final StreamSlots SLOTS = new StreamSlots() {
        public boolean acquire() { return true; }
        public void release() { }
    };

    public static void main(String[] args) throws Exception {
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!allocation.isThreadAllocatedMemorySupported()) throw new IllegalStateException("allocation unavailable");
        allocation.setThreadAllocatedMemoryEnabled(true);
        File file = Files.createTempFile("sonicnio-metadata-", ".flac").toFile();
        try {
            try (RandomAccessFile out = new RandomAccessFile(file, "rw")) { out.setLength(16 * 1024 * 1024); }
            FileResponse reference = new FileResponse(file, new NioHttpServer.HttpRequest(), SLOTS);
            try {
                Method etag;
                try { etag = FileResponse.class.getDeclaredMethod("generateETag", File.class); }
                catch (NoSuchMethodException snapshotVersion) {
                    etag = FileResponse.class.getDeclaredMethod("generateETag", File.class, long.class, long.class);
                }
                Method date = FileResponse.class.getDeclaredMethod("formatHttpDate", long.class);
                etag.setAccessible(true); date.setAccessible(true);
                NioHttpServer.HttpRequest conditional = request("If-None-Match: " + reference.headers.get("ETag"));
                NioHttpServer.HttpRequest range = request("Range: bytes=0-65535");
                for (String mode : new String[] {"etag", "date", "conditional-headers", "range-headers"}) {
                    for (int sample = 0; sample <= 5; sample++) {
                        long beforeBytes = allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
                        long start = System.nanoTime(), checksum = 0;
                        for (int i = 0; i < OPERATIONS; i++) {
                            if (mode.equals("etag")) {
                                if (etag.getParameterCount() == 1) checksum += ((String) etag.invoke(reference, file)).length();
                                else {
                                    BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                    checksum += ((String) etag.invoke(reference, file, attributes.size(),
                                            attributes.lastModifiedTime().toMillis())).length();
                                }
                            }
                            else if (mode.equals("date")) checksum += ((String) date.invoke(reference,
                                    1704067200000L + i * 1000L)).length();
                            else {
                                FileResponse response = new FileResponse(file,
                                        mode.equals("conditional-headers") ? conditional : range, SLOTS);
                                try {
                                    response.buildHeaders();
                                    int expected = mode.equals("conditional-headers") ? 304 : 206;
                                    if (response.statusCode != expected) throw new AssertionError(response.statusCode);
                                    checksum += response.headerBuffer.remaining();
                                } finally { response.close(); }
                            }
                        }
                        long elapsed = System.nanoTime() - start;
                        long allocated = allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                        consumed = checksum;
                        if (sample > 0) System.out.printf(java.util.Locale.ROOT,
                                "{\"label\":\"%s\",\"mode\":\"%s\",\"sample\":%d,\"operations\":%d,\"ns_per_op\":%.3f,\"bytes_per_op\":%.3f,\"checksum\":%d,\"class_origin\":\"%s\"}%n",
                                args[0], mode, sample, OPERATIONS, elapsed / (double) OPERATIONS,
                                allocated / (double) OPERATIONS, consumed,
                                FileResponse.class.getProtectionDomain().getCodeSource().getLocation());
                    }
                }
            } finally { reference.close(); }
        } finally { Files.deleteIfExists(file.toPath()); }
    }

    private static NioHttpServer.HttpRequest request(String header) {
        byte[] bytes = ("GET /file HTTP/1.1\r\n" + header + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        NioHttpServer.HttpRequest request = new NioHttpServer.HttpRequest();
        request.parse(bytes, bytes.length, "127.0.0.1");
        return request;
    }
}
