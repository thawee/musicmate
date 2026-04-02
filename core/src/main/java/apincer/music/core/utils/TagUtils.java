package apincer.music.core.utils;

import static apincer.music.core.Constants.LEGEND_CD;
import static apincer.music.core.Constants.LEGEND_DSD;
import static apincer.music.core.Constants.LEGEND_HIRES;
import static apincer.music.core.Constants.LEGEND_LOSSY;
import static apincer.music.core.Constants.LEGEND_MQA;
import static apincer.music.core.Constants.LEGEND_STUDIO;
import static apincer.music.core.Constants.QUALITY_SAMPLING_RATE_44;
import static apincer.music.core.Constants.QUALITY_SAMPLING_RATE_96;
import static apincer.music.core.utils.StringUtils.SYMBOL_ENC_SEP;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Locale;

import apincer.music.core.Constants;
import apincer.music.core.model.Track;
import apincer.music.core.repository.PlaylistRepository;
import musicmate.core.R;

public class TagUtils {
    private static final String TAG = "MusicTagUtils";

    public static String getBPSAndSampleRate(Track tag) {
        long sampleRate = tag.getAudioSampleRate();
        if(isMQA(tag)) {
            sampleRate = tag.getMqaSampleRate();
        }
        return String.format("%s/%s", tag.getAudioBitsDepth(), StringUtils.formatAudioSampleRate(sampleRate,false));
    }

    public static String getSampleRate(Track tag) {
        long sampleRate = tag.getAudioSampleRate();
        if(isMQA(tag)) {
            sampleRate = tag.getMqaSampleRate();
        }
        return String.format("%s", StringUtils.formatAudioSampleRate(sampleRate,true));
    }

    public static boolean isMQAStudio(Track tag) {
        return trimToEmpty(tag.getQualityInd()).contains("MQA Studio");
    }

    public static boolean isMQA(Track tag) {
        return trimToEmpty(tag.getQualityInd()).contains("MQA");
    }

    @Deprecated
    public static int getResolutionColor(Context context, Track tag) {
        // DSD - DSD
        // Hi-Res Lossless - >= 24 bits and >= 48 kHz
        // Lossless - >= 24 bits and >= 48 kHz
        // High Quality - compress
        if(isDSD(tag)) {
            return context.getColor(R.color.quality_hd);
        }else if(isHiRes(tag)) {
            return context.getColor(R.color.quality_hd);
        }else if(isPCM24Bits(tag)) {
            return context.getColor(R.color.quality_h24bits);
        }else if(isLossless(tag) || isMQA(tag)){
            return context.getColor(R.color.quality_sd);
        }else {
            return context.getColor(R.color.quality_unknown);
        }
    }

    public static boolean isPCM24Bits(Track tag) {
        return ( !isLossy(tag) && (tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD));
    }

    public static boolean isPCM(Track tag) {
        return ( !isLossy(tag) && (tag.getAudioBitsDepth() >= 16));
    }

