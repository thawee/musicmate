package apincer.music.core.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A response whose body is made on another thread while it is sent (e.g. audio decoded to PCM),
 * with a known Content-Length. The producer fills a bounded queue; the selector thread sends what
 * is queued (ADR-036: only it touches the connection). When the queue is empty the selector parks
 * the connection (no OP_WRITE), and the producer wakes it through requestWrite, so a slow
 * producer never makes the selector spin. Producing more or fewer bytes than declared fails the
 * stream, which closes the connection, rather than sending a body of the wrong length.
 */
public final class StreamingResponse extends NioHttpServer.HttpResponse implements StreamBody {

    /** Receives produced bytes; blocks while the queue is full; throws once the response is closed. */
    public interface Sink {
        void write(byte[] data, int offset, int length) throws IOException;
    }

    /** Writes the whole body into the sink; runs on a producer thread. */
    public interface Producer {
        void produce(Sink sink) throws Exception;
    }

    private static final int CHUNK = 64 * 1024;

    private final ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(16);
    private final long contentLength;
    private final StreamSlots slots;
    private final AtomicBoolean parked = new AtomicBoolean();
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile Runnable wake;
    private volatile boolean producerDone;
    private volatile boolean producerFailed;
    private volatile boolean closed;
    private ByteBuffer pending; // selector thread only
    private Future<?> task;

    StreamingResponse(int status, String statusText, long contentLength, StreamSlots slots) {
        this.contentLength = contentLength;
        this.slots = slots;
        setStatus(status, statusText);
        addHeader("Content-Length", String.valueOf(contentLength));
    }

    void start(ExecutorService producers, Producer producer) {
        task = producers.submit(() -> {
            long[] produced = {0};
            try {
                producer.produce((data, offset, length) -> {
                    if (produced[0] + length > contentLength) {
                        throw new IOException("Producer exceeded the declared Content-Length");
                    }
                    for (int at = offset; at < offset + length; at += CHUNK) {
                        int n = Math.min(CHUNK, offset + length - at);
                        ByteBuffer chunk = ByteBuffer.allocate(n);
                        chunk.put(data, at, n).flip();
                        try {
                            while (!queue.offer(chunk, 200, TimeUnit.MILLISECONDS)) {
                                if (closed) throw new IOException("Response closed");
                            }
                        } catch (InterruptedException interrupted) { // close() cancels the producer
                            Thread.currentThread().interrupt();
                            throw new IOException("Response closed", interrupted);
                        }
                        if (closed) throw new IOException("Response closed");
                        wakeIfParked();
                    }
                    produced[0] += length;
                });
                if (produced[0] != contentLength) {
                    throw new IOException("Producer made " + produced[0] + " of " + contentLength + " bytes");
                }
                producerDone = true;
            } catch (Throwable t) {
                producerFailed = true;
            } finally {
                wakeIfParked();
            }
        });
    }

    private void wakeIfParked() {
        if (parked.compareAndSet(true, false)) {
            Runnable w = wake;
            if (w != null) w.run();
        }
    }

    @Override
    public long write(SocketChannel channel) throws IOException {
        if (headerBuffer == null) buildHeaders();
        long written = 0;
        if (!headersSent) {
            written += channel.write(headerBuffer);
            if (headerBuffer.hasRemaining()) return written;
            headersSent = true;
        }
        while (true) {
            if (pending == null) pending = queue.poll();
            if (pending == null) {
                if (producerFailed) throw new IOException("Stream producer failed");
                return written;
            }
            written += channel.write(pending);
            if (pending.hasRemaining()) return written; // socket buffer full: OP_WRITE stays on
            pending = null;
        }
    }

    @Override
    public boolean isFullySent() {
        return headersSent && pending == null && queue.isEmpty() && producerDone;
    }

    /**
     * Selector thread, after a write that left the body unfinished: true when the queue is empty
     * and the connection should stop asking for OP_WRITE until {@code wakeAction} runs.
     */
    boolean park(Runnable wakeAction) {
        if (!headersSent || pending != null) return false; // the socket was full, not the queue
        wake = wakeAction;
        parked.set(true);
        // Re-check after parking: the producer may have queued or finished in between
        if (!queue.isEmpty() || producerDone || producerFailed) {
            parked.set(false);
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel(true);
        queue.clear();
        releaseSlot();
    }

    private void releaseSlot() {
        if (released.compareAndSet(false, true)) slots.release();
    }
}
