package apincer.music.core.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private File trackFile;
    private File bigFile;
    private final AtomicReference<String> lastPostBody = new AtomicReference<>();
    private final List<Integer> wsSequence = Collections.synchronizedList(new ArrayList<>());
    private final List<String> wsEvents = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void startServer() throws Exception {
        content = new byte[1000];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 251);
        trackFile = temporary.newFile("track.flac");
        Files.write(trackFile.toPath(), content);
        bigFile = temporary.newFile("big.flac");
        Files.write(bigFile.toPath(), new byte[8 * 1024 * 1024]);

        // The probed port is free only until the probe closes; another socket can take it before the
        // server binds (common after the soak test's churn), so try a fresh port
        for (int attempt = 1; ; attempt++) {
            try (ServerSocket probe = new ServerSocket(0)) {
                port = probe.getLocalPort();
            }
            launchServer();
            if (awaitListening()) return;
            server.stop();
            if (attempt == 3) throw new IllegalStateException("server did not start on port " + port);
        }
    }

    private void launchServer() {
        server = new NioHttpServer(port);
        server.registerHttpHandler(request -> {
            try {
                if ("POST".equals(request.getMethod())) {
                    lastPostBody.set(new String(request.getBody(), StandardCharsets.UTF_8));
                    return new NioHttpServer.HttpResponse().setBody("ok".getBytes(StandardCharsets.UTF_8));
                }
                if (request.getPath().startsWith("/slow")) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                    return new NioHttpServer.HttpResponse().setBody("late".getBytes(StandardCharsets.UTF_8));
                }
                if (request.getPath().startsWith("/missing")) {
                    return new NioHttpServer.HttpResponse().setStatus(404, "Not Found"); // no body, like the UPnP adapter
                }
                if (request.getPath().startsWith("/big")) {
                    return server.createFileResponse(bigFile, request);
                }
                return server.createFileResponse(trackFile, request);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        server.registerWebSocketHandler(new EchoHandler());
        Thread thread = new Thread(server, "nio-test-server");
        thread.setDaemon(true);
        thread.start();
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
    public void midStreamDisconnect_releasesStreamAndConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.setReceiveBufferSize(4096);
            OutputStream out = socket.getOutputStream();
            out.write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            socket.getInputStream().read(new byte[1024]);
            socket.setSoLinger(true, 0); // reset, as a renderer abandons a stream when seeking
        }
        awaitCounter("activeStreams", 0);
        awaitCounter("activeConnections", 0);
        assertEquals(0, counter("activeStreams"));
        assertEquals(0, counter("activeConnections"));
    }

    @Test
    public void webSocket_serverClose_sendsCloseFrameAndClosesSocket() throws Exception {
        try (Socket socket = connect()) {
            exchange(socket, "GET /ws HTTP/1.1\r\nHost: test\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n", false);
            OutputStream out = socket.getOutputStream();
            out.write(new byte[]{(byte) 0x81, (byte) 0x85, 0, 0, 0, 0, 'c', 'l', 'o', 's', 'e'}); // masked "close"
            out.flush();
            InputStream in = socket.getInputStream();
            assertEquals(0x88, in.read()); // CLOSE frame
            int length = in.read();
            byte[] payload = in.readNBytes(length);
            assertEquals(WebSocket.CLOSE_NORMAL, ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF));
            assertEquals(-1, in.read()); // then the server closes the socket
        }
    }

    @Test
    public void streamLimit_newStreamEvictsTheIdleOne() throws Exception {
        server.setMaxConcurrentStreams(1);
        try (Socket first = connect(); Socket second = connect()) {
            first.setReceiveBufferSize(4096);
            first.getOutputStream().write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            first.getOutputStream().flush();
            first.getInputStream().read(new byte[1024]); // stream is open and stalls on our full buffer
            Thread.sleep(300);

            Response r = exchange(second, get("bytes=0-3"), true);
            assertEquals(206, r.status); // admitted, not 503

            // the idle stream is closed by the server: end of stream, or a reset (timing-dependent)
            InputStream in = first.getInputStream();
            byte[] drain = new byte[65536];
            long deadline = System.currentTimeMillis() + 5000;
            int n;
            try {
                do {
                    n = in.read(drain);
                } while (n >= 0 && System.currentTimeMillis() < deadline);
            } catch (java.net.SocketException reset) {
                n = -1;
            }
            assertEquals(-1, n);
        }
        assertEquals(1, server.getStats().evictions);
    }

    @Test
    public void stats_countRequestsBytesAndConnections() throws Exception {
        NioHttpServer.Stats before = server.getStats();
        try (Socket socket = connect()) {
            assertEquals(200, exchange(socket, "GET /track HTTP/1.1\r\nHost: test\r\n\r\n", true).status);
            assertEquals(206, exchange(socket, get("bytes=0-99"), true).status);
            NioHttpServer.Stats during = server.getStats();
            assertEquals(1, during.connections);
            assertEquals(2, during.requests - before.requests);
            // both bodies (1000 + 100 bytes) plus their headers
            assertTrue("bytesSent " + during.bytesSent, during.bytesSent - before.bytesSent > 1100);
        }
        awaitCounter("activeConnections", 0);
        assertEquals(0, server.getStats().connections);
        assertEquals(0, server.getStats().streams);
    }

    @Test
    public void webSocket_messagesFromOneConnection_areHandledInOrder() throws Exception {
        try (Socket socket = connect()) {
            exchange(socket, "GET /ws HTTP/1.1\r\nHost: test\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n", false);
            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            for (int i = 0; i < 50; i++) {
                byte[] payload = ("seq:" + i).getBytes(StandardCharsets.UTF_8);
                frames.write(0x81);
                frames.write(0x80 | payload.length);
                frames.write(new byte[4]); // zero mask
                frames.write(payload);
            }
            socket.getOutputStream().write(frames.toByteArray());
            socket.getOutputStream().flush();
            long deadline = System.currentTimeMillis() + 5000;
            while (wsSequence.size() < 50 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        }
        assertEquals(new ArrayList<>(expected50()), new ArrayList<>(wsSequence));
    }

    private static List<Integer> expected50() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) list.add(i);
        return list;
    }

    @Test
    public void stopBeforeRun_keepsTheServerStopped() throws Exception {
        int otherPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            otherPort = probe.getLocalPort();
        }
        NioHttpServer early = new NioHttpServer(otherPort);
        early.stop(); // e.g. a restart racing the start of the server thread
        Thread thread = new Thread(early);
        thread.setDaemon(true);
        thread.start();
        thread.join(2000);
        try (Socket ignored = new Socket("127.0.0.1", otherPort)) {
            early.stop();
            throw new AssertionError("server started after stop()");
        } catch (IOException expected) {
            // nothing listening
        }
    }

    @Test
    public void stop_releasesOpenStreamsAndConnections() throws Exception {
        try (Socket socket = connect()) {
            socket.setReceiveBufferSize(4096);
            socket.getOutputStream().write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            socket.getInputStream().read(new byte[1024]); // a stream is open and stalled
            Thread.sleep(300);
            assertEquals(1, counter("activeStreams"));

            server.stop(); // the same cleanup runs when the selector is recreated after an error
            long deadline = System.currentTimeMillis() + 5000;
            while ((counter("activeStreams") != 0 || counter("activeConnections") != 0)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(0, counter("activeStreams"));
            assertEquals(0, counter("activeConnections"));
        }
    }

    private int counter(String name) throws Exception {
        java.lang.reflect.Field field = NioHttpServer.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicInteger) field.get(server)).get();
    }

    @Test
    public void webSocket_forceClose_closesSocketAndReleasesConnection() throws Exception {
        try (Socket socket = connect()) {
            upgrade(socket);
            sendMaskedText(socket, "kill");
            assertEquals(-1, socket.getInputStream().read());
        }
        awaitCounter("activeConnections", 0);
        assertEquals(0, counter("activeConnections"));
    }

    @Test
    public void stop_returnsPromptlyWhileAHandlerIsBusy() throws Exception {
        Socket socket = connect();
        socket.getOutputStream().write("GET /slow HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
        Thread.sleep(200); // the handler is now sleeping on a worker
        long start = System.currentTimeMillis();
        server.stop();
        long elapsed = System.currentTimeMillis() - start;
        socket.close();
        assertTrue("stop() blocked for " + elapsed + " ms", elapsed < 1000);
    }

    @Test
    public void imageMimeType_comesFromContentNotExtension() throws Exception {
        File png = temporary.newFile("cover.jpg"); // PNG bytes behind a .jpg name
        Files.write(png.toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D});
        assertEquals("image/png", FileContentTypes.readContentForMime(png));
    }

    @Test
    public void clientConnectionClose_isHonoured() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, "GET /track HTTP/1.1\r\nHost: test\r\nRange: bytes=0-3\r\nConnection: close\r\n\r\n", true);
            assertEquals("close", r.header("connection"));
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    public void chunkedRequestBody_isRejected() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, "POST /ctl HTTP/1.1\r\nHost: test\r\nTransfer-Encoding: chunked\r\n\r\n"
                    + "5\r\nhello\r\n0\r\n\r\n", true);
            assertEquals(501, r.status);
        }
        assertNull(lastPostBody.get());
    }

    @Test
    public void webSocket_oversizedFrame_isNotDeliveredAsAMessage() throws Exception {
        try (Socket socket = connect()) {
            upgrade(socket);
            // Header claiming a 2 MB payload (over the 1 MB limit); the frame is rejected
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81);
            frame.write(0x80 | 127);
            frame.write(new byte[]{0, 0, 0, 0, 0, 0x20, 0, 0});
            frame.write(new byte[4]);
            socket.getOutputStream().write(frame.toByteArray());
            socket.getOutputStream().flush();
            // An echoed empty text frame (0x81) would mean a partial message reached the handler
            assertEquals(0x88, socket.getInputStream().read());
        }
    }

    @Test
    public void webSocket_protocolErrorAfterAQueuedReply_stillClosesTheConnection() throws Exception {
        // Found by NioHttpServerFuzzTest: a PING queues a PONG, then a malformed frame arrives
        try (Socket socket = connect()) {
            upgrade(socket);
            socket.getOutputStream().write(new byte[]{
                    (byte) 0x89, (byte) 0x80, 0, 0, 0, 0,   // masked, empty PING
                    (byte) 0x81, 0x01, 'x'});              // unmasked text frame: protocol error
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[64];
            int n;
            do {
                n = in.read(buffer); // PONG, then CLOSE, then end of stream
            } while (n >= 0);
        }
        awaitCounter("activeConnections", 0);
        assertEquals(0, counter("activeConnections"));
    }

    @Test
    public void notModified_doesNotEvictAnActiveStream() throws Exception {
        String etag;
        try (Socket probe = connect()) {
            etag = exchange(probe, "GET /big HTTP/1.1\r\nHost: test\r\nRange: bytes=0-0\r\n\r\n", true).header("etag");
        }
        server.setMaxConcurrentStreams(1);
        try (Socket stream = connect()) {
            stream.setReceiveBufferSize(4096);
            stream.getOutputStream().write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            stream.getOutputStream().flush();
            stream.getInputStream().read(new byte[1024]); // holds the only stream slot
            Thread.sleep(300);
            try (Socket revalidate = connect()) {
                Response r = exchange(revalidate, "GET /big HTTP/1.1\r\nHost: test\r\nIf-None-Match: " + etag + "\r\n\r\n", false);
                assertEquals(304, r.status);
            }
            Thread.sleep(500);
            // still one open stream: the 304 must not have evicted it (buffered bytes would hide a close)
            assertEquals(1, counter("activeStreams"));
        }
    }

    @Test
    public void webSocket_framePipelinedWithUpgrade_isHandledAfterOnOpen() throws Exception {
        try (Socket socket = connect()) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            raw.write(("GET /ws HTTP/1.1\r\nHost: test\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            byte[] payload = "first".getBytes(StandardCharsets.UTF_8);
            raw.write(0x81);
            raw.write(0x80 | payload.length);
            raw.write(new byte[4]);
            raw.write(payload);
            socket.getOutputStream().write(raw.toByteArray()); // upgrade and first message in one segment
            socket.getOutputStream().flush();
            long deadline = System.currentTimeMillis() + 5000;
            while (wsEvents.size() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        }
        assertEquals(java.util.Arrays.asList("open", "message"), new ArrayList<>(wsEvents));
    }

    @Test
    public void expectContinue_gets100BeforeTheBody() throws Exception {
        String body = "<s:Envelope>expect</s:Envelope>";
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write(("POST /ctl HTTP/1.1\r\nHost: test\r\nExpect: 100-continue\r\nContent-Length: "
                    + body.length() + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            socket.setSoTimeout(1000); // a client waits about 1 s for 100 before sending anyway
            Response interim = readResponseHead(socket.getInputStream());
            assertEquals(100, interim.status);
            socket.setSoTimeout(5000);
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals(200, exchange(socket, "", true).status);
        }
        assertEquals(body, lastPostBody.get());
    }

    @Test
    public void pipelinedRequests_areAnsweredInOrder() throws Exception {
        try (Socket socket = connect()) {
            socket.getOutputStream().write((get("bytes=0-3") + get("bytes=4-7")).getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            Response first = exchange(socket, "", true);
            Response second = exchange(socket, "", true);
            assertArrayEquals(Arrays.copyOfRange(content, 0, 4), first.body);
            assertArrayEquals(Arrays.copyOfRange(content, 4, 8), second.body);
        }
    }

    @Test
    public void http10WithoutKeepAlive_closesAfterTheResponse() throws Exception {
        try (Socket socket = connect()) {
            Response r = exchange(socket, "GET /track HTTP/1.0\r\nRange: bytes=0-3\r\n\r\n", true);
            assertEquals("close", r.header("connection"));
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    public void slowHeaders_areCutOffAtTheHeaderDeadline() throws Exception {
        server.setHeaderReadTimeout(500);
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            long start = System.currentTimeMillis();
            boolean closed = false;
            // trickle one byte every 150 ms; each byte would reset an idle timer, not this deadline
            for (byte b : "GET /track HTTP/1.1\r\nX-Slow: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.ISO_8859_1)) {
                try {
                    out.write(b);
                    out.flush();
                } catch (IOException e) {
                    closed = true;
                    break;
                }
                Thread.sleep(150);
            }
            if (!closed) {
                socket.setSoTimeout(3000);
                try {
                    closed = socket.getInputStream().read() == -1;
                } catch (IOException reset) {
                    closed = true;
                }
            }
            assertTrue("slow client was not cut off", closed);
            assertTrue("cut off too late", System.currentTimeMillis() - start < 6000);
            assertEquals(1, server.getStats().timeouts);
        }
    }

    @Test
    public void longStream_outlivesTheHeaderDeadline() throws Exception {
        // A renderer plays a track over minutes on one connection; the header deadline must not cut it
        server.setHeaderReadTimeout(500);
        try (Socket socket = connect()) {
            socket.setReceiveBufferSize(64 * 1024);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            readResponseHead(in);
            long received = 0;
            long start = System.currentTimeMillis();
            byte[] buffer = new byte[64 * 1024];
            // read at about 4 MB/s, so the 8 MB body takes about 2 s, well past the 500 ms deadline
            while (received < bigFile.length()) {
                int n = in.read(buffer);
                if (n < 0) break;
                received += n;
                long due = start + received / 4096; // 4096 bytes per ms
                long wait = due - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
            }
            assertTrue("test did not run past the deadline", System.currentTimeMillis() - start > 1000);
            assertEquals(bigFile.length(), received);
        }
    }

    @Test
    public void stalledReader_isClosedAfterTheIdleTimeout() throws Exception {
        server.setKeepAliveTimeout(1000);
        try (Socket socket = connect()) {
            socket.setReceiveBufferSize(4096);
            socket.getOutputStream().write("GET /big HTTP/1.1\r\nHost: test\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            // never read: the renderer is stalled; the stream's slot must be released
            awaitCounter("activeStreams", 1);
            long deadline = System.currentTimeMillis() + 6000;
            while (counter("activeStreams") != 0 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertEquals(0, counter("activeStreams"));
        }
    }

    private static Response readResponseHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) throw new IOException("closed before headers ended");
            head.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        Response r = new Response();
        r.status = Integer.parseInt(head.toString(StandardCharsets.ISO_8859_1.name()).split(" ")[1]);
        return r;
    }

    private void upgrade(Socket socket) throws IOException {
        exchange(socket, "GET /ws HTTP/1.1\r\nHost: test\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n", false);
    }

    private static void sendMaskedText(Socket socket, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);
        frame.write(0x80 | payload.length);
        frame.write(new byte[4]); // zero mask
        frame.write(payload);
        socket.getOutputStream().write(frame.toByteArray());
        socket.getOutputStream().flush();
    }

    private void awaitCounter(String name, int value) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (counter(name) != value && System.currentTimeMillis() < deadline) Thread.sleep(50);
    }

    // --- helpers ---


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

    private boolean awaitListening() throws Exception {
        for (int i = 0; i < 100; i++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return true;
            } catch (IOException notYet) {
                Thread.sleep(50);
            }
        }
        return false;
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

    private final class EchoHandler implements WebSocket.Handler {
        @Override public String getNamespace() { return "/ws"; }
        @Override public void onOpen(WebSocket.Connection connection) { wsEvents.add("open"); }
        @Override public void onMessage(WebSocket.Connection connection, String message) {
            if ("close".equals(message)) {
                connection.close(WebSocket.CLOSE_NORMAL, "bye");
            } else if ("first".equals(message)) {
                wsEvents.add("message");
            } else if ("kill".equals(message)) {
                connection.forceClose();
            } else if (message.startsWith("seq:")) {
                int n = Integer.parseInt(message.substring(4));
                if (n % 2 == 0) {
                    try { Thread.sleep(5); } catch (InterruptedException ignored) { }
                }
                wsSequence.add(n);
            } else {
                connection.send(message);
            }
        }
        @Override public void onMessage(WebSocket.Connection connection, byte[] message) { connection.send(message); }
        @Override public void onClose(WebSocket.Connection connection, int code, String reason) { }
        @Override public void onError(WebSocket.Connection connection, Exception ex) { }
    }
}
