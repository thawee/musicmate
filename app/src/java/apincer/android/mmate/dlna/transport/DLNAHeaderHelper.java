package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.utils.MusicTagUtils.isAACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isALACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isDSDFile;
import static apincer.android.mmate.utils.MusicTagUtils.isFLACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isLosslessFormat;
import static apincer.android.mmate.utils.MusicTagUtils.isMPegFile;
import static apincer.android.mmate.utils.MusicTagUtils.isPCM;
import static apincer.android.mmate.utils.MusicTagUtils.isWavFile;

import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.MimeTypeUtils;

public class DLNAHeaderHelper {
    // DLNA flags for audiophile streaming
    private static final String DLNA_FLAGS_STREAMING_LOSSLESS = "01700000000000000000000000000000";
    private static final String DLNA_FLAGS_GAPLESS = "01780000000000000000000000000000";

    /**
     * Generate DLNA content features string based on audio format
     * Enhanced for audiophile quality streaming with format-specific profiles
     */
    public static String getDLNAContentFeatures(MusicTag tag) {
        //String flags = tag.isGapless() ? DLNA_FLAGS_GAPLESS : DLNA_FLAGS_STREAMING_LOSSLESS;
        String flags = DLNA_FLAGS_STREAMING_LOSSLESS;

        if (isMPegFile(tag)) {
            if (tag.getAudioBitRate() >= 320) {
                return "DLNA.ORG_PN=MP3_320;DLNA.ORG_OP=01;DLNA.ORG_CI=0"; //DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0"; //DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isFLACFile(tag)) {
            if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
                return "DLNA.ORG_PN=FLAC_HD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isWavFile(tag)) {
            if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
                return "DLNA.ORG_PN=LPCM_HD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isAACFile(tag)) {
            if (tag.getAudioBitRate() >= 320) {
                return "DLNA.ORG_PN=AAC_ADTS_320;DLNA.ORG_OP=01;DLNA.ORG_CI=0"; //DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=AAC_ADTS;DLNA.ORG_OP=01;DLNA.ORG_CI=0"; //DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isALACFile(tag)) {
            if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
                return "DLNA.ORG_PN=ALAC_HD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=ALAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isDSDFile(tag)) {
            // DSD support for audiophiles
            return "DLNA.ORG_PN=DSD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
        } else if (isPCM(tag)) {
            if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
                return "DLNA.ORG_PN=LPCM_HD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            }
        } else if (isLosslessFormat(tag)) {
            if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
                return "DLNA.ORG_PN=LPCM_HD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            } else {
                return "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
            }
        }

        // Default profile
        return "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=" + flags;
    }

    /**
     * Get appropriate MIME type with additional parameters for audiophile streaming
     */
    public static String getEnhancedContentType(MusicTag tag) {
        String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

        // Add quality parameters for audiophile streaming
        if (isLosslessFormat(tag) && tag.getAudioSampleRate() > 0 && tag.getAudioBitsDepth() > 0) {
            return mimeType + "; rate=" + tag.getAudioSampleRate() + "; bits=" + tag.getAudioBitsDepth();
        }

        return mimeType;
    }
}
