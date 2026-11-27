package apincer.android.mmate.audio.calibration

import android.content.Context

/** The last calibration file imported for each microphone, by the name Android gives it. */
class MicCalibrationStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mic_calibration", Context.MODE_PRIVATE)

    fun get(micName: String): MicCalibration? {
        val source = prefs.getString("source:$micName", null) ?: return null
        val curve = prefs.getString("curve:$micName", null)?.split(',')?.mapNotNull { it.toDoubleOrNull() }
        // A grid change makes an old curve unusable; importing the file again rebuilds it
        if (curve == null || curve.size != FrequencyGrid.HZ.size) return null
        return MicCalibration(source, curve.toDoubleArray())
    }

    fun put(micName: String, calibration: MicCalibration?) {
        prefs.edit().apply {
            if (calibration == null) remove("source:$micName").remove("curve:$micName")
            else putString("source:$micName", calibration.source).putString("curve:$micName", calibration.curveDb.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) })
        }.apply()
    }
}
