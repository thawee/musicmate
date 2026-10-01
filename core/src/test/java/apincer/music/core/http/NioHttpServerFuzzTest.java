package apincer.music.core.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fuzzes SonicNIO's HTTP and WebSocket parsing over real sockets. Properties: the server keeps
 * serving correct bytes, every reply starts with a valid status line (or the connection closes),
 * and all counters drain to zero. Set FUZZ_SEED to replay a run; FUZZ_CASES and FUZZ_WS_CASES scale it.
 */
public class NioHttpServerFuzzTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private NioHttpServer server;
    private int port;
    private byte[] content;

    private static final String[] CORPUS = {
            "GET /track HTTP/1.1\r\nHost: f\r\n\r\n",
            "GET /track HTTP/1.1\r\nHost: f\r\nRange: bytes=10-99\r\n\r\n",
            "HEAD /track HTTP/1.1\r\nHost: f\r\n\r\n",
            "POST /ctl HTTP/1.1\r\nHost: f\r\nContent-Length: 11\r\nContent-Type: text/xml\r\n\r\n<a>body</a>",
            "GET /ws HTTP/1.1\r\nHost: f\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n",
    };

    private static final String[] HOSTILE = {
            "GET /track HTTP/1.1\r\nRange: bytes=-0\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: bytes=50-10\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: bytes=99999999999999999999-\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: bytes=--\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: bytes= 1-2\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: items=0-5\r\n\r\n",
            "GET /track HTTP/1.1\r\nRange: bytes=0-1,2-3,4-5\r\n\r\n",
            "GET /track HTTP/1.1\r\nIf-Range: \r\nRange: bytes=0-3\r\n\r\n",
            "POST /ctl HTTP/1.1\r\nContent-Length: abc\r\n\r\n",
            "POST /ctl HTTP/1.1\r\nContent-Length: -1\r\n\r\n",
            "POST /ctl HTTP/1.1\r\nContent-Length: 99999999999\r\n\r\n",
            "POST /ctl HTTP/1.1\r\nContent-Length: 5\r\nContent-Length: 50\r\n\r\nhello",
            "POST /ctl HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n",
            "GET /track HTTP/1.1\nHost: f\n\n",
            "GET\r\n\r\n",
            " \r\n\r\n",
            "\r\n\r\n",
            "GET /track HTTP/1.1\r\n: no-name\r\nHost\r\n\r\n",
            "GET /tr\u0000ack HTTP/1.1\r\nHost: f\u0000\r\n\r\n",
            "GET /%2e%2e/%2e%2e/etc/passwd HTTP/1.1\r\n\r\n",
            "GET /track HTTP/9.9\r\n\r\n",
            "BREW /track HTCPCP/1.0\r\n\r\n",
            "GET /ws HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n", // no key
    };

    @Before
    public void start() throws Exception {
        content = new byte[64 * 1024];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 251);
        File file = temporary.newFile("track.flac");
        Files.write(file.toPath(), content);
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new NioHttpServer(port);
        server.setKeepAliveTimeout(2000);
        server.registerHttpHandler(request -> {
            try {
                if ("POST".equals(request.getMethod())) {
                    return new NioHttpServer.HttpResponse().setBody(request.getBody());
                }
                return server.createFileResponse(file, request);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        server.registerWebSocketHandler(new WebSocket.Handler() {
            @Override public String getNamespace() { return "/ws"; }
            @Override public void onOpen(WebSocket.Connection connection) { }
            @Override public void onMessage(WebSocket.Connection connection, String message) { connection.send(message); }
            @Override public void onMessage(WebSocket.Connection connection, byte[] message) { connection.send(message); }
            @Override public void onClose(WebSocket.Connection connection, int code, String reason) { }
            @Override public void onError(WebSocket.Connection connection, Exception ex) { }
        });
        Thread thread = new Thread(server, "nio-fuzz-server");
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
    public void hostileRequests_neverBreakTheServer() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String raw : HOSTILE) {
            String problem = sendAndCheck(raw.getBytes(StandardCharsets.ISO_8859_1));
            if (problem != null) problems.add(problem + " <- " + printable(raw));
            assertHealthy();
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
        assertDrained();
    }

    @Test
    public void mutatedRequests_neverBreakTheServer() throws Exception {
        long seed = longEnv("FUZZ_SEED", System.nanoTime());
        int cases = (int) longEnv("FUZZ_CASES", 1500);
        System.out.println("FUZZ seed=" + seed + " cases=" + cases);
        Random random = new Random(seed);
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < cases; i++) {
            byte[] input = mutate(CORPUS[random.nextInt(CORPUS.length)].getBytes(StandardCharsets.ISO_8859_1), random);
            String problem = sendAndCheck(input);
            if (problem != null && problems.size() < 20) problems.add("case " + i + ": " + problem + " <- " + printable(new String(input, StandardCharsets.ISO_8859_1)));
            if (i % 100 == 0) assertHealthy();
        }
        assertHealthy();
        assertTrue("seed " + seed + "\n" + String.join("\n", problems), problems.isEmpty());
        assertDrained();
    }

    @Test
    public void randomWebSocketFrames_neverBreakTheServer() throws Exception {
        long seed = longEnv("FUZZ_SEED", System.nanoTime());
        int cases = (int) longEnv("FUZZ_WS_CASES", 40);
        System.out.println("FUZZ ws seed=" + seed + " cases=" + cases);
        Random random = new Random(seed);
        for (int i = 0; i < cases; i++) {
            try (Socket socket = connect(300)) {
                socket.getOutputStream().write(CORPUS[4].getBytes(StandardCharsets.ISO_8859_1));
                readStatusLine(socket.getInputStream());
                byte[] junk = new byte[1 + random.nextInt(64)];
                random.nextBytes(junk);
                if (random.nextBoolean()) junk[0] = (byte) (0x80 | random.nextInt(16)); // plausible FIN + opcode
                if (junk.length > 1 && random.nextBoolean()) junk[1] = (byte) (0x80 | (junk[1] & 0x7F)); // masked
                socket.getOutputStream().write(junk);
                socket.getOutputStream().flush();
                drainQuietly(socket.getInputStream());
            } catch (IOException ignored) {
                // a closed connection is an acceptable outcome
            }
            if (i % 50 == 0) assertHealthy();
        }
        assertHealthy();
        assertDrained();
    }

    // --- checks ---

    /** Sends raw bytes; returns a problem description, or null if the server behaved. */
    private String sendAndCheck(byte[] raw) {
        try (Socket socket = connect(1500)) {
            socket.getOutputStream().write(raw);
            socket.getOutputStream().flush();
            socket.shutdownOutput(); // no more input: the server must answer or close
            String status = readStatusLine(socket.getInputStream());
            if (status == null) return null; // closed without a reply: acceptable for garbage
            if (!status.matches("HTTP/1\\.1 [1-5]\\d\\d .*")) return "bad status line [" + status + "]";
            return null;
        } catch (SocketTimeoutException e) {
            return null; // waiting for more bytes of an incomplete request is acceptable
        } catch (IOException e) {
            return null; // reset/closed: acceptable
        }
    }

    private void assertHealthy() throws Exception {
        try (Socket socket = connect(5000)) {
            socket.getOutputStream().write("GET /track HTTP/1.1\r\nHost: f\r\nRange: bytes=100-199\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            InputStream in = socket.getInputStream();
            String status = readStatusLine(in);
            assertEquals("HTTP/1.1 206 Partial Content", status);
            skipHeaders(in);
            assertArrayEquals(Arrays.copyOfRange(content, 100, 200), in.readNBytes(100));
        }
    }

    private void assertDrained() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while ((counter("activeStreams") != 0 || counter("activeConnections") != 0)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals("activeStreams", 0, counter("activeStreams"));
        assertEquals("activeConnections", 0, counter("activeConnections"));
    }

    // --- mutation ---

    private static byte[] mutate(byte[] input, Random random) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] data = input.clone();
        int edits = 1 + random.nextInt(4);
        for (int e = 0; e < edits && data.length > 0; e++) {
            int at = random.nextInt(data.length);
            switch (random.nextInt(6)) {
                case 0: data[at] = (byte) random.nextInt(256); break;                       // flip a byte
                case 1: data[at] = (byte) "\r\n:- 0123456789,=".charAt(random.nextInt(17)); break; // structural byte
                case 2: data = splice(data, at, Math.min(data.length, at + 1 + random.nextInt(8)), new byte[0]); break; // cut
                case 3: { byte[] junk = new byte[1 + random.nextInt(16)]; random.nextBytes(junk); data = splice(data, at, at, junk); break; }
                case 4: data = splice(data, at, at, data.clone()); break;                    // duplicate
                default: data = Arrays.copyOf(data, at); break;                             // truncate
            }
        }
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    private static byte[] splice(byte[] data, int from, int to, byte[] insert) {
        byte[] result = new byte[data.length - (to - from) + insert.length];
        System.arraycopy(data, 0, result, 0, from);
        System.arraycopy(insert, 0, result, from, insert.length);
        System.arraycopy(data, to, result, from + insert.length, data.length - to);
        return result;
    }

    // --- io helpers ---

    private Socket connect(int timeoutMs) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    private static String readStatusLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') break;
            if (b != '\r') line.write(b);
            if (line.size() > 200) break;
        }
        if (b < 0 && line.size() == 0) return null;
        return line.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void skipHeaders(InputStream in) throws IOException {
        int matched = 2; // the status line's CRLF was consumed; look for the blank line
        int previous = '\n';
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("closed in headers");
            if (b == '\n' && previous == '\r') {
                if (matched == 2) return;
                matched = 2;
            } else if (b != '\r') {
                matched = 0;
            }
            previous = b;
        }
    }

    private static void drainQuietly(InputStream in) {
        try {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) >= 0) { /* until close or timeout */ }
        } catch (IOException ignored) {
        }
    }

    private int counter(String name) throws Exception {
        Field field = NioHttpServer.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((AtomicInteger) field.get(server)).get();
    }

    private static String printable(String raw) {
        String s = raw.replace("\r", "\\r").replace("\n", "\\n").replace("\u0000", "\\0");
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }

    private static long longEnv(String name, long fallback) {
        String value = System.getenv(name);
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
