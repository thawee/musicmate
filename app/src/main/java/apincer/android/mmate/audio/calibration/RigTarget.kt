package apincer.android.mmate.audio.calibration

import kotlin.math.ln

/** A target measured on the same microphone and headphone fixture as the calibration. */
class RigTarget private constructor(val source: String, val curve: DoubleArray) {
    companion object {
        const val MAX_BYTES = 65_536

        /** CSV or TSV: frequencyHz,targetDb. A header and # comments are optional. */
        fun parse(text: String, source: String): RigTarget {
            require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Target must be at most 64 KiB." }
            require(source.isNotBlank() && source.length <= 160 && source.none { it.isISOControl() }) { "Name the fixture target (up to 160 characters)." }
            val points = mutableListOf<Pair<Double, Double>>()
            text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEach
                val columns = line.split(',', '\t').map { it.trim() }
                require(columns.size == 2) { "Use two columns: frequencyHz,targetDb." }
                if (points.isEmpty() && columns == listOf("frequencyHz", "targetDb")) return@forEach
                val hz = columns[0].toDoubleOrNull()
                val db = columns[1].toDoubleOrNull()
                require(hz != null && hz.isFinite() && hz in 10.0..24_000.0 && db != null && db.isFinite() && db in -120.0..120.0) { "Use finite frequencies from 10 to 24000 Hz and target levels from -120 to 120 dB." }
                require(points.isEmpty() || hz > points.last().first) { "Target frequencies must increase without duplicates." }
                points += hz to db
            }
            require(points.size >= 2 && points.first().first <= 100 && points.last().first >= 8000) { "Target must cover at least 100 Hz to 8000 Hz." }
            var upper = 1
            val curve = DoubleArray(FrequencyGrid.HZ.size) { index ->
                val hz = FrequencyGrid.HZ[index].coerceIn(points.first().first, points.last().first)
                while (upper < points.lastIndex && points[upper].first < hz) upper++
                val (a, av) = points[upper - 1]
                val (b, bv) = points[upper]
                av + (bv - av) * ln(hz / a) / ln(b / a)
            }
            return RigTarget(source.trim(), curve)
        }
    }
}
