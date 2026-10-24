package apincer.android.mmate.audio

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Puts Android's own FLAC decoder (libFLAC) ahead of a vendor one. Samsung's c2.sec.flac.decoder
 * returned noise and NaN for a valid 16-bit FLAC (S25, 2026-10-24; that file uses 1152-sample
 * blocks); the platform decoder is the reference. Other formats keep Android's order.
 */
object FlacDecoderSelector : MediaCodecSelector {
    override fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean): List<MediaCodecInfo> {
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        return if (mimeType == MimeTypes.AUDIO_FLAC) preferPlatform(decoders) { it.name } else decoders
    }

    /** Platform decoders first, otherwise in the given order. */
    fun <T> preferPlatform(decoders: List<T>, name: (T) -> String): List<T> =
        decoders.sortedBy { if (name(it).startsWith(PLATFORM_PREFIX)) 0 else 1 }

    private const val PLATFORM_PREFIX = "c2.android."
}
