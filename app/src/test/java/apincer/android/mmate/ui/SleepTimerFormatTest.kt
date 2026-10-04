package apincer.android.mmate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerFormatTest {

    @Test
    fun formatSleepRemaining_coversModes() {
        assertEquals("", MainActivity.formatSleepRemaining(0))
        assertEquals("Track End", MainActivity.formatSleepRemaining(-1))
        assertEquals("30m", MainActivity.formatSleepRemaining(30 * 60_000L))
        assertEquals("2m", MainActivity.formatSleepRemaining(60_001))
        assertEquals("1m", MainActivity.formatSleepRemaining(60_000))
        assertEquals("59s", MainActivity.formatSleepRemaining(59_000))
        assertEquals("1s", MainActivity.formatSleepRemaining(1))
    }
}
