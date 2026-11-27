package apincer.android.mmate.audio.calibration

/**
 * A measurement microphone's own response, from its maker's calibration file, on
 * [FrequencyGrid.HZ]. Every take has it subtracted, as REW does.
 */
class MicCalibration internal constructor(val source: String, val curveDb: DoubleArray) {
    companion object {
        const val MAX_BYTES = 262_144
        private val NUMBER = Regex("""[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?""")

        /**
         * One frequency and dB pair per line, then an optional phase column, which is ignored.
         * Columns are split by spaces, tabs, commas or semicolons; on a line with no period and spaced columns, a comma is a decimal point. A line that does not start
         * with a number is a header or comment, such as a UMIK file's quoted "Sens Factor" line.
         */
        fun parse(text: String, source: String): MicCalibration {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "A calibration file must be at most 256 KiB." }
            val points = mutableListOf<Pair<Double, Double>>()
            text.removePrefix("﻿").lineSequence().forEach { raw ->
                val columns = columnsOf(raw.trim()) ?: return@forEach
                require(columns.size >= 2) { "Each line needs a frequency and a level." }
                val (hz, db) = columns
                require(hz.isFinite() && hz in 1.0..48_000.0 && db.isFinite() && db in -60.0..60.0) {
                    "Line \"${raw.trim().take(40)}\" is not a frequency in Hz and a level in dB."
                }
                require(points.isEmpty() || hz > points.last().first) { "Frequencies must rise from line to line." }
                points += hz to db
            }
            require(points.size >= 2 && points.first().first <= 100 && points.last().first >= 10_000) {
                "The file must cover at least 100 Hz to 10 kHz."
            }
            return MicCalibration(source.trim().take(160).filterNot { it.isISOControl() }, FrequencyGrid.resample(points))
        }

        /** Null for a blank, header or comment line. */
        private fun columnsOf(line: String): List<Double>? {
            if (line.isEmpty() || NUMBER.matchAt(line, 0) == null) return null
            // A decimal-comma file ("20,5;-1,2", "20,5 -1,2") has no periods; one column left means comma-separated integers
            val separated = if ('.' in line) line.split(Regex("""[\s,;]+""")) else line.split(Regex("""[\s;]+""")).let { tokens ->
                if (tokens.size == 1) line.split(',') else tokens.map { it.trim(',').replace(',', '.') }
            }
            return separated.map { it.trim() }.filter { it.isNotEmpty() }.map { column ->
                requireNotNull(column.toDoubleOrNull()) { "\"${column.take(20)}\" is not a number." }
            }
        }
    }
}
