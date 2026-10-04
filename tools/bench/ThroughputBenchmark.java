package apincer.music.core.http;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Standalone host-loopback experiment, not an Android/Wi-Fi or Netty benchmark. */
public final class ThroughputBenchmark {
    private static final int SIZE = 16 * 1024 * 1024;
    private static final byte[] CONTENT = new byte[SIZE];
    private static byte[] expectedHash;

    public static void main(String[] args) throws Exception {
        String label = args[0];
        int soloBudget = Integer.parseInt(args[1]); // 0 preserves the baseline server defaults
        boolean batch = Boolean.parseBoolean(args[2]);
        for (int i = 0; i < SIZE; i++) CONTENT[i] = (byte) (i % 251);
        expectedHash = MessageDigest.getInstance("SHA-256").digest(CONTENT);
        Path file = Files.createTempFile("sonicnio-throughput-", ".flac");
        try {
            Files.write(file, CONTENT);
            for (String mode : List.of("file-single", "file-four", "pcm-four")) {
                if (args.length > 3 && !mode.equals(args[3])) continue;
                run(file.toFile(), mode, label, soloBudget, batch, 0); // unreported warmup
                for (int iteration = 1; iteration <= 3; iteration++) {
                    run(file.toFile(), mode, label, soloBudget, batch, iteration);
                }
            }
        } finally { Files.deleteIfExists(file); }
    }

    private static void run(File file, String mode, String label, int soloBudget,
                            boolean batch, int iteration) throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) { port = probe.getLocalPort(); }
        NioHttpServer server = new NioHttpServer(port);
        server.setMaxConcurrentStreams(16);
        server.setMaxThread(4);
        if (soloBudget > 0) {
            server.getClass().getMethod("setFileWriteBudgets", int.class, int.class)
                    .invoke(server, 256 * 1024, soloBudget);
            server.getClass().getMethod("setBatchGeneratedAudio", boolean.class).invoke(server, batch);
        }
        server.registerHttpHandler(request -> {
            try {
                if (request.getPath().startsWith("/pcm")) {
                    return server.createStreamingResponse(200, "OK", SIZE, sink -> {
                        for (int at = 0; at < SIZE; at += 4096) sink.write(CONTENT, at, 4096);
                    });
                }
                return server.createFileResponse(file, request);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
        Thread thread = new Thread(server, "throughput-server");
        thread.setDaemon(true); thread.start();
        for (int attempt = 0; ; attempt++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) { break; }
            catch (ConnectException notReady) {
                if (attempt == 100) throw notReady;
                Thread.sleep(10);
            }
        }
        int clients = mode.equals("file-single") ? 1 : 4;
        ExecutorService pool = Executors.newFixedThreadPool(clients + 1);
        AtomicLong bytes = new AtomicLong();
        List<Long> seeks = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> jobs = new ArrayList<>();
        long started = System.nanoTime(), deadline = started + TimeUnit.SECONDS.toNanos(2);
        try {
            for (int c = 0; c < clients; c++) {
                jobs.add(pool.submit(() -> {
                    try (Socket socket = connect(port)) {
                        do { bytes.addAndGet(fetch(socket, mode.startsWith("pcm") ? "/pcm" : "/file", false, null)); }
                        while (System.nanoTime() < deadline);
                    } catch (Exception error) { throw new RuntimeException(error); }
                }));
            }
            // Keep single-stream runs genuinely single-connection; contention runs add seek traffic.
            if (clients > 1) jobs.add(pool.submit(() -> {
                try (Socket socket = connect(port)) {
                    while (System.nanoTime() < deadline) {
                        fetch(socket, "/file", true, seeks);
                        Thread.sleep(10);
                    }
                } catch (Exception error) { throw new RuntimeException(error); }
            }));
            for (Future<?> job : jobs) job.get(30, TimeUnit.SECONDS);
            double seconds = (System.nanoTime() - started) / 1e9;
            long drain = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (server.getStats().connections != 0 && System.nanoTime() < drain) Thread.sleep(10);
            if (server.getStats().connections != 0 || server.getStats().streams != 0
                    || server.getResourceUsage().bufferedAudioBytes() != 0) throw new AssertionError("resources did not drain");
            if (iteration > 0) {
                Collections.sort(seeks);
                var calls = NioHttpServer.class.getDeclaredField("writeCalls"); calls.setAccessible(true);
                System.out.printf(Locale.ROOT,
                        "{\"label\":\"%s\",\"mode\":\"%s\",\"iteration\":%d,\"solo_budget\":%d,\"batch\":%s,\"bytes\":%d,\"seconds\":%.6f,\"MiB_s\":%.3f,\"seek_samples\":%d,\"seek_p95_ms\":%.3f,\"write_calls\":%d,\"audio_budget_peak\":%d,\"errors\":0}%n",
                        label, mode, iteration, soloBudget, batch, bytes.get(), seconds,
                        bytes.get() / 1048576.0 / seconds, seeks.size(),
                        seeks.isEmpty() ? 0 : seeks.get((int) (seeks.size() * .95)) / 1e6,
                        ((AtomicInteger) calls.get(server)).get(), server.getResourceUsage().peakBufferedAudioBytes());
            }
        } catch (Exception | AssertionError failure) {
            System.err.println("Response diagnostics: " + server.getDiagnostics().responses());
            System.err.println("Close diagnostics: " + server.getDiagnostics().closes());
            throw failure;
        } finally {
            pool.shutdownNow(); server.stop(); thread.join(5000);
            if (thread.isAlive()) throw new AssertionError("server did not stop");
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(10000); return socket;
    }

    private static int fetch(Socket socket, String path, boolean range, List<Long> seeks) throws Exception {
        String request = "GET " + path + " HTTP/1.1\r\nHost: bench\r\n"
                + (range ? "Range: bytes=100-65635\r\n" : "") + "\r\n";
        long started = System.nanoTime();
        socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read(); if (b < 0) throw new EOFException("missing headers");
            if (header.size() == 0 && seeks != null) seeks.add(System.nanoTime() - started);
            header.write(b); matched = b == "\r\n\r\n".charAt(matched) ? matched + 1 : b == '\r' ? 1 : 0;
            if (header.size() > 8192) throw new IOException("oversized headers");
        }
        String text = header.toString(StandardCharsets.ISO_8859_1);
        if (!text.startsWith(range ? "HTTP/1.1 206 " : "HTTP/1.1 200 ")) throw new IOException(text);
        int length = -1;
        for (String line : text.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        }
        if (length != (range ? 65536 : SIZE)) throw new IOException("wrong length " + length);
        byte[] body = in.readNBytes(length);
        if (body.length != length) throw new EOFException("short body");
        if (range) {
            if (!Arrays.equals(body, Arrays.copyOfRange(CONTENT, 100, 65636))) throw new AssertionError("range mismatch");
        } else if (!Arrays.equals(expectedHash, MessageDigest.getInstance("SHA-256").digest(body))) {
            throw new AssertionError("payload mismatch");
        }
        return length;
    }
}
