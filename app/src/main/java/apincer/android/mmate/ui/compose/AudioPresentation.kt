package apincer.android.mmate.ui.compose

import androidx.compose.ui.graphics.Color
import apincer.music.core.model.Track
import apincer.music.core.utils.TagUtils

/** Native file metadata presentation; original MQA rates do not describe decoder output. */
object AudioPresentation {
    private enum class Tier(val compact: String, val expanded: String, val accent: Color) {
        DSD("DSD", "DSD AUDIO", Color(0xFF00E5FF)),
        MQA("MQA", "MQA MASTER", Color(0xFFE040FB)),
        MQA_STUDIO("MQA", "MQA STUDIO", Color(0xFFE040FB)),
        HI_RES("HI-RES", "HI-RES LOSSLESS", Color(0xFFFFD700)),
        PCM24("24-BIT", "24-BIT STUDIO", Color(0xFFFFD700)),
        CD("CD", "CD QUALITY", Color(0xFF64B5F6)),
        LOSSY("", "STANDARD QUALITY", Color(0xFF9E9E9E)),
        UNKNOWN("", "CD QUALITY", Color(0xFF9E9E9E))
    }

    private fun tier(track: Track): Tier = when {
        TagUtils.isDSD(track) -> Tier.DSD
        TagUtils.isMQAStudio(track) -> Tier.MQA_STUDIO
        TagUtils.isMQA(track) -> Tier.MQA
        TagUtils.isHiRes(track) -> Tier.HI_RES
        TagUtils.isPCM24Bits(track) -> Tier.PCM24
        TagUtils.isLossless(track) -> Tier.CD
        TagUtils.isLossy(track) -> Tier.LOSSY
        else -> Tier.UNKNOWN
    }

    private fun tier(raw: String): Tier = when {
        raw.contains("DSD", true) -> Tier.DSD
        raw.contains("MQA", true) && raw.contains("Studio", true) -> Tier.MQA_STUDIO
        raw.contains("MQA", true) -> Tier.MQA
        raw.contains("Hi-Res", true) -> Tier.HI_RES
        raw.contains("24-BIT", true) || raw.contains("Studio", true) -> Tier.PCM24
        raw.contains("CD", true) || raw.contains("Lossless", true) -> Tier.CD
        raw.contains("Standard", true) || raw.contains("Lossy", true) -> Tier.LOSSY
        else -> Tier.UNKNOWN
    }

    @JvmStatic
    fun qualityLabel(track: Track?, expanded: Boolean): String {
        if (track == null) return "-"
        val tier = tier(track)
        if (expanded && tier != Tier.UNKNOWN) return tier.expanded
        val raw = track.qualityInd.orEmpty().takeUnless { it.isEmpty() || it == "-" }
            ?: TagUtils.getQualityIndicator(track)
        return qualityLabel(raw, expanded)
    }

    fun qualityLabel(raw: String?, expanded: Boolean): String {
        val value = raw.orEmpty()
        val tier = tier(value)
        return if (expanded) {
            if (tier != Tier.UNKNOWN || value.isEmpty() || value == "-") tier.expanded else value.uppercase()
        } else {
            if (tier == Tier.MQA || tier == Tier.MQA_STUDIO) "MQA" else value.ifEmpty { "-" }
        }
    }

    fun accent(track: Track): Color = tier(track).accent
    fun accent(raw: String?): Color {
        val label = qualityLabel(raw, true)
        val tier = tier(label)
        return if (tier == Tier.UNKNOWN && label.contains("HR", true)) Tier.HI_RES.accent else tier.accent
    }

    private fun rate(value: Long): String = (value / 1000.0).toString().removeSuffix(".0")

    fun compactResolution(track: Track): String = when {
        TagUtils.isDSD(track) -> "DSD${dsdMultiplier(track)}"
        track.audioBitsDepth > 0 && track.audioSampleRate > 0 -> "${track.audioBitsDepth}/${rate(track.audioSampleRate)}"
        track.audioBitRate > 0 -> "${track.audioBitRate / 1000}k"
        else -> ""
    }

    private fun dsdMultiplier(track: Track): Int = when {
        track.audioSampleRate >= 22579200 -> 512
        track.audioSampleRate >= 11289600 -> 256
        track.audioSampleRate >= 5644800 -> 128
        else -> 64
    }

    fun unifiedBadgeText(track: Track?): String {
        if (track == null) return "-"
        if (TagUtils.isDSD(track)) return compactResolution(track) // e.g. "DSD64" without space
        val quality = tier(track).compact
        val resolution = compactResolution(track)
        return listOf(quality, resolution).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "-" }
    }

    fun encodedResolution(track: Track?): String {
        if (track == null) return ""
        if (TagUtils.isDSD(track)) return compactResolution(track)
        return listOf(
            track.audioBitsDepth.takeIf { it > 0 }?.let { "$it-bit" },
            track.audioSampleRate.takeIf { it > 0 }?.let { "${rate(it)} kHz" }
        ).filterNotNull().joinToString(" / ")
    }

    fun originalRate(track: Track?): String =
        if (track != null && TagUtils.isMQA(track) && track.mqaSampleRate > 0) "${rate(track.mqaSampleRate)} kHz" else ""

    fun resolutionDescription(track: Track): String {
        val encoded = encodedResolution(track)
        val original = originalRate(track)
        return listOf(
            encoded.takeIf { it.isNotEmpty() }?.let { "Encoded: $it" },
            original.takeIf { it.isNotEmpty() }?.let { "Original: $it" }
        ).filterNotNull().joinToString("; ").ifEmpty { compactResolution(track) }
    }
}
