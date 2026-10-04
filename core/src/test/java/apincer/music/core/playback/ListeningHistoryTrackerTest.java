package apincer.music.core.playback;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class ListeningHistoryTrackerTest {
    private final List<ListeningHistoryTracker.Event> events = new ArrayList<>();
    private final ListeningHistoryTracker tracker = new ListeningHistoryTracker(events::add);

    private void start(long duration) {
        tracker.onState(1, duration, true, 0, 1000);
        tracker.onPosition(1, 0, 0, 1000);
    }

    private void progress(int from, int to) {
        for (int i = from; i <= to; i++) tracker.onPosition(1, i * 1000L, i * 1000L, 1000L + i * 1000L);
    }

    private long count(ListeningHistoryTracker.Kind kind) {
        return events.stream().filter(e -> e.kind() == kind).count();
    }

    @Test public void ninetyPercentCountsOnceDespiteDuplicateCallbacks() {
        start(100_000);
        progress(1, 89);
        assertEquals(0, count(ListeningHistoryTracker.Kind.COMPLETED));
        progress(90, 100);
        tracker.naturalEnd(100_000, 101_000);
        tracker.naturalEnd(100_000, 101_000);
        assertEquals(1, count(ListeningHistoryTracker.Kind.STARTED));
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void seekToEndIsNotACompletedListen() {
        start(100_000);
        progress(1, 10);
        tracker.seek();
        tracker.onPosition(1, 99_000, 11_000, 12_000);
        tracker.naturalEnd(12_000, 13_000);
        assertEquals(0, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void pauseAndSameTrackHandoffPreserveProgressWithoutCountingDowntime() {
        start(100_000);
        progress(1, 40);
        tracker.onState(1, 100_000, false, 40_000, 41_000);
        tracker.onState(1, 100_000, true, 100_000, 101_000);
        tracker.onPosition(1, 40_000, 100_000, 101_000);
        for (int i = 41; i <= 90; i++) tracker.onPosition(1, i * 1000L, (i + 60) * 1000L, (i + 61) * 1000L);
        assertEquals(1, count(ListeningHistoryTracker.Kind.STARTED));
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void unannouncedForwardSeekAndStalledClockCannotInflateListening() {
        start(100_000);
        progress(1, 10);
        tracker.onPosition(1, 95_000, 11_000, 12_000);
        tracker.onPosition(1, 100_000, 11_000, 12_000);
        tracker.naturalEnd(12_000, 13_000);
        assertEquals(0, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void subsecondDuplicatePositionsDoNotLoseWholeSeconds() {
        start(10_000);
        for (int i = 1; i <= 36; i++) tracker.onPosition(1, (i / 4) * 1000L, i * 250L, 1000 + i * 250L);
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void skipRecordedOnceAndReplayGetsANewSession() {
        start(100_000); progress(1, 10);
        tracker.end(true); tracker.end(true);
        assertEquals(1, count(ListeningHistoryTracker.Kind.SKIPPED));
        start(100_000); progress(1, 90);
        assertEquals(2, count(ListeningHistoryTracker.Kind.STARTED));
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void unknownDurationNeedsNaturalEndAndDoesNotCompleteAfterSeek() {
        start(0); progress(1, 100);
        assertEquals(0, count(ListeningHistoryTracker.Kind.COMPLETED));
        tracker.naturalEnd(100_000, 101_000);
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
        start(0); progress(1, 10); tracker.seek();
        tracker.naturalEnd(11_000, 12_000);
        assertEquals(1, count(ListeningHistoryTracker.Kind.COMPLETED));
    }

    @Test public void noProgressOrLongTelemetryGapDoesNotCountAsListening() {
        start(100_000);
        tracker.onPosition(1, 95_000, 95_000, 96_000);
        assertEquals(0, count(ListeningHistoryTracker.Kind.COMPLETED));
        assertEquals(0, count(ListeningHistoryTracker.Kind.STARTED));
    }
}
