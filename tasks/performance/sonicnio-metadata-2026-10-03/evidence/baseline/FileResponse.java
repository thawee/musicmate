package apincer.music.core.http;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams a file or range with {@code FileChannel.transferTo()} and a bounded per-turn allowance.
 * Handles HEAD, ETag/If-None-Match (304), If-Range and RFC 7233 ranges (invalid ranges are ignored).
 * Each open file holds a {@link StreamSlots} slot until {@link #close()}.
 */
final class FileResponse extends NioHttpServer.HttpResponse implements StreamBody {
    private final StreamSlots slots;
    private final boolean media;
    private final FileChannel fileChannel;
    private final long fileSize;
    private long bytesSent = 0;
    private final long rangeStart;
    private final long rangeEnd;
    private final long rangeLength;
    private final AtomicBoolean hasClosed = new AtomicBoolean(false);

    private static final long CHUNK_SIZE = 262144; // 256KB chunks for smooth streaming

    FileResponse(File file, NioHttpServer.HttpRequest request, StreamSlots slots) throws IOException {
        this(file, request, slots, -1);
    }

    /**
     * @param startOffset when 0 or more, send the file from this byte as 200 (no Content-Range)
     *                    and ignore any Range header: a DLNA time seek resolved to a position
     */
    FileResponse(File file, NioHttpServer.HttpRequest request, StreamSlots slots, long startOffset) throws IOException {
        this(file, request, slots, startOffset, true);
    }

    FileResponse(File file, NioHttpServer.HttpRequest request, StreamSlots slots, long startOffset, boolean media) throws IOException {
        super();
        this.media = media;
        this.slots = slots;

        long fileLen = file.length();

        // HEAD request → no streaming, no stream count
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            this.fileChannel = null;
            this.fileSize = fileLen;
            this.rangeStart = 0;
            this.rangeEnd = 0;
            this.rangeLength = 0;

            setStatus(NioHttpServer.HTTP_OK, "OK");
            addHeader("Content-Length", String.valueOf(fileLen));
            addHeader("Accept-Ranges", "bytes");
            return;
        }

        this.addHeader("Content-Type", FileContentTypes.readContentForMime(file));

        String etag = generateETag(file);
        this.addHeader("ETag", etag);
        this.addHeader("Last-Modified", formatHttpDate(file.lastModified()));

        long tempStart = 0;
        long tempEnd = fileLen - 1;

        String ifNoneMatch = request.getHeader("if-none-match", null);
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            setStatus(NioHttpServer.HTTP_NOT_MODIFIED, "Not Modified");
            this.fileChannel = null;
            this.fileSize = 0;
            this.rangeStart = 0;
            this.rangeEnd = 0;
            this.rangeLength = 0;
            return;
        }

        // Only a request that will send a body takes a slot (not HEAD or 304).
        if (!slots.acquire()) {
            throw new IOException("Service Unavailable - max concurrent streams reached");
        }
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (IOException e) {
            slots.release();
            throw e;
        }
        this.fileChannel = raf.getChannel();
        this.fileSize = fileChannel.size();

        try {
            String rangeHeader = request.getHeader("range", "");
            boolean rangeValid = true;
            // 206 only for a range that was parsed and applied; RFC 7233 says an invalid or
            // unsupported (e.g. multi-range) Range header is ignored and the file is sent as 200
            boolean rangeApplied = false;

            if (startOffset >= 0) {
                tempStart = Math.min(startOffset, Math.max(0, fileSize - 1));
            } else if (rangeHeader.startsWith("bytes=")) {
                String ifRange = request.getHeader("if-range", null);
                if (ifRange != null) {
                    rangeValid = ifRange.equals(etag);
                }

                if (rangeValid && parseRangeHeader(rangeHeader)) {

                    if (parsedStart >= fileSize) {
                        setStatus(NioHttpServer.HTTP_RANGE_NOT_SATISFIABLE, "Range Not Satisfiable");
                        addHeader("Content-Range", "bytes */" + fileSize);

                        rangeStart = 0;
                        rangeEnd = 0;
                        rangeLength = 0;

                        close();
                        return;
                    }

                    tempStart = parsedStart;
                    tempEnd = Math.min(parsedEnd, fileSize - 1);
                    rangeApplied = true;
                }
            }

            this.rangeStart = tempStart;
            this.rangeEnd = tempEnd;
            this.rangeLength = this.rangeEnd - this.rangeStart + 1;

            if (!rangeApplied) {
                setStatus(NioHttpServer.HTTP_OK, "OK");
            } else {
                setStatus(NioHttpServer.HTTP_PARTIAL_CONTENT, "Partial Content");
                addHeader("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize);
            }

            addHeader("Content-Length", String.valueOf(rangeLength));
            addHeader("Accept-Ranges", "bytes");
            addHeader("Connection", "keep-alive"); // DLNA stability
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    private long parsedStart, parsedEnd;

    private boolean parseRangeHeader(String rangeHeader) {
        try {
            String rangeValue = rangeHeader.substring(6);
            parsedStart = -1;
            parsedEnd = -1;

            if (rangeValue.startsWith("-")) {
                long lastBytes = Long.parseLong(rangeValue.substring(1));
                parsedStart = Math.max(0, this.fileSize - lastBytes);
                parsedEnd = this.fileSize - 1;
            } else {
                String[] ranges = rangeValue.split("-");
                parsedStart = Long.parseLong(ranges[0]);
                parsedEnd = (ranges.length > 1 && !ranges[1].isEmpty())
                        ? Long.parseLong(ranges[1])
                        : this.fileSize - 1;
            }

            // Syntax is valid even when the requested start is past EOF; the caller
            // must return 416 for that unsatisfiable range instead of serving the whole file.
            return parsedStart >= 0 && (parsedStart >= fileSize ||
                    (parsedEnd >= 0 && parsedStart <= parsedEnd));

        } catch (Exception e) {
            return false;
        }
    }

    private String generateETag(File file) {
        String value = file.getAbsolutePath() + "-" + file.length() + "-" + file.lastModified();

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return "\"" + bytesToHex(hash).substring(0, 16) + "-" +
                    Long.toHexString(file.length()) + "\"";
        } catch (NoSuchAlgorithmException e) {
            int hash = value.hashCode();
            return "\"" + Integer.toHexString(hash) + "-" +
                    Long.toHexString(file.length()) + "\"";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String formatHttpDate(long timestamp) {
        SimpleDateFormat df = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.format(new Date(timestamp));
    }

    @Override
    public long write(SocketChannel channel) throws IOException {
        return write(channel, (int) CHUNK_SIZE);
    }

    long write(SocketChannel channel, int writeBudget) throws IOException {
        if (headerBuffer == null) buildHeaders();
        long sent = 0;

        if (!headersSent) {
            sent += channel.write(headerBuffer);
            if (headerBuffer.hasRemaining()) return sent;
            headersSent = true;
        }

        if (statusCode != NioHttpServer.HTTP_OK && statusCode != NioHttpServer.HTTP_PARTIAL_CONTENT) return sent;

        if (fileChannel != null && fileChannel.isOpen() && bytesSent < rangeLength) {
            long position = rangeStart + bytesSent;
            long remaining = rangeLength - bytesSent;

            int maxTries = 1; // One bounded transfer; the server lowers the allowance under contention.

            while (remaining > 0 && maxTries-- > 0) {
                long chunk = Math.min(remaining, writeBudget);

                long written = fileChannel.transferTo(position, chunk, channel);

                if (written <= 0) {
                    // IMPORTANT: socket not ready → wait for next OP_WRITE
                    break;
                }

                position += written;
                remaining -= written;
                bytesSent += written;
                sent += written;
            }
        }
        return sent;
    }

    @Override
    public boolean isFullySent() {
        if (statusCode != NioHttpServer.HTTP_OK && statusCode != NioHttpServer.HTTP_PARTIAL_CONTENT) {
            return headersSent;
        }
        return headersSent && (fileChannel == null || bytesSent >= rangeLength);
    }

    @Override
    public boolean isMedia() { return media; }

    @Override
    long bodyBytesSent() { return bytesSent; }

    @Override
    public void close() throws IOException {
        if (!hasClosed.compareAndSet(false, true)) return;

        if (fileChannel != null) {
            if (fileChannel.isOpen()) {
                fileChannel.close();
            }
            slots.release();
        }
    }
}
