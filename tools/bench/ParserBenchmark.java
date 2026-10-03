package apincer.music.core.http;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Models selector-owned buffered request extraction; excludes reads, XML processing and handlers. */
public final class ParserBenchmark {
    private static volatile long consumed;
    public static void main(String[] args) throws Exception {
        boolean staged = args.length > 1 && args[1].equals("staged");
        boolean ranges;
        try { BoundedByteArrayOutputStream.class.getDeclaredMethod("copyRange", int.class, int.class); ranges = true; }
        catch (NoSuchMethodException baseline) { ranges = false; }
        boolean framing;
        try { NioHttpServer.HttpRequest.class.getDeclaredMethod("contentLength"); framing = true; }
        catch (NoSuchMethodException baseline) { framing = false; }
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (int size : new int[] {0, 4096, 262144, 1048576}) {
            int operations = size == 0 ? 20000 : size == 4096 ? 5000 : size == 262144 ? 500 : 125;
            byte[] header = ((size == 0 ? "GET" : "POST") + " /ctl HTTP/1.1\r\nHost: test\r\nContent-Length: "
                    + size + "\r\nContent-Type: text/xml\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            byte[] tail = "GET /next HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            byte[] body = new byte[size];
            for (int i = 0; i < size; i++) body[i] = (byte) (i % 251);
            var buffer = new BoundedByteArrayOutputStream(8192, 2 * 1024 * 1024);
            buffer.write(header, 0, header.length); buffer.write(body, 0, body.length); buffer.write(tail, 0, tail.length);
            var firstRead = new BoundedByteArrayOutputStream(8192, 2 * 1024 * 1024);
            byte[] packet = buffer.toByteArray();
            firstRead.write(packet, 0, Math.min(packet.length, 8192));
            for (int sample = 0; sample <= 5; sample++) {
                long allocated = allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
                long started = System.nanoTime(), checksum = 0;
                for (int i = 0; i < operations; i++) {
                    var request = new NioHttpServer.HttpRequest();
                    var headersBuffer = staged ? firstRead : buffer;
                    byte[] extra;
                    if (ranges) {
                        request.parseHeaders(headersBuffer.copyRange(0, header.length), header.length, "host");
                        if (framing && request.contentLength() != size) throw new AssertionError("length");
                        request.setBody(buffer.copyRange(header.length, header.length + size));
                        extra = buffer.copyRange(header.length + size, buffer.size());
                    } else {
                        request.parse(headersBuffer.toByteArray(), header.length, "host");
                        request.setBody(Arrays.copyOfRange(buffer.toByteArray(), header.length, header.length + size));
                        extra = Arrays.copyOfRange(buffer.toByteArray(), header.length + size, buffer.size());
                    }
                    byte[] parsed = request.getBody();
                    if (parsed.length != size || extra.length != tail.length || extra[0] != 'G'
                            || size > 0 && (parsed[0] != body[0] || parsed[size - 1] != body[size - 1]))
                        throw new AssertionError("body/tail");
                    checksum += parsed.length + extra.length;
                }
                long elapsed = System.nanoTime() - started;
                allocated = allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocated;
                consumed = checksum;
                if (sample > 0) System.out.printf(Locale.ROOT,
                        "{\"label\":\"%s\",\"body_size\":%d,\"sample\":%d,\"operations\":%d,\"ns_per_op\":%.3f,\"bytes_per_op\":%.3f,\"checksum\":%d,\"class_origin\":\"%s\"}%n",
                        args[0], size, sample, operations, elapsed / (double) operations, allocated / (double) operations,
                        consumed, NioHttpServer.class.getProtectionDomain().getCodeSource().getLocation());
            }
        }
    }
}
