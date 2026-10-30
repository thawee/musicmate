package apincer.android.mmate.audio.dsp

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class AutoSoundField(val label: String) { GENRE("Genre"), STYLE("Style") }

@Serializable
data class AutoSoundRule(val field: AutoSoundField, val value: String, val mode: SoundMode)

@Serializable
data class AutoSoundConfig(
    val enabled: Boolean = false,
    val fallback: SoundMode = SoundMode.REFERENCE,
    val rules: List<AutoSoundRule> = listOf(
        AutoSoundRule(AutoSoundField.GENRE, "Ambient", SoundMode.RELAX),
        AutoSoundRule(AutoSoundField.STYLE, "Acoustic", SoundMode.REFERENCE),
        AutoSoundRule(AutoSoundField.GENRE, "Electronic", SoundMode.LIVELY)
    )
)

data class AutoSoundResolution(val mode: SoundMode, val reason: String)

data class AutoSoundState(
    val config: AutoSoundConfig = AutoSoundConfig(),
    val held: Boolean = false,
    val resolvedMode: SoundMode = SoundMode.REFERENCE,
    val reason: String = "No matching tag · Neutral fallback"
)

/** Ordered, exact tag matching. Multiword styles stay intact; comparisons ignore case/spacing. */
object AutoSoundMode {
    private val separators = Regex("[;,/|\\n\\r\\u0000]+")
    private val whitespace = Regex("\\s+")
    private fun normalized(value: String) = value.trim().replace(whitespace, " ").lowercase(Locale.ROOT)
    private fun tokens(value: String?) = value.orEmpty().split(separators).map(::normalized).filter { it.isNotEmpty() }.toSet()

    fun resolve(config: AutoSoundConfig, genre: String?, style: String?): AutoSoundResolution {
        val genres = tokens(genre)
        val styles = tokens(style)
        val rule = config.rules.firstOrNull {
            normalized(it.value).isNotEmpty() && normalized(it.value) in if (it.field == AutoSoundField.GENRE) genres else styles
        }
        return if (rule == null) AutoSoundResolution(config.fallback, "No matching tag · ${config.fallback.label} fallback")
        else AutoSoundResolution(rule.mode, "${rule.field.label}: ${rule.value.trim()} → ${rule.mode.label}")
    }
}
