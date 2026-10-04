package apincer.android.mmate.worker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.music.core.model.Track;

public class FileOperationTaskCompletionTest {

    @Test
    public void parallelBatch_completesOnceAfterEveryStatus() throws Exception {
        final int total = 64;
        for (int round = 0; round < 200; round++) {
            AtomicInteger done = new AtomicInteger();
            AtomicInteger statuses = new AtomicInteger();
            AtomicInteger completions = new AtomicInteger();
            AtomicInteger statusesSeenAtCompletion = new AtomicInteger(-1);
            FileOperationTask.ProgressCallback callback = new FileOperationTask.ProgressCallback() {
                @Override
                public void onProgress(Track tag, int progress, String status) {
                    statuses.incrementAndGet();
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                    statusesSeenAtCompletion.set(statuses.get());
                }
            };

            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < total; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        callback.onProgress(null, 0, "Failed");
                    } catch (InterruptedException ignored) {
                    } finally {
                        FileOperationTask.finishItem(done, total, callback);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals(1, completions.get());
            assertEquals(total, statusesSeenAtCompletion.get());
        }
    }
}
