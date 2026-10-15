package apincer.android.mmate.ui

import apincer.android.mmate.ui.compose.isSleepOptionSelected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun sleepOption_staysSelectedWhileCountingDown() {
        // 30 minutes chosen, one minute later the countdown reads "29m"
        assertTrue(isSleepOptionSelected(30, false, "29m", 30))
        assertTrue(isSleepOptionSelected(30, false, "45s", 30))
        assertFalse(isSleepOptionSelected(15, false, "29m", 30))
        assertFalse(isSleepOptionSelected(0, true, "29m", 30))
        assertFalse(isSleepOptionSelected(0, false, "29m", 30))
    }

    @Test
    fun sleepOption_endOfTrackAndOff() {
        assertTrue(isSleepOptionSelected(0, true, "Track End", 0))
        assertFalse(isSleepOptionSelected(30, false, "Track End", 0))
        assertFalse(isSleepOptionSelected(0, false, "Track End", 0))
        assertTrue(isSleepOptionSelected(0, false, "", 0))
        assertFalse(isSleepOptionSelected(0, true, "", 0))
        assertFalse(isSleepOptionSelected(30, false, "", 0))
    }
}
