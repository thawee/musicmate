package apincer.android.mmate.service;

import java.util.concurrent.Future;
import java.util.function.LongSupplier;

/**
 * Sends a renderer's volume without flooding it while a slider is dragged. Only the newest value
 * is ever pending, and the last one is always sent. A steady renderer gets at most one command
 * per throttle window; a fragile one (a portable player whose UPnP stack drops actions under
 * bursts) gets one command once the value has stopped moving.
 */
final class VolumeCoalescer {

    interface Sender { void send(String targetId, int volume); }

    interface Scheduler { Future<?> schedule(Runnable task, long delayMs); }

    static final long THROTTLE_MS = 300;
    static final long SETTLE_MS = 250;

    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Sender sender;

    private String pendingTarget;
    private int pendingVolume;
    private Future<?> scheduled;
    private long lastSentAt = -THROTTLE_MS;

    VolumeCoalescer(Scheduler scheduler, LongSupplier clock, Sender sender) {
        this.scheduler = scheduler;
        this.clock = clock;
        this.sender = sender;
    }

    synchronized void submit(String targetId, int volume, boolean waitUntilSettled) {
        pendingTarget = targetId;
        pendingVolume = volume;
        if (waitUntilSettled) {
            if (scheduled != null) scheduled.cancel(false);
            scheduled = scheduler.schedule(this::flush, SETTLE_MS);
        } else if (scheduled == null) {
            long wait = Math.max(0, lastSentAt + THROTTLE_MS - clock.getAsLong());
            scheduled = scheduler.schedule(this::flush, wait);
        }
    }

    private void flush() {
        String target;
        int volume;
        synchronized (this) {
            scheduled = null; // a value submitted from here on schedules its own send
            target = pendingTarget;
            volume = pendingVolume;
            pendingTarget = null;
            if (target == null) return;
            lastSentAt = clock.getAsLong();
        }
        sender.send(target, volume);
    }
}
