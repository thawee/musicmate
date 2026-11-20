package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CalibrationMethod
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HeadphoneProfile(
    val id: String,
    val name: String,
    val source: String,
    val preampDb: Double,
    val bands: List<PeqBand>,
    val manual: ManualHeadphoneTuning? = null,
    val output: CalibrationOutput = CalibrationOutput.HEADPHONE,
    val method: CalibrationMethod = CalibrationMethod.AUTO_EQ
) {
    val calibrationMethod: CalibrationMethod get() = if (manual != null) CalibrationMethod.LISTENING else method

    companion object {
        fun createManual(name: String, tuning: ManualHeadphoneTuning, output: CalibrationOutput = CalibrationOutput.HEADPHONE): HeadphoneProfile {
            HeadphoneValidation.text(name, "Headphone name", 200)
            tuning.validate()
            return HeadphoneProfile(UUID.randomUUID().toString(), name.trim(), "Listening setup", 0.0, emptyList(), tuning,
                output, CalibrationMethod.LISTENING)
        }
    }
}

@Serializable
data class HeadphoneBinding(
    val profileId: String? = null,
    val enabled: Boolean = false,
    val bassDb: Double = 0.0,
    val trebleDb: Double = 0.0,
    val usbEqAllowed: Boolean = false,
    /** Legacy files default to Custom so their saved trims retain their sound. */
    val soundMode: SoundMode = SoundMode.CUSTOM,
    val nightListening: Boolean = false,
    val crossfeed: Boolean = false
) {
    /** Bass migration keeps its shelf approximately; its old mid cut is intentionally retired. */
    internal fun migrated(): HeadphoneBinding = when (soundMode) {
        SoundMode.NIGHT -> copy(soundMode = SoundMode.REFERENCE, nightListening = true)
        SoundMode.BASS -> copy(soundMode = SoundMode.CUSTOM, bassDb = 4.0)
        else -> this
    }
}

@Serializable
data class HeadphoneLibrary(
    val profiles: List<HeadphoneProfile> = emptyList(),
    val bindings: Map<String, HeadphoneBinding> = emptyMap()
)

object HeadphoneProfileParser {
    const val MAX_BYTES = 65_536

    private val preampCommand = Regex("Preamp:\\s+(\\S+)\\s+dB", RegexOption.IGNORE_CASE)
    private val filterCommand = Regex("Filter\\s+([0-9]+):\\s+(ON|OFF)\\b(.*)", RegexOption.IGNORE_CASE)
    private val filterParameters = Regex("(\\S+)\\s+Fc\\s+(\\S+)\\s+Hz\\s+Gain\\s+(\\S+)\\s+dB\\s+Q\\s+(\\S+)", RegexOption.IGNORE_CASE)
    private val footer = Regex("See\\s+https?://\\S+.*", RegexOption.IGNORE_CASE)
    private const val AUTO_EQ_USAGE_FOOTER = "See [usage instructions](https://github.com/jaakkopasanen/AutoEq#usage) for more options and info."

