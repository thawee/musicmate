package apincer.music.core.http;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SerialExecutorTest {
    @Test
    public void overflow_reservesCloseAfterAdmittedMessages() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        List<Integer> calls = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(() -> pool, rejected::incrementAndGet);
        try {
            serial.execute(() -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 256; i++) {
                int n = i;
                serial.execute(() -> calls.add(n));
            }
            serial.execute(() -> calls.add(-1));
            serial.execute(() -> calls.add(-2));
            assertEquals(1, rejected.get());
            serial.executeClose(() -> { calls.add(256); closed.countDown(); });
            release.countDown();
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertEquals(257, calls.size());
            for (int i = 0; i < calls.size(); i++) assertEquals(i, calls.get(i).intValue());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
