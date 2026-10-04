package apincer.music.core.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class MusicMateExecutorsTest {

    @Test
    public void guardedTaskSwallowsExceptionsAndLinkageErrors() {
        // Must not throw: an escaping exception would kill the worker thread and the app
        MusicMateExecutors.guarded(() -> { throw new IllegalStateException("bad file"); }).run();
        MusicMateExecutors.guarded(() -> { throw new ExceptionInInitializerError("static init"); }).run();
    }

    @Test
    public void guardedTaskStillRuns() {
        AtomicBoolean ran = new AtomicBoolean();
        MusicMateExecutors.guarded(() -> ran.set(true)).run();
        assertTrue(ran.get());
    }
}
