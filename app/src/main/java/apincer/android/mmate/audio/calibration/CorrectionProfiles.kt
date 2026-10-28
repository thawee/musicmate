package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.headphones.HeadphoneProfile
import kotlinx.serialization.Serializable

@Serializable
enum class CalibrationOutput { SPEAKER, HEADPHONE }

@Serializable
enum class CalibrationMethod(val label: String) {
    MICROPHONE("Microphone"), LISTENING("Listening"), AUTO_EQ("AutoEQ")
}

data class CorrectionProfileChoice(val id: String, val name: String, val output: CalibrationOutput, val method: String)

/** Present both stores together while keeping released profile files and Bluetooth matching intact. */
object CorrectionProfiles {
    fun fromSpeaker(profile: CarProfile) = HeadphoneProfile(
        id = "speaker:${profile.id}", name = profile.name, source = "Microphone · ${profile.micSource}",
        preampDb = 0.0, bands = profile.bands, output = CalibrationOutput.SPEAKER, method = CalibrationMethod.MICROPHONE)

    fun choices(speakers: List<CarProfile>, profiles: List<HeadphoneProfile>): List<CorrectionProfileChoice> =
        (profiles + speakers.map(::fromSpeaker).filter { converted -> profiles.none { it.id == converted.id } })
            .map { CorrectionProfileChoice(it.id, it.name, it.output, it.calibrationMethod.label) }
            .sortedWith(compareBy({ it.output.ordinal }, { it.name.lowercase(java.util.Locale.ROOT) }))
}
