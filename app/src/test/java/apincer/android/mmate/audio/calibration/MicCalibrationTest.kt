package apincer.android.mmate.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs

class MicCalibrationTest {
    private val grid = FrequencyGrid.HZ
    private fun at(hz: Double) = grid.indices.minBy { abs(grid[it] - hz) }

    @Test
    fun aUmikFileSkipsItsSensitivityLine() {
        val file = "Sens Factor =-11.36dB, AGain =18dB, SERNO: 8104655\n10.054 -5.22145\n1000 0\n20000 2.5\n"
        val curve = MicCalibration.parse(file, "8104655_90deg.txt").curveDb
        assertEquals(0.0, curve[at(1_000.0)], 0.01)
        assertEquals(2.5, curve.last(), 0.01)
        // Halfway between 1 kHz and 20 kHz on a log axis
        assertEquals(1.25, curve[at(kotlin.math.sqrt(1_000.0 * 20_000.0))], 0.02)
    }

    @Test
    fun aDaytonFileSkipsItsStarredLineAndTabs() {
        val file = "*1000Hz\t-38.0\n20.00\t-0.7\n20.55\t-0.5\n10000\t1.0\n"
        val curve = MicCalibration.parse(file, "iMM-6.txt").curveDb
        assertEquals(-0.7, curve.first(), 1e-9)
        assertEquals(1.0, curve.last(), 1e-9)
    }

    @Test
    fun commasAndAPhaseColumnAreRead() {
        val curve = MicCalibration.parse("﻿Freq(Hz), SPL(dB), Phase\n20, 1.0, 12\n20000, 1.0, -3\n", "rew.csv").curveDb
        assertEquals(1.0, curve[at(1_000.0)], 1e-9)
    }

    @Test
    fun decimalCommasBetweenSemicolonsAreRead() {
        val curve = MicCalibration.parse("20,0;-1,5\n20000,0;-1,5\n", "eu.csv").curveDb
        assertEquals(-1.5, curve[at(1_000.0)], 1e-9)
    }

    @Test
    fun windowsLineEndingsExponentsAndTabbedDecimalCommasAreRead() {
        assertEquals(2.0, MicCalibration.parse("20 2\r\n2e4 2\r\n", "crlf.txt").curveDb[at(1_000.0)], 1e-9)
        assertEquals(-0.7, MicCalibration.parse("20,0\t-0,7\n20000,0\t-0,7\n", "tabs.txt").curveDb[at(1_000.0)], 1e-9)
        assertEquals(-0.7, MicCalibration.parse("20.0,\t-0.7\n20000.0,\t-0.7\n", "both.txt").curveDb[at(1_000.0)], 1e-9)
        assertEquals(-0.7, MicCalibration.parse("20.0\t-0.7 0.0\n20000.0\t-0.7 0.0\n", "mixed.txt").curveDb[at(1_000.0)], 1e-9)
        assertEquals(-0.7, MicCalibration.parse("20,0 -0,7\n20000,0 -0,7\n", "spaced.txt").curveDb[at(1_000.0)], 1e-9)
        assertEquals(-1.0, MicCalibration.parse("20, -1, 12\n20000, -1, 12\n", "csv.txt").curveDb[at(1_000.0)], 1e-9)
    }

    @Test
    fun aFileThatIsNotACalibrationIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("20 0\n10 1\n20000 0\n", "x") }
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("200 0\n20000 0\n", "x") }
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("20 0\n20000 abc\n", "x") }
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("20\n20000\n", "x") }
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("20 0\n20000 90\n", "x") }
        assertThrows(IllegalArgumentException::class.java) { MicCalibration.parse("not a calibration file", "x") }
    }
}
