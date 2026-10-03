package apincer.music.core.http;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

/** Request/frame buffer with a hard size limit; also scans for the end of the HTTP headers in place. */
final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
    private final int maxSize;

    public BoundedByteArrayOutputStream(int initialSize, int maxSize) {
        super(initialSize);
        this.maxSize = maxSize;
    }

    /** Position just past the first CRLFCRLF at or after {@code from}, or -1; scans in place. */
    synchronized int indexOfHeaderEnd(int from) {
        for (int i = Math.max(0, from); i + 3 < count; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    @Override
    public synchronized void write(@NonNull byte[] b, int off, int len) {
        if (count + len > maxSize) {
            throw new RuntimeException("Request size exceeds limit: " + maxSize);
        }
        super.write(b, off, len);
    }

    @Override
    public synchronized void write(int b) {
        if (count + 1 > maxSize) {
            throw new RuntimeException("Request size exceeds limit: " + maxSize);
        }
        super.write(b);
    }
}
