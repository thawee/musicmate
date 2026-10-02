package apincer.music.server.jupnp.transport;

import static apincer.music.core.utils.TagUtils.isAACFile;
import static apincer.music.core.utils.TagUtils.isLosslessFormat;
import static apincer.music.core.utils.TagUtils.isMPegFile;

import apincer.music.core.model.Track;
import apincer.music.core.utils.MimeTypeUtils;

public class DLNAHeaderHelper {

    /**
     * Audiophile metadata headers sent with every track stream, identical on all engines:
     * X-Audio-Sample-Rate/-Bit-Depth/-Bitrate (only when known), -Format and -Bit-Perfect.
     */
    public static java.util.Map<String, String> getAudioHeaders(Track tag) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (tag == null) return headers;
        if (tag.getAudioSampleRate() > 0) headers.put("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
        if (tag.getAudioBitsDepth() > 0) headers.put("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
        if (tag.getAudioBitRate() > 0) headers.put("X-Audio-Bitrate", tag.getAudioBitRate() / 1000 + " kbps");
        headers.put("X-Audio-Format", String.valueOf(tag.getFileType()));
        headers.put("X-Audio-Bit-Perfect", "true"); // files are streamed unmodified
        return headers;
    }
    // DLNA flags for audiophile streaming
    private static final String DLNA_FLAGS_STREAMING_LOSSLESS = "01700000000000000000000000000000";
    private static final String DLNA_FLAGS_GAPLESS = "01780000000000000000000000000000";

    /**
     * The DLNA 4th field for a track, used for both the contentFeatures.dlna.org header and the
     * DIDL-Lite protocolInfo so the two always agree. DLNA.ORG_PN is sent only for profiles DLNA
     * defines (MP3, AAC_ISO, AAC_ADTS). DLNA has none for FLAC, ALAC, DSD, WAV or AIFF; an
     * invented name ("FLAC_HD", "DSD") lets strict renderers refuse the file, so those omit it.
     * OP=01: seeking by byte range only.
     */
    public static String getDLNAContentFeatures(Track tag) {
        String profile = dlnaProfile(tag);
        return (profile != null ? "DLNA.ORG_PN=" + profile + ";" : "")
                + "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + DLNA_FLAGS_STREAMING_LOSSLESS;
    }

    private static String dlnaProfile(Track tag) {
        if (isMPegFile(tag)) return "MP3";
        if (isAACFile(tag)) {
            String path = tag.getPath() == null ? "" : tag.getPath().toLowerCase(java.util.Locale.ROOT);
            return path.endsWith(".aac") ? "AAC_ADTS" : "AAC_ISO"; // raw ADTS stream vs MP4 container
        }
        return null;
    }

    /**
     * Get appropriate MIME type with additional parameters for audiophile streaming
     */
    public static String getEnhancedContentType(Track tag) {
        String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

        // Add quality parameters for audiophile streaming
        if (isLosslessFormat(tag) && tag.getAudioSampleRate() > 0 && tag.getAudioBitsDepth() > 0) {
            return mimeType + "; rate=" + tag.getAudioSampleRate() + "; bits=" + tag.getAudioBitsDepth();
        }

        return mimeType;
    }
}
