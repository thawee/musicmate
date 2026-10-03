package apincer.music.core.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A response whose body is made on another thread while it is sent (e.g. audio decoded to PCM),
 * with a known Content-Length. The producer fills a bounded queue; the selector thread sends what
 * is queued (ADR-036: only it touches the connection). When the queue is empty the selector parks
 * the connection (no OP_WRITE), and the producer wakes it through requestWrite, so a slow
 * producer never makes the selector spin. Producing more or fewer bytes than declared fails the
 * stream, which closes the connection, rather than sending a body of the wrong length.
 * Small producer writes may share a reserved 64 KiB chunk. Startup and selector draining publish
 * partial chunks promptly; a partial producer chunk remains charged until transmitted or cancelled.
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

    static final int CHUNK = 64 * 1024;
    static final int WRITE_BUDGET = 256 * 1024;

    private final ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(16);
    private final long contentLength;
    private final StreamSlots slots;
    private final AudioBufferBudget budget;
    private final boolean batchWrites;
    private final AtomicBoolean parked = new AtomicBoolean();
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile Runnable wake;
    private volatile boolean producerDone;
    private volatile Throwable producerFailure;
    private volatile boolean closed;
    private ByteBuffer pending; // selector thread only
    private ByteBuffer producerBuffer; // guarded by this; cancellation owns its reservation too
    private Future<?> task;

    StreamingResponse(int status, String statusText, long contentLength, StreamSlots slots) {
        this(status, statusText, contentLength, slots, new AudioBufferBudget(2L * 1024 * 1024));
    }

    StreamingResponse(int status, String statusText, long contentLength, StreamSlots slots, AudioBufferBudget budget) {
        this(status, statusText, contentLength, slots, budget, true);
    }

    StreamingResponse(int status, String statusText, long contentLength, StreamSlots slots,
                      AudioBufferBudget budget, boolean batchWrites) {
        if (contentLength < 0) throw new IllegalArgumentException("Content length must not be negative");
        this.budget = budget;
        this.batchWrites = batchWrites;
        this.contentLength = contentLength;
        this.slots = slots;
        setStatus(status, statusText);
        addHeader("Content-Length", String.valueOf(contentLength));
    }

    synchronized void start(ExecutorService producers, Producer producer) {
        task = producers.submit(() -> {
            long[] produced = {0};
            try {
                producer.produce((data, offset, length) -> {
                    if (length < 0 || length > contentLength - produced[0]) {
                        throw new IOException("Producer exceeded the declared Content-Length");
                    }
                    for (int at = offset; at < offset + length;) {
                        boolean needsBuffer;
                        synchronized (this) {
                            if (closed) throw new IOException("Response closed");
                            needsBuffer = producerBuffer == null;
                        }
                        if (needsBuffer) {
                            int capacity = batchWrites && !queue.isEmpty() ? (int) Math.min(CHUNK, contentLength - produced[0])
                                    : Math.min(CHUNK, offset + length - at);
                            budget.acquire(capacity, () -> closed);
                            boolean owned = false;
                            try {
                                ByteBuffer chunk = ByteBuffer.allocate(capacity);
                                synchronized (this) {
                                    if (closed) throw new IOException("Response closed");
                                    producerBuffer = chunk;
                                    owned = true;
                                }
                            } finally {
                                if (!owned) budget.release(capacity);
                            }
                        }
                        boolean full;
                        synchronized (this) {
                            if (closed) throw new IOException("Response closed");
                            if (producerBuffer == null) continue; // selector drained a partial batch
                            int n = Math.min(producerBuffer.remaining(), offset + length - at);
                            producerBuffer.put(data, at, n);
                            at += n;
                            produced[0] += n;
                            full = !producerBuffer.hasRemaining();
                        }
                        if (full || !batchWrites) flushProducerBuffer();
                    }
                    // Publish promptly when no queued audio covers startup/producer pauses.
                    if (queue.isEmpty()) flushProducerBuffer();
                });
                if (produced[0] != contentLength) {
                    throw new IOException("Producer made " + produced[0] + " of " + contentLength + " bytes");
                }
                flushProducerBuffer();
                producerDone = true;
            } catch (Throwable t) {
                producerFailure = t;
            } finally {
                synchronized (this) { releaseProducerBuffer(); }
                wakeIfParked();
            }
        });
    }

    private void flushProducerBuffer() throws IOException {
        try {
            synchronized (this) {
                if (closed) throw new IOException("Response closed");
                if (producerBuffer == null) return;
                while (!closed && producerBuffer != null && queue.remainingCapacity() == 0) wait(200);
                if (closed) throw new IOException("Response closed");
                if (producerBuffer == null) return; // selector drained it while this producer waited
                producerBuffer.flip();
                queue.add(producerBuffer);
                producerBuffer = null; // queued response now owns the reservation
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Response closed", interrupted);
        }
        wakeIfParked();
    }

    private void releaseProducerBuffer() {
        if (producerBuffer != null) {
            budget.release(producerBuffer.capacity());
            producerBuffer = null;
        }
    }

    synchronized boolean awaitingProducer() {
        return headersSent && pending == null && queue.isEmpty()
                && (producerBuffer == null || producerBuffer.position() == 0)
                && !producerDone && producerFailure == null;
    }

    Throwable failure() { return producerFailure; }

    private void wakeIfParked() {
        if (parked.compareAndSet(true, false)) {
            Runnable w = wake;
            if (w != null) w.run();
        }
    }

    @Override
    public synchronized long write(SocketChannel channel) throws IOException {
        if (headerBuffer == null) buildHeaders();
        long written = 0;
        if (!headersSent) {
            written += channel.write(headerBuffer);
            if (headerBuffer.hasRemaining()) return written;
            headersSent = true;
        }
        int bodyWrittenThisTurn = 0;
        while (bodyWrittenThisTurn < WRITE_BUDGET) {
            if (pending == null) {
                pending = queue.poll();
                if (pending == null && producerBuffer != null && producerBuffer.position() > 0) {
                    // Never park with deliverable PCM held in a partial producer batch.
                    pending = producerBuffer;
                    pending.flip();
                    producerBuffer = null;
                }
                notifyAll(); // room for the producer; budget stays reserved while pending
            }
            if (pending == null) {
                if (producerFailure != null) throw new IOException("Stream producer failed", producerFailure);
                return written;
            }
            int previousLimit = pending.limit();
            pending.limit(Math.min(previousLimit, pending.position() + WRITE_BUDGET - bodyWrittenThisTurn));
            int n;
            try { n = channel.write(pending); }
            finally { pending.limit(previousLimit); }
            bodyWrittenThisTurn += n;
            sentBodyBytes += n;
            written += n;
            if (pending.hasRemaining()) return written; // socket buffer full: OP_WRITE stays on
            budget.release(pending.capacity());
            pending = null;
        }
        return written;
    }

    @Override
    public boolean isFullySent() {
        return headersSent && pending == null && queue.isEmpty() && producerDone;
    }

    /**
     * Selector thread, after a write that left the body unfinished: true when the queue is empty
     * and the connection should stop asking for OP_WRITE until {@code wakeAction} runs.
     */
    synchronized boolean park(Runnable wakeAction) {
        if (!headersSent || pending != null || (producerBuffer != null && producerBuffer.position() > 0)) return false;
        wake = wakeAction;
        parked.set(true);
        // Re-check after parking: the producer may have queued or finished in between
        if (!queue.isEmpty() || producerDone || producerFailure != null) {
            parked.set(false);
            return false;
        }
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (task != null) task.cancel(true);
        releaseProducerBuffer();
        ByteBuffer chunk;
        while ((chunk = queue.poll()) != null) budget.release(chunk.capacity());
        if (pending != null) {
            budget.release(pending.capacity());
            pending = null;
        }
        notifyAll();
        releaseSlot();
    }

    private void releaseSlot() {
        if (released.compareAndSet(false, true)) slots.release();
    }
}
