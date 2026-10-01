package apincer.music.core.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** End-to-end tests of SonicNIO over a real socket: ranges, HEAD, keep-alive and WebSocket. */
public class NioHttpServerTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private NioHttpServer server;
    private int port;
    private byte[] content;
    private File bigFile;
    private final AtomicReference<String> lastPostBody = new AtomicReference<>();

    @Before
    public void startServer() throws Exception {
        content = new byte[1000];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 251);
        File file = temporary.newFile("track.flac");
        Files.write(file.toPath(), content);
        bigFile = temporary.newFile("big.flac");
        Files.write(bigFile.toPath(), new byte[8 * 1024 * 1024]);

        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new NioHttpServer(port);
        server.registerHttpHandler(request -> {
            try {
                if ("POST".equals(request.getMethod())) {
                    lastPostBody.set(new String(request.getBody(), StandardCharsets.UTF_8));
                    return new NioHttpServer.HttpResponse().setBody("ok".getBytes(StandardCharsets.UTF_8));
                }
                if (request.getPath().startsWith("/missing")) {
                    return new NioHttpServer.HttpResponse().setStatus(404, "Not Found"); // no body, like the UPnP adapter
                }
                if (request.getPath().startsWith("/big")) {
                    return server.createFileResponse(bigFile, request);
                }
                return server.createFileResponse(file, request);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        server.registerWebSocketHandler(new EchoHandler());
        Thread thread = new Thread(server, "nio-test-server");
        thread.setDaemon(true);
        thread.start();
        awaitListening();
    }

    @After
    public void stopServer() {
        server.stop();
    }

    @Test
    public void get_returnsWholeFile() throws Exception {
        Response r = request("GET /track HTTP/1.1\r\nHost: test\r\n\r\n");
        assertEquals(200, r.status);
        assertEquals("bytes", r.header("accept-ranges"));
        assertArrayEquals(content, r.body);
    }

    @Test
    public void range_returnsSlice() throws Exception {
        Response r = request(get("bytes=10-19"));
        assertEquals(206, r.status);
        assertEquals("bytes 10-19/1000", r.header("content-range"));
        assertArrayEquals(Arrays.copyOfRange(content, 10, 20), r.body);
    }

    @Test
    public void suffixRange_returnsLastBytes() throws Exception {
        Response r = request(get("bytes=-5"));
        assertEquals(206, r.status);
        assertEquals("bytes 995-999/1000", r.header("content-range"));
        assertArrayEquals(Arrays.copyOfRange(content, 995, 1000), r.body);
    }

    @Test
    public void openEndedRange_returnsTail() throws Exception {
        Response r = request(get("bytes=900-"));
        assertEquals(206, r.status);
        assertArrayEquals(Arrays.copyOfRange(content, 900, 1000), r.body);
    }

    @Test
    public void rangeEndPastEof_isClampedToLastByte() throws Exception {
        Response r = request(get("bytes=990-5000"));
        assertEquals(206, r.status);
        assertEquals("bytes 990-999/1000", r.header("content-range"));
        assertArrayEquals(Arrays.copyOfRange(content, 990, 1000), r.body);
    }

    @Test
    public void rangeStartPastEof_returns416() throws Exception {
        Response r = requestHeadersOnly(get("bytes=1000-"));
        assertEquals(416, r.status);
        assertEquals("bytes */1000", r.header("content-range"));
    }

    @Test
    public void rangeStartPastEof_416EndsCleanly_andConnectionIsReusable() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, get("bytes=1000-"), true);
            assertEquals(416, r.status);
            assertEquals("0", r.header("content-length"));
            assertEquals(206, exchange(socket, get("bytes=0-3"), true).status);
        }
    }

    @Test
    public void bodylessResponse_hasZeroContentLength_andConnectionIsReusable() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, "GET /missing HTTP/1.1\r\nHost: test\r\n\r\n", true);
            assertEquals(404, r.status);
            assertEquals("0", r.header("content-length"));
            assertEquals(206, exchange(socket, get("bytes=0-3"), true).status);
        }
    }

    @Test
    public void malformedRange_isIgnored() throws Exception {
        Response r = request(get("bytes=abc"));
        assertEquals(200, r.status);
        assertNull(r.header("content-range"));
        assertArrayEquals(content, r.body);
    }

    @Test
    public void multipleRanges_areServedAsWholeFile() throws Exception {
        Response r = request(get("bytes=0-1,5-9"));
        assertEquals(200, r.status);
        assertNull(r.header("content-range"));
        assertArrayEquals(content, r.body);
    }

    @Test
    public void ifRangeMismatch_returnsWholeFile() throws Exception {
        Response r = request("GET /track HTTP/1.1\r\nHost: test\r\nRange: bytes=10-19\r\nIf-Range: \"stale\"\r\n\r\n");
        assertEquals(200, r.status);
        assertArrayEquals(content, r.body);
    }

    @Test
    public void head_returnsLengthWithoutBody_andConnectionStaysUsable() throws Exception {
        try (Socket socket = connect()) {
            Response head = exchange(socket, "HEAD /track HTTP/1.1\r\nHost: test\r\n\r\n", false);
            assertEquals(200, head.status);
            assertEquals("1000", head.header("content-length"));
            // A body after HEAD would corrupt this second response on the same connection
            Response get = exchange(socket, get("bytes=0-3"), true);
            assertEquals(206, get.status);
            assertArrayEquals(Arrays.copyOfRange(content, 0, 4), get.body);
        }
    }

    @Test
    public void keepAlive_servesTwoRequestsOnOneConnection() throws Exception {
        try (Socket socket = connect()) {
            assertArrayEquals(Arrays.copyOfRange(content, 0, 10), exchange(socket, get("bytes=0-9"), true).body);
            assertArrayEquals(Arrays.copyOfRange(content, 10, 20), exchange(socket, get("bytes=10-19"), true).body);
        }
    }

    @Test
    public void webSocket_handshakeAndEcho() throws Exception {
        try (Socket socket = connect()) {
            // RFC 6455 section 1.3 sample key and accept value
            Response r = exchange(socket, "GET /ws HTTP/1.1\r\nHost: test\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n", false);
            assertEquals(101, r.status);
            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", r.header("sec-websocket-accept"));

            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] mask = {1, 2, 3, 4};
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81); // FIN + text
            frame.write(0x80 | payload.length); // masked, as every client frame must be
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i % 4]);
            OutputStream out = socket.getOutputStream();
            out.write(frame.toByteArray());
            out.flush();

            InputStream in = socket.getInputStream();
            assertEquals(0x81, in.read());
            int length = in.read();
            assertEquals(payload.length, length); // server frames are unmasked
            assertArrayEquals(payload, in.readNBytes(length));
        }
    }

    @Test
    public void post_bodyArrivingAfterHeaders_reachesHandlerComplete() throws Exception {
        // UPnP SOAP actions and GENA NOTIFY: many clients send the body in a later segment
        String body = "<s:Envelope><s:Body>Browse</s:Body></s:Envelope>";
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write(("POST /ctl HTTP/1.1\r\nHost: test\r\nContent-Length: " + body.length() + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            Thread.sleep(200);
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals(200, exchange(socket, "", true).status);
        }
        assertEquals(body, lastPostBody.get());
    }

    @Test
    public void post_bodyInSamePacket_isCutToContentLength() throws Exception {
        try (Socket socket = connect()) {
            // Bytes past Content-Length (e.g. a pipelined next request) must not leak into the body
            assertEquals(200, exchange(socket, "POST /ctl HTTP/1.1\r\nHost: test\r\nContent-Length: 5\r\n\r\nhelloEXTRA", true).status);
        }
        assertEquals("hello", lastPostBody.get());
    }

    @Test
    public void midStreamDisconnect_doesNotReturnRequestToPoolTwice() throws Exception {
        try (Socket socket = connect()) {
            socket.setReceiveBufferSize(4096);
            OutputStream out = socket.getOutputStream();
            out.write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            socket.getInputStream().read(new byte[1024]);
            socket.setSoLinger(true, 0); // reset, as a renderer abandons a stream when seeking
        }
        Thread.sleep(1500);
        assertEquals(1, maxCopiesOfOneRequestInPool());
    }

    // --- helpers ---

    private int maxCopiesOfOneRequestInPool() throws Exception {
        java.lang.reflect.Field poolField = NioHttpServer.class.getDeclaredField("requestPool");
        poolField.setAccessible(true);
        Object pool = poolField.get(server);
        java.lang.reflect.Field queueField = pool.getClass().getDeclaredField("pool");
        queueField.setAccessible(true);
        List<Object> items = new ArrayList<>((Queue<?>) queueField.get(pool));
        IdentityHashMap<Object, Integer> counts = new IdentityHashMap<>();
        for (Object item : items) counts.merge(item, 1, Integer::sum);
        return counts.isEmpty() ? 0 : Collections.max(counts.values());
    }

    private static String get(String range) {
        return "GET /track HTTP/1.1\r\nHost: test\r\nRange: " + range + "\r\n\r\n";
    }

    private Response request(String raw) throws Exception {
        try (Socket socket = connect()) {
            return exchange(socket, raw, true);
        }
    }

    private Response requestHeadersOnly(String raw) throws Exception {
        try (Socket socket = connect()) {
            return exchange(socket, raw, false);
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        return socket;
    }

    private void awaitListening() throws Exception {
        for (int i = 0; i < 100; i++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException notYet) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("server did not start on port " + port);
    }

    private static Response exchange(Socket socket, String raw, boolean readBody) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(raw.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        InputStream in = socket.getInputStream();

        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) throw new IOException("connection closed before headers ended");
            head.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");
        Response response = new Response();
        response.status = Integer.parseInt(lines[0].split(" ")[1]);
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                response.headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
            }
        }
        if (readBody) {
            int length = Integer.parseInt(response.header("content-length"));
            response.body = in.readNBytes(length);
            assertEquals("short body", length, response.body.length);
        }
        return response;
    }

    private static final class Response {
        int status;
        final Map<String, String> headers = new LinkedHashMap<>();
        byte[] body = new byte[0];

        String header(String name) {
            return headers.get(name);
        }
    }

    private static final class EchoHandler implements WebSocket.Handler {
        @Override public String getNamespace() { return "/ws"; }
        @Override public void onOpen(WebSocket.Connection connection) { }
        @Override public void onMessage(WebSocket.Connection connection, String message) { connection.send(message); }
        @Override public void onMessage(WebSocket.Connection connection, byte[] message) { connection.send(message); }
        @Override public void onClose(WebSocket.Connection connection, int code, String reason) { }
        @Override public void onError(WebSocket.Connection connection, Exception ex) { }
    }
}
