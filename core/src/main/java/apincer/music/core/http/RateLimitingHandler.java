package apincer.music.core.http;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitingHandler extends ChainedHandler {
    private final int maxRequestsPerSecond;

    // Tracks the request count per IP address
    private final ConcurrentHashMap<String, ClientRecord> clients = new ConcurrentHashMap<>();

    // Evict stale entries once every 60 seconds to prevent unbounded map growth.
    private volatile long lastEvictSecond = 0;

    public RateLimitingHandler(int maxRequestsPerSecond, NioHttpServer.Handler next) {
        super(next);
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    @Override
    public NioHttpServer.HttpResponse handle(NioHttpServer.HttpRequest request) {
        String ip = request.getRemoteHost();

        // Fast integer division to get the current second
        long currentSecond = System.currentTimeMillis() / 1000;

        // Periodically remove entries that haven't been active for more than 2 seconds.
        // Uses a non-synchronized check so only one thread triggers eviction per interval.
        if (currentSecond - lastEvictSecond > 60) {
            lastEvictSecond = currentSecond;
            clients.entrySet().removeIf(e -> currentSecond - e.getValue().second > 2);
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