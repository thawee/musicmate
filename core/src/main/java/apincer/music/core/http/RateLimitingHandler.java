package apincer.music.core.http;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitingHandler extends ChainedHandler {
    private final int maxRequestsPerSecond;
    // Paths that are neither counted nor limited (e.g. cover art for a WebUI grid)
    private final java.util.function.Predicate<String> exemptPath;

    // Tracks the request count per IP address
    private final ConcurrentHashMap<String, ClientRecord> clients = new ConcurrentHashMap<>();

    // Evict stale entries once every 60 seconds to prevent unbounded map growth.
    private volatile long lastEvictSecond = 0;
    
    // Atomic counter for evictions to prevent lost updates under high concurrency
    private final AtomicLong evictionCount = new AtomicLong(0);
    private final AtomicLong limitedCount = new AtomicLong(0);

    /** Requests answered with 429 since start. */
    public long getRateLimitedCount() {
        return limitedCount.get();
    }

    public RateLimitingHandler(int maxRequestsPerSecond, NioHttpServer.Handler next) {
        this(maxRequestsPerSecond, path -> false, next);
    }

    public RateLimitingHandler(int maxRequestsPerSecond, java.util.function.Predicate<String> exemptPath,
                               NioHttpServer.Handler next) {
        super(next);
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.exemptPath = exemptPath;
    }

    @Override
    public NioHttpServer.HttpResponse handle(NioHttpServer.HttpRequest request) {
        String path = request.getPath();
        if (path != null && exemptPath.test(path)) {
            return next(request);
        }
        String ip = request.getRemoteHost();

        // Fast integer division to get the current second
        long currentSecond = System.currentTimeMillis() / 1000;

        // Periodically remove entries that haven't been active for more than 2 seconds.
        // Uses atomic eviction to prevent lost updates under high concurrency.
        if (currentSecond - lastEvictSecond > 60) {
            lastEvictSecond = currentSecond;
            long removed = evictionCount.getAndIncrement();
            // Remove stale entries atomically - only one thread will do bulk cleanup
            clients.entrySet().removeIf(e -> currentSecond - e.getValue().second > 2);
            // Log eviction count periodically
            if (removed % 10000 == 0) {
                java.util.logging.Logger.getLogger("NioHttpServer").fine("RateLimiter: evicted " + removed + " stale entries");
            }
        }

        // Get or create the record for this IP (almost zero allocation after first request)
        ClientRecord record = clients.computeIfAbsent(ip, k -> new ClientRecord(currentSecond));

        synchronized (record) {
            // If we've moved to a new second, reset the counter
            if (record.second != currentSecond) {
                record.second = currentSecond;
                record.count = 0;
            }

            record.count++;

            // If the TV is spamming, drop the hammer
            if (record.count > maxRequestsPerSecond) {
                limitedCount.incrementAndGet();
                return new NioHttpServer.HttpResponse()
                        .setStatus(429, "Too Many Requests")
                        .addHeader("Retry-After", "2") // Tell the TV to back off for 2 seconds
                        .addHeader("Connection", "close") // Force the TCP socket to close
                        .setBody("Rate limit exceeded".getBytes());
            }
        }

        // The request rate is safe, pass it down the chain!
        return next(request);
    }

    // A tiny, mutable data class to prevent memory allocations
    private static class ClientRecord {
        long second;
        int count;
        ClientRecord(long second) {
            this.second = second;
            this.count = 0;
        }
    }
}