    fun parse(text: String, name: String, source: String): HeadphoneProfile {
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Profile is too large; import a parametric text file up to 64 KiB."
        }
        HeadphoneValidation.text(name, "Headphone name", 200)
        HeadphoneValidation.text(source, "Profile source", 512)
        var preampDb = 0.0
        var hasPreamp = false
        val bands = mutableListOf<PeqBand>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            if (raw.trim() == AUTO_EQ_USAGE_FOOTER) return@forEachIndexed
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("//") || footer.matches(line)) return@forEachIndexed
            val preamp = preampCommand.matchEntire(line)
            if (preamp != null) {
                require(!hasPreamp) { "Line $lineNumber: include only one Preamp command." }
                preampDb = number(preamp.groupValues[1], "Preamp", -30.0..0.0, lineNumber)
                hasPreamp = true
                return@forEachIndexed
            }
            val filter = filterCommand.matchEntire(line)
                ?: throw IllegalArgumentException("Line $lineNumber: unsupported or malformed command. Use Preamp or Filter with PK, LSC or HSC, Fc, Gain and Q.")
            require((filter.groupValues[1].toIntOrNull() ?: 0) > 0) { "Line $lineNumber: filter number must be a positive integer." }
            if (filter.groupValues[2].equals("OFF", ignoreCase = true)) return@forEachIndexed
            val parameters = filter.groupValues[3].trim()
            val typeName = parameters.substringBefore(' ').substringBefore('\t').uppercase()
            val type = when (typeName) {
                "PK" -> PeqBand.Type.PEAK
                "LSC" -> PeqBand.Type.LOW_SHELF
                "HSC" -> PeqBand.Type.HIGH_SHELF
                else -> throw IllegalArgumentException("Line $lineNumber: unsupported active filter '$typeName'. Use PK, LSC or HSC.")
            }
            val values = filterParameters.matchEntire(parameters)
                ?: throw IllegalArgumentException("Line $lineNumber: malformed filter. Expected ON $typeName Fc <Hz> Hz Gain <dB> dB Q <Q>.")
            bands += PeqBand(
                type,
                number(values.groupValues[2], "Frequency (Hz)", 20.0..20_000.0, lineNumber),
                number(values.groupValues[3], "Gain (dB)", -24.0..12.0, lineNumber),
                number(values.groupValues[4], "Q", 0.1..12.0, lineNumber)
            )
            require(bands.size <= 20) { "Line $lineNumber: at most 20 active filters are supported." }
        }
        require(bands.isNotEmpty()) { "Profile must contain at least one active PK, LSC or HSC filter." }
        return HeadphoneProfile(UUID.randomUUID().toString(), name.trim(), source.trim(), preampDb, bands.toList())
    }

    private fun number(token: String, label: String, range: ClosedFloatingPointRange<Double>, line: Int): Double {
        val value = token.toDoubleOrNull()
        require(value != null && value.isFinite() && value in range) {
            "Line $line: $label must be a finite number from ${range.start} to ${range.endInclusive}."
        }
        return value
    }
}

internal object HeadphoneValidation {
    fun text(value: String, label: String, maxLength: Int) {
        require(value.isNotBlank() && value.length <= maxLength && value.none { it.isISOControl() }) {
            "$label must contain 1–$maxLength characters without control characters."
        }
    }

    fun library(library: HeadphoneLibrary) {
        require(library.profiles.size <= 100) { "At most 100 headphone profiles can be stored." }
        require(library.bindings.size <= 100) { "At most 100 output bindings can be stored." }
        require(library.profiles.map { it.id }.distinct().size == library.profiles.size) { "Profile IDs must be unique." }
        library.profiles.forEach { profile ->
            text(profile.id, "Profile ID", 128)
            text(profile.name, "Headphone name", 200)
            text(profile.source, "Profile source", 512)
            require(profile.preampDb.isFinite() && profile.preampDb in -30.0..0.0) { "Preamp must be between -30 and 0 dB." }
            if (profile.manual != null) {
                require(profile.bands.isEmpty() && profile.preampDb == 0.0) { "Listening profiles use their own tone controls." }
                profile.manual.validate()
            } else require(profile.bands.size in (if (profile.method == CalibrationMethod.MICROPHONE) 0 else 1)..20) {
                "Profiles must contain 1–20 active filters; a measured flat correction may have none."
            }
            profile.bands.forEach { band ->
                require(band.frequencyHz.isFinite() && band.frequencyHz in 20.0..20_000.0) { "Filter frequency must be between 20 and 20000 Hz." }
                require(band.gainDb.isFinite() && band.gainDb in -24.0..12.0) { "Filter gain must be between -24 and 12 dB." }
                require(band.q.isFinite() && band.q in 0.1..12.0) { "Filter Q must be between 0.1 and 12." }
            }
        }
        library.bindings.forEach { (output, binding) ->
            text(output, "Output ID", 512)
            binding.profileId?.let { text(it, "Selected profile ID", 128) }
            require(binding.bassDb.isFinite() && binding.bassDb in -6.0..6.0) { "Personal bass must be between -6 and 6 dB." }
            require(binding.trebleDb.isFinite() && binding.trebleDb in -6.0..6.0) { "Personal treble must be between -6 and 6 dB." }
        }
    }
}
