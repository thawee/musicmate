package apincer.music.core.http;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Soak test for SonicNIO: concurrent clients mixing whole-file and range reads, keep-alive runs,
 * abrupt resets mid-stream and WebSocket bursts. Afterwards every counter must be back to zero.
 * Prints a throughput / time-to-first-byte baseline.
 * Scale with the SOAK_CLIENTS and SOAK_SECONDS environment variables (defaults 16 and 8).
 */
public class NioHttpServerSoakTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private static final int FILE_SIZE = 4 * 1024 * 1024;

    private NioHttpServer server;
    private int port;
    private byte[] content;

    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicInteger operations = new AtomicInteger();
    private final Queue<String> errors = new ConcurrentLinkedQueue<>();
    private final Queue<Long> rangeTtfbMicros = new ConcurrentLinkedQueue<>();

    @Before
    public void start() throws Exception {
        content = new byte[FILE_SIZE];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 251);
        File file = temporary.newFile("track.flac");
        Files.write(file.toPath(), content);

        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new NioHttpServer(port);
        server.setMaxConcurrentStreams(256); // steady state; eviction has its own test
        server.registerHttpHandler(request -> {
            try {
                return server.createFileResponse(file, request);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        server.registerWebSocketHandler(new WebSocket.Handler() {
            @Override public String getNamespace() { return "/ws"; }
            @Override public void onOpen(WebSocket.Connection connection) { }
            @Override public void onMessage(WebSocket.Connection connection, String message) { connection.send(message); }
            @Override public void onMessage(WebSocket.Connection connection, byte[] message) { }
            @Override public void onClose(WebSocket.Connection connection, int code, String reason) { }
            @Override public void onError(WebSocket.Connection connection, Exception ex) { }
        });
        Thread thread = new Thread(server, "nio-soak-server");
        thread.setDaemon(true);
        thread.start();
        for (int i = 0; i < 100; i++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException notYet) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("server did not start");
    }

    @After
    public void stop() {
        server.stop();
    }

    @Test
    public void mixedLoad_leavesNoLeaksAndServesCorrectBytes() throws Exception {
        int clients = intEnv("SOAK_CLIENTS", 16);
        int seconds = intEnv("SOAK_SECONDS", 8);
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        CountDownLatch done = new CountDownLatch(clients);

        long started = System.nanoTime();
        for (int c = 0; c < clients; c++) {
            Thread client = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() < deadline) {
                        runOneOperation();
                        operations.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t.getClass().getSimpleName() + ": " + t.getMessage());
                } finally {
                    done.countDown();
                }
            }, "soak-client-" + c);
            client.setDaemon(true);
            client.start();
        }
        done.await();
        double elapsed = (System.nanoTime() - started) / 1e9;

        // Everything must drain back to zero once clients are gone
        long drainDeadline = System.currentTimeMillis() + 10_000;
        while ((counter("activeStreams") != 0 || counter("activeConnections") != 0)
                && System.currentTimeMillis() < drainDeadline) {
            Thread.sleep(50);
        }

        printBaseline(clients, elapsed);
        assertEquals("errors: " + errors, 0, errors.size());
        assertEquals("activeStreams after load", 0, counter("activeStreams"));
        assertEquals("activeConnections after load", 0, counter("activeConnections"));
    }

    private void runOneOperation() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (random.nextInt(5)) {
            case 0: wholeFile(); break;
            case 1: rangeRead(); break;
            case 2: keepAliveRun(); break;
            case 3: abruptReset(); break;
            default: webSocketBurst(); break;
        }
    }

    private void wholeFile() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, "GET /track HTTP/1.1\r\nHost: soak\r\n\r\n");
            check(r.status == 200, "whole file status " + r.status);
            check(r.body.length == FILE_SIZE, "whole file length " + r.body.length);
            check(java.util.Arrays.equals(content, r.body), "whole file bytes differ");
        }
    }

    private void rangeRead() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int start = random.nextInt(FILE_SIZE - 1);
        int end = Math.min(FILE_SIZE - 1, start + random.nextInt(512 * 1024));
        try (Socket socket = connect()) {
            long t0 = System.nanoTime();
            Response r = exchange(socket, range(start, end));
            rangeTtfbMicros.add(r.firstByteNanos <= 0 ? 0 : (r.firstByteNanos - t0) / 1000);
            verifyRange(r, start, end);
        }
    }

    private void keepAliveRun() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try (Socket socket = connect()) {
            for (int i = 0; i < 3; i++) {
                int start = random.nextInt(FILE_SIZE - 1);
                int end = Math.min(FILE_SIZE - 1, start + random.nextInt(64 * 1024));
                verifyRange(exchange(socket, range(start, end)), start, end);
            }
        }
    }

    private void abruptReset() throws Exception {
        Socket socket = connect();
        socket.setReceiveBufferSize(8192);
        socket.getOutputStream().write("GET /track HTTP/1.1\r\nHost: soak\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
        socket.getInputStream().read(new byte[4096]);
        socket.setSoLinger(true, 0); // RST, as a renderer abandons a stream to seek
        socket.close();
    }

    private void webSocketBurst() throws Exception {
        try (Socket socket = connect()) {
            Response handshake = exchangeHeadersOnly(socket, "GET /ws HTTP/1.1\r\nHost: soak\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
            check(handshake.status == 101, "ws handshake " + handshake.status);
            OutputStream out = socket.getOutputStream();
            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            List<String> sent = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String text = "m" + i;
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                frames.write(0x81);
                frames.write(0x80 | payload.length);
                frames.write(new byte[4]);
                frames.write(payload);
                sent.add(text);
            }
            out.write(frames.toByteArray());
            out.flush();
            InputStream in = socket.getInputStream();
            for (String expected : sent) {
                check(in.read() == 0x81, "ws frame type");
                int length = in.read();
                String echoed = new String(in.readNBytes(length), StandardCharsets.UTF_8);
                check(expected.equals(echoed), "ws echo order: expected " + expected + " got " + echoed);
            }
        }
    }

    // --- helpers ---

    private void verifyRange(Response r, int start, int end) {
        check(r.status == 206, "range status " + r.status + " for " + start + "-" + end);
        int length = end - start + 1;
        check(r.body.length == length, "range length " + r.body.length + " expected " + length);
        for (int i = 0; i < r.body.length; i++) {
            if (r.body[i] != content[start + i]) {
                check(false, "range byte mismatch at " + (start + i));
                return;
            }
        }
    }

    private void check(boolean ok, String message) {
        if (!ok) errors.add(message);
    }

    private static String range(int start, int end) {
        return "GET /track HTTP/1.1\r\nHost: soak\r\nRange: bytes=" + start + "-" + end + "\r\n\r\n";
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(10_000);
        return socket;
    }

    private Response exchange(Socket socket, String raw) throws IOException {
        Response r = exchangeHeadersOnly(socket, raw);
        String lengthHeader = r.contentLength;
        int length = lengthHeader == null ? 0 : Integer.parseInt(lengthHeader);
        r.body = socket.getInputStream().readNBytes(length);
        bytesRead.addAndGet(r.body.length);
        return r;
    }

    private static Response exchangeHeadersOnly(Socket socket, String raw) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(raw.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        InputStream in = socket.getInputStream();
        Response response = new Response();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) throw new IOException("connection closed before headers ended");
            if (response.firstByteNanos == 0) response.firstByteNanos = System.nanoTime();
            head.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");
        response.status = Integer.parseInt(lines[0].split(" ")[1]);
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0 && lines[i].substring(0, colon).trim().equalsIgnoreCase("content-length")) {
                response.contentLength = lines[i].substring(colon + 1).trim();
            }
        }
        return response;
    }

    private int counter(String name) throws Exception {
        Field field = NioHttpServer.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((AtomicInteger) field.get(server)).get();
    }


    private void printBaseline(int clients, double seconds) {
        List<Long> ttfb = new ArrayList<>(rangeTtfbMicros);
        Collections.sort(ttfb);
        long p50 = ttfb.isEmpty() ? 0 : ttfb.get(ttfb.size() / 2);
        long p95 = ttfb.isEmpty() ? 0 : ttfb.get((int) (ttfb.size() * 0.95));
        System.out.println(String.format(Locale.ROOT,
                "SOAK clients=%d seconds=%.1f operations=%d throughput=%.1f MB/s rangeTTFB p50=%d us p95=%d us errors=%d",
                clients, seconds, operations.get(), bytesRead.get() / 1048576.0 / seconds, p50, p95, errors.size()));
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class Response {
        int status;
        String contentLength;
        long firstByteNanos;
        byte[] body = new byte[0];
    }
}
