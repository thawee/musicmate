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
    // Total 16MB cache capacity (can hold head chunks for up to 4 tracks simultaneously)
    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024; // 16MB

    private static volatile AudioStreamCacheManager instance;

    private final LruCache<String, byte[]> memoryCache;
    private final ExecutorService preloadExecutor;
    private java.util.concurrent.Future<?> activePreloadFuture;
    private final Object preloadLock = new Object();

    private AudioStreamCacheManager() {
        this.memoryCache = new LruCache<String, byte[]>(MAX_CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, byte[] value) {
                return value != null ? value.length : 0;
            }
        };
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
     * Asynchronously pre-buffers the first HEAD_CHUNK_SIZE bytes of the given track into memory.
     */
    public void preloadTrack(Track track) {
        if (track == null || track.getPath() == null) return;
        final String path = track.getPath();

        synchronized (memoryCache) {
            if (memoryCache.get(path) != null) {
                return; // Already cached
            }
        }

        synchronized (preloadLock) {
            if (activePreloadFuture != null && !activePreloadFuture.isDone()) {
                activePreloadFuture.cancel(true);
            }

            activePreloadFuture = preloadExecutor.submit(() -> {
                try {
                    File file = new File(path);
                    if (!file.exists() || !file.canRead()) return;

                    long fileLength = file.length();
                    if (fileLength <= 0) return;

                    int bytesToRead = (int) Math.min(fileLength, HEAD_CHUNK_SIZE);
                    byte[] buffer = new byte[bytesToRead];

                    try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                         FileChannel channel = raf.getChannel()) {
                        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                        int totalRead = 0;
                        while (totalRead < bytesToRead && !Thread.currentThread().isInterrupted()) {
                            int read = channel.read(byteBuffer);
                            if (read <= 0) break;
                            totalRead += read;
                        }
                        if (totalRead > 0 && !Thread.currentThread().isInterrupted()) {
                            byte[] finalBuffer = (totalRead == bytesToRead) ? buffer : Arrays.copyOf(buffer, totalRead);
                            synchronized (memoryCache) {
                                memoryCache.put(path, finalBuffer);
                            }
                            Log.d(TAG, "Pre-buffered " + totalRead + " bytes for: " + track.getTitle());
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
     * Retrieves pre-buffered bytes if the request falls within the cached range (start offset = 0).
     */
    public byte[] getPreloadedHead(String path) {
        if (path == null) return null;
        synchronized (memoryCache) {
            return memoryCache.get(path);
        }
    }

    /**
     * Evicts a specific track from the cache.
     */
    public void evict(Track track) {
        if (track == null || track.getPath() == null) return;
        synchronized (memoryCache) {
            memoryCache.remove(track.getPath());
        }
    }

    /**
     * Clears all pre-buffered audio chunks.
     */
    public void clear() {
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
    }
}