    public static boolean isDSD(@UnknownNullability Track tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isDSD64(Track tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isDSD256(Track tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isHiRes(@UnknownNullability Track tag) {
        // > 24/96
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ((tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD) && (tag.getAudioSampleRate() >= QUALITY_SAMPLING_RATE_96));
    }

    public static boolean isHiRes48(@UnknownNullability Track tag) {
        // > 24/96
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ((tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD) && (tag.getAudioSampleRate() < QUALITY_SAMPLING_RATE_96));
    }

    public static boolean isCDQuality(@UnknownNullability Track tag) {
        return (isLossless(tag) && tag.getAudioBitsDepth() == Constants.QUALITY_BIT_CD && tag.getAudioSampleRate() == QUALITY_SAMPLING_RATE_44);
    }

    public static String getFormattedSubtitle(Track tag) {
        String album = StringUtils.trimTitle(tag.getAlbum());
        String artist = StringUtils.trimTitle(tag.getArtist());
        if (isEmpty(artist)) {
            artist = StringUtils.trimTitle(tag.getAlbumArtist());
        }
        if (isEmpty(album) && isEmpty(artist)) {
            return StringUtils.UNKNOWN_CAP + StringUtils.SEP_SUBTITLE + StringUtils.UNKNOWN_CAP;
        } else if (isEmpty(album)) {
            return artist;
        } else if (isEmpty(artist)) {
            return StringUtils.UNKNOWN_CAP + StringUtils.SEP_SUBTITLE + album;
        }
        return StringUtils.truncate(artist, 40, StringUtils.TruncateType.SUFFIX) + StringUtils.SEP_SUBTITLE + album;
    }

    public static boolean isLossless(@UnknownNullability Track tag) {
        return (isFLACFile(tag) || isAIFFile(tag) || isWavFile(tag) || isALACFile(tag)) && !isHiRes(tag) && !isMQA(tag);
    }

    public static boolean isLossy(Track tag) {
        return isMPegFile(tag) || isAACFile(tag);
    }

    public static String getDynamicRangeScore(Track tag) {
        String text;
        if(tag.getDrScore()==0.00) {
            text = "";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDrScore());
        }

        return text;
    }

    public static String getDynamicRange(Track tag) {
        String text;
        if(tag.getDynamicRange()==0.00) {
            text = "";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDynamicRange());
        }

        return text;
    }

    public static String getDynamicRangeAsString(Track tag) {
        String text;
        if(tag.getDynamicRange()==0.00) {
            text = "";
        }else {
           // text = String.format(Locale.US, "%.2f dB", tag.getDynamicRange());
            text = String.format(Locale.US, "%.0f dB", tag.getDynamicRange());
        }

        return text;
    }


    public static int getRating(Track tag) {
        if(PlaylistRepository.isInPlaylist(tag)) {
            return 5;
        }

        return 0;
    }

    public static boolean isOnDownloadDir(Track tag) {
       // return !tag.isMusicManaged();
        return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    public static String getDefaultAlbum(Track tag) {
        // if album empty, add single
        String defaultAlbum;
        if(isEmpty(tag.getAlbum()) && !isEmpty(tag.getArtist())) {
            defaultAlbum = getFirstArtist(tag.getArtist())+" - "+Constants.DEFAULT_ALBUM_TEXT; //getFirstArtist(tag.getArtist())+" - Single";
        }else {
            defaultAlbum = trimToEmpty(tag.getAlbum());
        }
        return defaultAlbum;
    }

    public static String getFirstArtist(String artist) {
        if(artist.indexOf(";")>0) {
            return artist.substring(0,artist.indexOf(";"));
       // }else if(artist.indexOf(",")>0) {
       //     return artist.substring(0,artist.indexOf(","));
       // }else if(artist.indexOf("-")>0) {  // some artist name contain -
       //     return artist.substring(0,artist.indexOf("-"));
       // }else if(artist.indexOf("&")>0) {
        //    return artist.substring(0,artist.indexOf("&"));
        }
        return artist;
    }

    @Deprecated
    public static int getStatusColor(String status) {
        if(status.toLowerCase().contains("upsampled")) return 0xFFF1C40F; // Yellow/Orange
        if(status.contains("Hi-Res")) return 0xFF2ECC71; // Green
        if(status.toLowerCase().contains("lossy transcode")) return 0xFFE74C3C; // Red
        return 0xFF3498DB; // Blue
        //return 0xFFE74C3C; // Red
       /* switch (status) {
            case "24-bit Hi-Res": return 0xFF2ECC71; // Green
            case "CD Quality":    return 0xFF3498DB; // Blue
            case "Fake Lossless": return 0xFFE74C3C; // Red
            default:              return 0xFFF1C40F; // Yellow/Orange
        } */
    }

    public static int getCodecColor(Context context, Track tag) {
        if(isLossy(tag)) {
            return context.getColor(R.color.mm_label_lossy);
        }

        return context.getColor(R.color.mm_label_lossless);
    }

    public static String getEncodingTypeShort(@UnknownNullability Track tag) {
        if(TagUtils.isDSD(tag)) {
            return Constants.LEGEND_DSD;
        }else if(isMQA(tag)) {
            return Constants.LEGEND_MQA;
        }else if(isHiRes(tag)) {
            return Constants.LEGEND_HIRES;
        }else if(isPCM24Bits(tag)) {
            return Constants.LEGEND_STUDIO;
        }else if(isLossless(tag)) {
            return Constants.LEGEND_CD;
        }else {
            return LEGEND_LOSSY;
        }
    }

    public static boolean isWavFile(@UnknownNullability Track musicTag) {
        return (Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isFLACFile(@UnknownNullability Track musicTag) {
        return (Constants.MEDIA_ENC_FLAC.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isDSDFile(Track tag) {
        return (Constants.MEDIA_ENC_DSF.equalsIgnoreCase(tag.getAudioEncoding()) ||
                Constants.MEDIA_ENC_DFF.equalsIgnoreCase(tag.getAudioEncoding()) );
    }

    public static boolean isMp4File(Track tag) {
        // m4a, mov, ,p4
        return (Constants.MEDIA_ENC_AAC.equalsIgnoreCase(tag.getAudioEncoding()) ||
                Constants.MEDIA_ENC_ALAC.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isMPegFile(@UnknownNullability Track tag) {
        // mp3
        return (Constants.MEDIA_ENC_MPEG.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isALACFile(@UnknownNullability Track tag) {
        // m4a, mov, ,p4
        return (Constants.MEDIA_ENC_ALAC.equalsIgnoreCase(tag.getAudioEncoding()));
    }


    public static boolean isAIFFile(@UnknownNullability Track tag) {
        // aif, aiff
        return (Constants.MEDIA_ENC_AIFF.equalsIgnoreCase(tag.getAudioEncoding()) || Constants.MEDIA_ENC_AIFF_ALT.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isAACFile(@UnknownNullability Track musicTag) {
        return Constants.MEDIA_ENC_AAC.equalsIgnoreCase(musicTag.getAudioEncoding());
    }

    public static int getChannels(Track tag) {
        String chStr = tag.getAudioChannels();
        return 2;
    }

    // Helper to determine if a format is lossless (for audiophile renderers)
    public static boolean isLosslessFormat(Track tag) {
        String format = tag.getFileType() != null ? tag.getFileType().toLowerCase() : "";
        String codec = tag.getAudioEncoding() != null ? tag.getAudioEncoding().toLowerCase() : "";
        String path = tag.getPath().toLowerCase();

        return format.contains("flac") || format.contains("alac") || format.contains("aiff") ||
                format.contains("wav") || format.contains("dsd") || format.contains("dff") ||
                codec.contains("flac") || codec.contains("alac") || codec.contains("pcm") ||
                path.endsWith(".flac") || path.endsWith(".alac") || path.endsWith(".aiff") ||
                path.endsWith(".wav") || path.endsWith(".dsd") || path.endsWith(".dff") ||
                path.endsWith(".dsf");
    }

    /**
     * Determines and formats the audio quality for a given song.
     * The logic prioritizes specific, high-fidelity formats over generic ones.
     *
     * @param song The MusicTag object representing the song.
     * @return A descriptive string for the audio quality, suitable for music lovers.
     */
    public static String getQualityIndicator(@UnknownNullability Track song) {
        // 1. Check for DSD, a single-bit format (highest priority)
        if (song.getAudioBitsDepth() == 1) {
            return LEGEND_DSD; //"DSD";
        }

        // 2. Check for MQA and include the original sample rate
        String mqaIndicator = trimToEmpty(song.getQualityInd());
        if ("MQA".equalsIgnoreCase(mqaIndicator) || "MQA Studio".equalsIgnoreCase(mqaIndicator)) {
            // We now include the original sample rate for MQA
            return LEGEND_MQA; //"MQA"; // mqaIndicator;
        }

        // 3. Check for Hi-Res Audio (any format with bit depth > 16 or sample rate > 44.1kHz)
        if (song.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD && song.getAudioSampleRate() >= QUALITY_SAMPLING_RATE_96) {
            return LEGEND_HIRES; //"HR"; //""Hi-Res";
        }

        // 4. Check for Lossy formats (this should be the final check)
        if (isLossy(song)) {
            // Explicitly label the audio as "Lossy" instead of an empty string,
            // which provides more valuable information to the user.
            return LEGEND_LOSSY; //"LC"; // lossy compress codecs
        }

        // 5. Check for CD-Extended Quality (24-bit / 44.1 kHz)
        if(isPCM24Bits(song)) {
            return LEGEND_STUDIO;
        }

        //6. else all 16 bit is CD quality
        return  LEGEND_CD; // "SQ"; //""CD";
    }

    public static String getQualityNote(String codec) {
        if(codec.equals(Constants.TITLE_CD_QUALITY)) {
            return "16–bit • 44.1–48 kHz";
        }else  if(codec.equals(Constants.TITLE_DSD)) {
            return "DSD64 (2.8MHz) to DSD512 (22.5MHz)";
        }else  if(codec.equals(Constants.TITLE_MQA_MASTER_QUALITY)) {
            return "Unfolds to 24-bit / 352.8 kHz";
        }else  if(codec.equals(Constants.TITLE_CD_EXT_QUALITY)) {
            return "24-bit • 44.1–88.2 kHz";
        }else  if(codec.equals(Constants.TITLE_HIRES_QUALITY)) {
            return "24-bit • 96–384 kHz";
        }else  if(codec.equals(Constants.TITLE_HIGH_QUALITY)) {
            return "MP3 • AAC • OGG";
        }

        return "";
    }

    public static int getCodecBgColor(Context mContext, @NotNull Track tag) {
            if(isLossy(tag)) {
                return mContext.getColor(R.color.mm_label_lossy_bg);
            }

            return mContext.getColor(R.color.mm_label_lossless_bg);
    }

    public static String formatResolution(int bitPerSampling, long samplingRate, long altSamplingRate) {
        String bps = StringUtils.formatAudioSampleRate(samplingRate, true);
        if(altSamplingRate >0 && samplingRate != altSamplingRate) {
            bps = bps + " ("+StringUtils.formatAudioSampleRate(altSamplingRate, true)+")";
        }

        return StringUtils.formatAudioBitsDepth(bitPerSampling)
                + SYMBOL_ENC_SEP
                + bps;
    }

    public static String formatCodec(@NotNull Track tag) {
         String codec = tag.getAudioEncoding();
         return trimToEmpty(codec).toUpperCase();
    }
}
