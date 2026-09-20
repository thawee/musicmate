package apincer.music.core.playback;

import android.util.Log;

import androidx.collection.LruCache;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import apincer.music.core.model.Track;

/**
 * High-performance, in-memory audio head-chunk cache for stutter-free local and DLNA streaming.
 * Pre-buffers the initial 2MB - 4MB of upcoming tracks into an LRU in-memory buffer.
 * Eliminates Storage Access Framework (SAF) and SD card latency during track transitions.
 */
public class AudioStreamCacheManager {
    private static final String TAG = "AudioStreamCacheManager";

    // Max 4MB pre-buffered head chunk per track
    public static final int HEAD_CHUNK_SIZE = 4 * 1024 * 1024; // 4MB
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB direct buffer for zero GC page cache warming

    private static volatile AudioStreamCacheManager instance;

    private final LruCache<String, Boolean> preloadedPaths;
    private final ExecutorService preloadExecutor;
    private java.util.concurrent.Future<?> activePreloadFuture;
    private final Object preloadLock = new Object();

    private AudioStreamCacheManager() {
        this.preloadedPaths = new LruCache<>(64);
        this.preloadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AudioPreloader");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    public static AudioStreamCacheManager getInstance() {
        if (instance == null) {
            synchronized (AudioStreamCacheManager.class) {
                if (instance == null) {
                    instance = new AudioStreamCacheManager();
                }
            }
        }
        return instance;
    }

    /**
     * Cancels any currently queued or executing background preload task.
     * Useful during rapid queue track skipping.
     */
    public void cancelPendingPreloads() {
        synchronized (preloadLock) {
            if (activePreloadFuture != null && !activePreloadFuture.isDone()) {
                activePreloadFuture.cancel(true);
                activePreloadFuture = null;
            }
        }
    }

    /**
     * Asynchronously pre-warms the first HEAD_CHUNK_SIZE bytes of the given track into the OS page cache.
     * Uses zero JVM heap allocations and prevents flash storage read delays during track start.
     */
    public void preloadTrack(Track track) {
        if (track == null || track.getPath() == null) return;
        final String path = track.getPath();

        synchronized (preloadedPaths) {
            if (Boolean.TRUE.equals(preloadedPaths.get(path))) {
                return; // Already warmed in page cache
            }
        }

        synchronized (preloadLock) {
            activePreloadFuture = preloadExecutor.submit(() -> {
                try {
                    File file = new File(path);
                    if (!file.exists() || !file.canRead()) return;

                    long fileLength = file.length();
                    if (fileLength <= 0) return;

                    int bytesToRead = (int) Math.min(fileLength, HEAD_CHUNK_SIZE);
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

                    try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                         FileChannel channel = raf.getChannel()) {
                        int totalRead = 0;
                        while (totalRead < bytesToRead && !Thread.currentThread().isInterrupted()) {
                            byteBuffer.clear();
                            int toRead = Math.min(byteBuffer.capacity(), bytesToRead - totalRead);
                            byteBuffer.limit(toRead);
                            int read = channel.read(byteBuffer);
                            if (read <= 0) break;
                            totalRead += read;
                        }
                        if (totalRead > 0 && !Thread.currentThread().isInterrupted()) {
                            synchronized (preloadedPaths) {
                                preloadedPaths.put(path, Boolean.TRUE);
                            }
                            Log.d(TAG, "Pre-warmed " + totalRead + " bytes in OS page cache for: " + track.getTitle());
                        }
                    }
                } catch (Exception e) {
                    if (!(e instanceof java.nio.channels.ClosedByInterruptException)) {
                        Log.w(TAG, "Failed to preload track: " + track.getTitle(), e);
                    }
                }
            });
        }
    }

    /**
     * Legacy getter preserved for backward compatibility. Streaming now streams directly from FileChannel.
     * @deprecated Preloading now warms the OS page cache directly; byte buffer is not kept in heap memory.
     */
    @Deprecated
    public byte[] getPreloadedHead(String path) {
        return null;
    }

    /**
     * Evicts a specific track from the preloaded paths cache.
     */
    public void evict(Track track) {
        if (track == null || track.getPath() == null) return;
        synchronized (preloadedPaths) {
            preloadedPaths.remove(track.getPath());
        }
    }

    /**
     * Clears all pre-buffered track paths.
     */
    public void clear() {
        synchronized (preloadedPaths) {
            preloadedPaths.evictAll();
        }
    }
}
