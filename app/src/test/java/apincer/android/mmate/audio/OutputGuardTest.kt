package apincer.android.mmate.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OutputGuardTest {
    @Before
    @After
    fun reset() = OutputGuard.reset()

    @Test
    fun theCeilingOnAFullScaleMasterStaysDark() {
        OutputGuard.report(0.944, clipped = false)
        OutputGuard.report(0.9, clipped = false)
        assertEquals(OutputGuardState(), OutputGuard.state.value)
    }

    @Test
    fun aClipRecordsEvenRightAfterLimiting() {
        OutputGuard.report(0.5, clipped = false)
        val limited = OutputGuard.state.value.limitedAtMs
        assertTrue(limited > 0)
        OutputGuard.report(0.5, clipped = false)
        assertEquals(limited, OutputGuard.state.value.limitedAtMs)
        OutputGuard.report(1.0, clipped = true)
        assertTrue(OutputGuard.state.value.clippedAtMs > 0)
        assertEquals(limited, OutputGuard.state.value.limitedAtMs)
    }
}
