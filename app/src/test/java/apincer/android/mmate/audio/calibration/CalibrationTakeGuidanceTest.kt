package apincer.android.mmate.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTakeGuidanceTest {
    private val guidance = CalibrationTakeGuidance()

    @Test
    fun firstQuietTakePrioritizesNoiseWithoutIncreasingVolume() {
        val advice = reject(Take.Reason.TOO_QUIET)
        assertTrue(advice.contains("room and handling noise"))
        assertFalse(advice.contains("volume up"))
        assertTrue(advice.contains("Measured noise detail"))
    }

    @Test
    fun firstClippedTakeChecksHandlingNoiseBeforeLoweringVolume() {
        val advice = reject(Take.Reason.CLIPPED)
        assertTrue(advice.contains("recording overloaded"))
        assertTrue(advice.contains("Hold the microphone still"))
        assertTrue(advice.contains("if it clips again, turn the volume down"))
    }

    @Test
    fun clippedThenQuietStopsVolumeReversal() {
        reject(Take.Reason.CLIPPED)
        assertRangeAdvice(reject(Take.Reason.TOO_QUIET))
    }

    @Test
    fun quietThenClippedStopsVolumeReversal() {
        reject(Take.Reason.TOO_QUIET)
        assertRangeAdvice(reject(Take.Reason.CLIPPED))
    }

    @Test
    fun repeatedRetriesKeepRangeAdvice() {
        reject(Take.Reason.CLIPPED)
        reject(Take.Reason.TOO_QUIET)
        repeat(3) {
            assertRangeAdvice(reject(Take.Reason.CLIPPED))
            assertRangeAdvice(reject(Take.Reason.TOO_QUIET))
        }
    }

    @Test
    fun resetClearsBothRejectionHistories() {
        reject(Take.Reason.CLIPPED)
        guidance.reset()
        assertFalse(reject(Take.Reason.TOO_QUIET).contains("Recent attempts"))
        guidance.reset()
        assertTrue(reject(Take.Reason.CLIPPED).contains("if it clips again, turn the volume down"))
    }

    @Test
    fun otherRejectionsPreserveTheirAdviceAndLevelHistory() {
        reject(Take.Reason.CLIPPED)
        val detail = "Reconnect the selected microphone. Keep this full capture advice."
        assertEquals(detail, guidance.advice(Take.Rejected(Take.Reason.CAPTURE, detail)))
        assertEquals("Measured noise detail. Measure again; if it repeats, reconnect Android Auto.", reject(Take.Reason.TOO_SHORT))
        assertEquals("Measured noise detail. Check the volume and that the phone is playing through the speaker.", reject(Take.Reason.NO_RESPONSE))
        assertRangeAdvice(reject(Take.Reason.TOO_QUIET))
    }

    private fun reject(reason: Take.Reason) = guidance.advice(Take.Rejected(reason, "Measured noise detail"))

    private fun assertRangeAdvice(advice: String) {
        assertTrue(advice.contains("lower volume"))
        assertTrue(advice.contains("both recording overload and low signal-to-noise"))
        assertTrue(advice.contains("room and handling noise"))
        assertTrue(advice.contains("hold the microphone still"))
        assertTrue(advice.contains("check its placement before retrying"))
        assertFalse(advice.contains("Turn the volume up"))
    }
}
