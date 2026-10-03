package apincer.music.core.http;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamingResponseTest {
    @Test
    public void selectorDrainingWhileProducerWaitsForQueueSpace_preservesEveryByte() throws Exception {
        byte[] body = new byte[20 * 4096];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
        AudioBufferBudget budget = new AudioBufferBudget(1024 * 1024);
        StreamingResponse response = new StreamingResponse(200, "OK", body.length, unlimitedSlots(), budget, false);
        ExecutorService producer = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Thread> worker = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
        try {
            response.start(producer, sink -> {
                worker.set(Thread.currentThread()); started.countDown();
                for (int at = 0; at < body.length; at += 4096) sink.write(body, at, 4096);
                finished.countDown();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((budget.used() != 17L * 4096 || worker.get().getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < deadline) Thread.yield();
            assertEquals(17L * 4096, budget.used());
            assertEquals(Thread.State.TIMED_WAITING, worker.get().getState());
            AcceptingChannel channel = new AcceptingChannel(); response.headersSent = true;
            response.write(channel); // drains all queued chunks and the producer-held seventeenth
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            while (!response.isFullySent() && System.nanoTime() < deadline) response.write(channel);
            assertTrue(response.isFullySent());
            assertNull(response.failure());
            assertArrayEquals(body, channel.bytes.toByteArray());
            assertEquals(0, budget.used());
        } finally { response.close(); producer.shutdownNow(); }
    }

    @Test
    public void smallWrites_areBatchedWithoutChangingBytesOrFinalTail() throws Exception {
        byte[] body = new byte[4 * StreamingResponse.CHUNK + 123];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
        AudioBufferBudget budget = new AudioBufferBudget(1024 * 1024);
        StreamingResponse response = new StreamingResponse(200, "OK", body.length, unlimitedSlots(), budget);
        ExecutorService producer = Executors.newSingleThreadExecutor();
        CountDownLatch produced = new CountDownLatch(1);
        try {
            response.start(producer, sink -> {
                for (int at = 0; at < body.length; at += 4096) {
                    sink.write(body, at, Math.min(4096, body.length - at));
                }
                produced.countDown();
            });
            assertTrue(produced.await(5, TimeUnit.SECONDS));
            AcceptingChannel channel = new AcceptingChannel();
            response.headersSent = true;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!response.isFullySent() && System.nanoTime() < deadline) response.write(channel);
            assertTrue(response.isFullySent());
            assertArrayEquals(body, channel.bytes.toByteArray());
            assertTrue("small decoder writes were not batched", channel.writeCalls < 10);
            assertEquals(0, budget.used());
        } finally { response.close(); producer.shutdownNow(); }
    }

    @Test
    public void pausedProducer_partialBatchIsDrainedBeforeParking() throws Exception {
        AudioBufferBudget budget = new AudioBufferBudget(6 * StreamingResponse.CHUNK);
        StreamingResponse response = new StreamingResponse(200, "OK", 8L * StreamingResponse.CHUNK,
                unlimitedSlots(), budget);
        ExecutorService producer = Executors.newSingleThreadExecutor();
        CountDownLatch staged = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            response.start(producer, sink -> {
                sink.write(new byte[StreamingResponse.WRITE_BUDGET], 0, StreamingResponse.WRITE_BUDGET);
                sink.write(new byte[4096], 0, 4096);
                staged.countDown();
                release.await(5, TimeUnit.SECONDS);
            });
            assertTrue(staged.await(5, TimeUnit.SECONDS));
            AcceptingChannel channel = new AcceptingChannel();
            response.headersSent = true;
            response.write(channel);
            assertEquals(StreamingResponse.WRITE_BUDGET, response.bodyBytesSent());
            assertFalse(response.awaitingProducer());
            assertFalse(response.park(() -> fail("partial PCM must not park")));
            response.write(channel);
            assertEquals(StreamingResponse.WRITE_BUDGET + 4096, response.bodyBytesSent());
            assertEquals(0, budget.used());
            assertTrue(response.awaitingProducer());
        } finally { response.close(); release.countDown(); producer.shutdownNow(); }
    }

    @Test
    public void partialBatch_isReleasedWhenProducerFailsOrIsCancelled() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            AudioBufferBudget budget = new AudioBufferBudget(2 * StreamingResponse.CHUNK);
            StreamingResponse response = new StreamingResponse(200, "OK", 3L * StreamingResponse.CHUNK,
                    unlimitedSlots(), budget);
            ExecutorService producer = Executors.newSingleThreadExecutor();
            CountDownLatch staged = new CountDownLatch(1), release = new CountDownLatch(1);
            try {
                response.start(producer, sink -> {
                    sink.write(new byte[4096], 0, 4096); // first bytes queued promptly
                    sink.write(new byte[4096], 0, 4096); // producer-held partial batch
                    staged.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    if (fail) throw new IOException("decoder failed with partial batch");
                });
                assertTrue(staged.await(5, TimeUnit.SECONDS));
                assertEquals(4096L + StreamingResponse.CHUNK, budget.used());
                if (fail) {
                    release.countDown();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (response.failure() == null && System.nanoTime() < deadline) Thread.yield();
                    assertNotNull(response.failure());
                    while (budget.used() != 4096 && System.nanoTime() < deadline) Thread.yield();
                    assertEquals(4096, budget.used());
                }
                response.close();
                assertEquals(0, budget.used());
            } finally { release.countDown(); response.close(); producer.shutdownNow(); }
        }
    }

    @Test
    public void shortProducer_flushesFirstBytesBeforePausingAndStillFailsLengthCheck() throws Exception {
        AudioBufferBudget budget = new AudioBufferBudget(StreamingResponse.CHUNK);
        StreamingResponse response = new StreamingResponse(200, "OK", 1000, unlimitedSlots(), budget);
        ExecutorService producer = Executors.newSingleThreadExecutor();
        CountDownLatch first = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            response.start(producer, sink -> {
                sink.write(new byte[]{1, 2, 3}, 0, 3);
                first.countDown();
                release.await(5, TimeUnit.SECONDS);
            });
            assertTrue(first.await(5, TimeUnit.SECONDS));
            AcceptingChannel channel = new AcceptingChannel();
            response.headersSent = true;
            response.write(channel);
            assertArrayEquals(new byte[]{1, 2, 3}, channel.bytes.toByteArray());
            assertFalse(response.isFullySent());
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (response.failure() == null && System.nanoTime() < deadline) Thread.yield();
            assertNotNull(response.failure());
            assertThrows(IOException.class, () -> response.write(channel));
            assertEquals(0, budget.used());
        } finally { release.countDown(); response.close(); producer.shutdownNow(); }
    }

    private static StreamSlots unlimitedSlots() {
        return new StreamSlots() {
            public boolean acquire() { return true; }
            public void release() { }
        };
    }

    @Test
    public void sharedBudget_includesPendingBytesAndIsReleasedOnCancellation() throws Exception {
        AudioBufferBudget budget = new AudioBufferBudget(StreamingResponse.CHUNK);
        StreamSlots slots = new StreamSlots() {
            public boolean acquire() { return true; }
            public void release() { }
        };
        StreamingResponse first = new StreamingResponse(200, "OK", 2L * StreamingResponse.CHUNK, slots, budget);
        StreamingResponse second = new StreamingResponse(200, "OK", StreamingResponse.CHUNK, slots, budget);
        ExecutorService producers = Executors.newFixedThreadPool(2);
        CountDownLatch firstQueued = new CountDownLatch(1);
        CountDownLatch secondQueued = new CountDownLatch(1);
        try {
            first.start(producers, sink -> {
                byte[] chunk = new byte[StreamingResponse.CHUNK];
                sink.write(chunk, 0, chunk.length);
                firstQueued.countDown();
                sink.write(chunk, 0, chunk.length);
            });
            assertTrue(firstQueued.await(5, TimeUnit.SECONDS));
            second.start(producers, sink -> {
                byte[] chunk = new byte[StreamingResponse.CHUNK];
                sink.write(chunk, 0, chunk.length);
                secondQueued.countDown();
            });
            AcceptingChannel channel = new AcceptingChannel();
            first.buildHeaders();
            first.headersSent = true; // isolate a partial body write from the headers
            channel.maxWrite = 137;
            first.write(channel);
            assertEquals(137, first.bodyBytesSent());
            assertEquals(StreamingResponse.CHUNK, budget.used());
            assertEquals(1, secondQueued.getCount());
            first.close();
            assertTrue(secondQueued.await(5, TimeUnit.SECONDS));
            second.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (budget.used() != 0 && System.nanoTime() < deadline) Thread.yield();
            assertEquals(0, budget.used());
            assertEquals(StreamingResponse.CHUNK, budget.peak());
        } finally {
            first.close();
            second.close();
            producers.shutdownNow();
        }
    }

    @Test
    public void continuouslyReadyBody_yieldsAfterBudgetAndPreservesPartialWrites() throws Exception {
        byte[] body = new byte[10 * StreamingResponse.CHUNK];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
        AtomicInteger releases = new AtomicInteger();
        StreamSlots slots = new StreamSlots() {
            public boolean acquire() { return true; }
            public void release() { releases.incrementAndGet(); }
        };
        StreamingResponse response = new StreamingResponse(200, "OK", body.length, slots);
        ExecutorService producer = Executors.newSingleThreadExecutor();
        CountDownLatch filled = new CountDownLatch(1);
        try {
            response.start(producer, sink -> {
                sink.write(body, 0, body.length);
                filled.countDown();
            });
            assertTrue(filled.await(5, TimeUnit.SECONDS));
            AcceptingChannel channel = new AcceptingChannel();
            response.write(channel);
            assertEquals(StreamingResponse.WRITE_BUDGET, response.bodyBytesSent());
            assertFalse(response.isFullySent());
            channel.maxWrite = 137; // exercise pending offsets across selector turns
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!response.isFullySent() && System.nanoTime() < deadline) {
                long before = response.bodyBytesSent();
                response.write(channel);
                assertTrue(response.bodyBytesSent() - before <= StreamingResponse.WRITE_BUDGET);
            }
            assertTrue(response.isFullySent());
            byte[] wire = channel.bytes.toByteArray();
            int headerEnd = new String(wire, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("\r\n\r\n") + 4;
            assertArrayEquals(body, java.util.Arrays.copyOfRange(wire, headerEnd, wire.length));
        } finally {
            response.close();
            response.close();
            producer.shutdownNow();
        }
        assertEquals(1, releases.get());
    }

    /** Always writable, with a controlled per-call allowance independent of OS socket buffers. */
    static final class AcceptingChannel extends SocketChannel {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int maxWrite = Integer.MAX_VALUE;
        int writeCalls;
        AcceptingChannel() { super(null); }
        @Override public int write(ByteBuffer src) {
            writeCalls++;
            int n = Math.min(src.remaining(), maxWrite);
            byte[] data = new byte[n];
            src.get(data);
            bytes.write(data, 0, n);
            return n;
        }
        @Override public long write(ByteBuffer[] srcs, int offset, int length) { throw new UnsupportedOperationException(); }
        @Override public int read(ByteBuffer dst) { throw new UnsupportedOperationException(); }
        @Override public long read(ByteBuffer[] dsts, int offset, int length) { throw new UnsupportedOperationException(); }
        @Override public SocketChannel bind(SocketAddress local) { return this; }
        @Override public <T> SocketChannel setOption(SocketOption<T> name, T value) { return this; }
        @Override public <T> T getOption(SocketOption<T> name) { return null; }
        @Override public Set<SocketOption<?>> supportedOptions() { return Collections.emptySet(); }
        @Override public SocketChannel shutdownInput() { return this; }
        @Override public SocketChannel shutdownOutput() { return this; }
        @Override public Socket socket() { return null; }
        @Override public boolean isConnected() { return true; }
        @Override public boolean isConnectionPending() { return false; }
        @Override public boolean connect(SocketAddress remote) { return true; }
        @Override public boolean finishConnect() { return true; }
        @Override public SocketAddress getRemoteAddress() { return null; }
        @Override public SocketAddress getLocalAddress() { return null; }
        @Override protected void implCloseSelectableChannel() throws IOException { }
        @Override protected void implConfigureBlocking(boolean block) { }
    }
}
