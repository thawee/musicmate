package apincer.android.mmate.ui.compose

import apincer.android.mmate.audio.OutputGuardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardLightTest {
    @Test
    fun limitingStaysLitForThreeSeconds() {
        val state = OutputGuardState(limitedAtMs = 1_000)
        assertEquals(GuardLight.LIMITING, guardLight(state, 1_000))
        assertEquals(GuardLight.LIMITING, guardLight(state, 3_999))
        assertNull(guardLight(state, 4_000))
    }

    @Test
    fun aClipOutranksLimitingUntilItExpires() {
        val state = OutputGuardState(limitedAtMs = 5_000, clippedAtMs = 4_000)
        assertEquals(GuardLight.CLIPPING, guardLight(state, 5_000))
        assertEquals(GuardLight.LIMITING, guardLight(state, 7_000))
    }

    @Test
    fun nothingRecordedIsDark() {
        assertNull(guardLight(OutputGuardState(), 10_000))
    }
}
