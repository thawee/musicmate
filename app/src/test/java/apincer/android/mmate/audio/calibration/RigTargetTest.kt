package apincer.android.mmate.audio.calibration

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class RigTargetTest {
    @Test fun interpolatesInLogFrequencyAndKeepsCustomTarget() {
        val target = RigTarget.parse("frequencyHz,targetDb\n100,0\n8000,12", "Fixture target")
        val index = FrequencyGrid.HZ.indices.minBy { abs(FrequencyGrid.HZ[it] - 1000.0) }
        assertEquals(12 * ln(FrequencyGrid.HZ[index] / 100) / ln(80.0), target.curve[index], 1e-9)
        val fit = PeqFitter.fit(target.curve, 100.0, targetDb = target.curve)
        assertTrue(fit.bands.isEmpty())
        assertArrayEquals(target.curve, fit.targetDb, 0.0)
    }

    @Test fun rejectsInvalidCoverageNumbersAndOversize() {
        listOf("100,0\n7000,0", "200,0\n8000,0", "100,NaN\n8000,0", "100,200\n8000,0", "100,0\n100,1\n8000,0", "x".repeat(65537)).forEach {
            try { RigTarget.parse(it, "Fixture"); fail("Accepted invalid target") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun supportsTsvWithComments() {
        val target = RigTarget.parse("# rig\nfrequencyHz\ttargetDb\n20\t-5\n100\t0\n8000\t2\n20000\t-2", "Coupler")
        assertEquals("Coupler", target.source)
        assertTrue(target.curve.all { it.isFinite() })
    }
}
