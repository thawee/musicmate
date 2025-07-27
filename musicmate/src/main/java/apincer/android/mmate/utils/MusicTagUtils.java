package apincer.android.mmate.utils;

import static apincer.android.mmate.Constants.QUALITY_GOOD;
import static apincer.android.mmate.Constants.QUALITY_RECOMMENDED;
import static apincer.android.mmate.Constants.QUALITY_SAMPLING_RATE_96;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;

public class MusicTagUtils {
    private static final String TAG = "MusicTagUtils";

    public static String getBPSAndSampleRate(MusicTag tag) {
        long sampleRate = tag.getAudioSampleRate();
        if(isMQA(tag)) {
            sampleRate = tag.getMqaSampleRate();
        }
        return String.format("%s/%s", tag.getAudioBitsDepth(), StringUtils.formatAudioSampleRate(sampleRate,false));
    }

    public static boolean isMQAStudio(MusicTag tag) {
        return trimToEmpty(tag.getMqaInd()).contains("MQA Studio");
    }

    public static boolean isMQA(MusicTag tag) {
        return trimToEmpty(tag.getMqaInd()).contains("MQA");
    }

    public static int getResolutionColor(Context context, MusicTag tag) {
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

    public static Drawable getResolutionBackground(Context context, MusicTag tag) {
        // DSD - DSD
        // Hi-Res Lossless - >= 24 bits and >= 48 kHz
        // Lossless - >= 24 bits and >= 48 kHz
        // High Quality - compress
        if(isDSD(tag) || isHiRes(tag)) {
            return ContextCompat.getDrawable(context, R.drawable.backgound_resolution_hd);
        }else if(isPCM24Bits(tag)) {
            return ContextCompat.getDrawable(context, R.drawable.backgound_resolution_24bits);
        }else if(isLossless(tag) || isMQA(tag)){
            return ContextCompat.getDrawable(context, R.drawable.backgound_resolution_sd);
        }else {
            return ContextCompat.getDrawable(context, R.drawable.backgound_resolution_unknown);
        }
    }

    public static Drawable getDynamicRangeDbBackground(Context context, MusicTag tag) {
        double drDb = tag.getDynamicRange();
        boolean perfect = false;
        if(tag.getAudioBitsDepth()==16) {
            perfect = (drDb>=96.00);
        }else if(tag.getAudioBitsDepth()==24) {
            perfect = (drDb>=144.00);
        }
        if(perfect) {
            return ContextCompat.getDrawable(context, R.drawable.backgound_drdb_perfect);
        }else {
            return ContextCompat.getDrawable(context, R.drawable.backgound_drdb_normal);
        }
    }


    public static int getEncodingColor(Context context, MusicTag tag) {
        /* IFI DAC v2
        yellow - pcm 44.1/48
        white  - pcm >= 88.2
        cyan   - dsd 64/128
        red    - dsd 256
        green  - MQA
        blue   - MQA Studio
        */

        if(isMQAStudio(tag)){
            return context.getColor(R.color.resolution_mqa_studio);
        }else if(isMQA(tag)){
            return context.getColor(R.color.resolution_mqa);
        }else if(isHiRes(tag)) {
            return context.getColor(R.color.resolution_pcm_96);
        } else if(isDSD64(tag)) {
                return context.getColor(R.color.resolution_dsd_64_128);
        } else if(isDSD256(tag)) {
                return context.getColor(R.color.resolution_dsd_256);
        }else {
            // 44.1 - 48
            return context.getColor(R.color.resolution_pcm_44_48);
        }
    }

    public static int getFileEncodingColor(Context context, MusicTag tag) {
        /* IFI DAC v2
        yellow - pcm 44.1/48
        white  - pcm >= 88.2

        cyan   - lossy
        red    - lossless
        green  - MQA
        blue   - MQA Studio
        */

        if(isMQAStudio(tag)){
            return context.getColor(R.color.resolution_mqa_studio);
        }else if(isMQA(tag)){
            return context.getColor(R.color.resolution_mqa);
        }else if(isDSD64(tag) || isDSD256(tag)) {
            return context.getColor(R.color.resolution_dsd);
        } else if(isLossless(tag) || isHiRes(tag)) {
            return context.getColor(R.color.resolution_pcm_96);
        }else {
            // 44.1 - 48
            return context.getColor(R.color.resolution_lossy);
        }
    }

    public static boolean isPCM24Bits(MusicTag tag) {
        return ( !isLossy(tag) && (tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD));
    }

    public static boolean isPCM(MusicTag tag) {
        return ( !isLossy(tag) && (tag.getAudioBitsDepth() >= 16));
    }

    public static boolean isDSD(MusicTag tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isDSD64(MusicTag tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isDSD256(MusicTag tag) {
        return tag.getAudioBitsDepth()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

    public static boolean isHiRes(MusicTag tag) {
        // > 24/96
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ((tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD) && (tag.getAudioSampleRate() >= QUALITY_SAMPLING_RATE_96));
    }

    public static String getFormattedTitle(Context context, MusicTag tag) {
        String title =  trimToEmpty(tag.getTitle());
        if(Settings.isShowTrackNumber(context)) {
            String track = trimToEmpty(tag.getTrack());
            if(track.startsWith("0")) {
                track = track.substring(1);
            }
            if(track.indexOf("/")>0) {
                track = track.substring(0,track.indexOf("/"));
            }
            if(!isEmpty(track)) {
                title = track + StringUtils.SEP_TITLE + title;
            }
        }
        return title;
    }

    public static String getFormattedSubtitle(MusicTag tag) {
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
        return StringUtils.truncate(artist, 40) + StringUtils.SEP_SUBTITLE + album;
    }

    public static boolean isLossless(MusicTag tag) {
        return (isFLACFile(tag) || isAIFFile(tag) || isWavFile(tag) || isALACFile(tag)) && !isHiRes(tag) && !isMQA(tag);
    }

    public static boolean isLossy(MusicTag tag) {
        return isMPegFile(tag) || isAACFile(tag);
    }

    public static String getDynamicRangeScore(MusicTag tag) {
        String text;
        if(tag.getDynamicRangeScore()==0.00) {
            text = "";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDynamicRangeScore());
        }

        return text;
    }

    public static String getDynamicRange(MusicTag tag) {
        String text;
        if(tag.getDynamicRange()==0.00) {
            text = "--";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDynamicRange());
        }

        return text;
    }

    public static String getDynamicRangeAsString(MusicTag tag) {
        String text;
        if(tag.getDynamicRange()==0.00) {
            text = "";
        }else {
           // text = String.format(Locale.US, "%.2f dB", tag.getDynamicRange());
            text = String.format(Locale.US, "%.0f dB", tag.getDynamicRange());
        }

        return text;
    }

    public static int getDRScoreColor(Context context, int drValue) {
        if (drValue == 0) return ContextCompat.getColor(context, R.color.grey600);
        else return ContextCompat.getColor(context, R.color.grey200);
       /* if (drValue == 0) return ContextCompat.getColor(context, R.color.dr_no_trans);
        else if (drValue < 7) return ContextCompat.getColor(context, R.color.red_light);
        else if (drValue < 10) return ContextCompat.getColor(context, R.color.oranges_salmon);
        else if (drValue < 13) return ContextCompat.getColor(context, R.color.greens_lime);
        else return ContextCompat.getColor(context, R.color.purples_magenta); */
    }

    public static Drawable getDRScoreBackgroundColor(Context context, int drValue) {
        if (drValue == 0) return ContextCompat.getDrawable(context, R.drawable.shape_background_dr);
        else if (drValue < 8) return ContextCompat.getDrawable(context, R.drawable.shape_background_dr_low);
        else if (drValue < 13) return ContextCompat.getDrawable(context, R.drawable.shape_background_dr_medium);
        else return ContextCompat.getDrawable(context, R.drawable.shape_background_dr_high);
    }

    public static int getSourceRescId(String letter) {
        //String letter = item.getSource();
        if (letter.equalsIgnoreCase(Constants.PUBLISHER_JOOX)) {
            return R.drawable.icon_joox;
        } else if (letter.equalsIgnoreCase(Constants.PUBLISHER_QOBUZ)) {
            return R.drawable.icon_qobuz;
        } else if (letter.equalsIgnoreCase(Constants.MEDIA_TYPE_CD)) { // || letter.equalsIgnoreCase(Constants.SRC_CD_LOSSLESS)) {
            return R.drawable.icon_cd;
        } else if (letter.equalsIgnoreCase(Constants.MEDIA_TYPE_SACD)) {
            return R.drawable.icon_sacd;
        } else if (letter.equalsIgnoreCase(Constants.MEDIA_TYPE_VINYL)) {
            return R.drawable.icon_vinyl;
        } else if (letter.equalsIgnoreCase(Constants.PUBLISHER_SPOTIFY)) {
            return R.drawable.icon_spotify;
        } else if (letter.equalsIgnoreCase(Constants.PUBLISHER_TIDAL)) {
            return R.drawable.icon_tidal;
        } else if (letter.equalsIgnoreCase(Constants.PUBLISHER_APPLE)) {
            return R.drawable.icon_itune;
        } else if (letter.equalsIgnoreCase(Constants.PUBLISHER_YOUTUBE)) {
            return R.drawable.icon_youtube;
        }

        return -1;
    }

    public static int getRating(MusicTag tag) {
        String label1 = tag.getMediaQuality();
        if(Constants.QUALITY_AUDIOPHILE.equals(label1)) {
            return 5;
        }else if(QUALITY_RECOMMENDED.equals(label1)) {
            return 4;
        }else if (QUALITY_GOOD.equals(label1)) {
            return 3;
        }else if(Constants.QUALITY_BAD.equals(label1)) {
            return 1;
        }else {
            return 0;
        }
    }

    public static boolean isOnDownloadDir(MusicTag tag) {
        return !tag.isMusicManaged();
       // return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    public static String getDefaultAlbum(MusicTag tag) {
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

    public static String getEncodingTypeShort(MusicTag tag) {
        if(tag.isDSD()) {
            return Constants.TITLE_DSD_SHORT;
        }else if(isMQA(tag)) {
            return Constants.TITLE_MQA_SHORT;
        }else if(isHiRes(tag)) {
            return Constants.TITLE_HIRES_SHORT;
        }else if(isLossless(tag)) {
            return Constants.TITLE_HIFI_LOSSLESS_SHORT;
        }else {
            return Constants.TITLE_HIGH_QUALITY_SHORT;
        }
    }

    public static boolean isWavFile(MusicTag musicTag) {
        return (Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isFLACFile(MusicTag musicTag) {
        return (Constants.MEDIA_ENC_FLAC.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isDSDFile(MusicTag tag) {
        return (Constants.MEDIA_ENC_DSF.equalsIgnoreCase(tag.getAudioEncoding()) ||
                Constants.MEDIA_ENC_DFF.equalsIgnoreCase(tag.getAudioEncoding()) );
    }

    public static boolean isMp4File(MusicTag tag) {
        // m4a, mov, ,p4
        return (Constants.MEDIA_ENC_AAC.equalsIgnoreCase(tag.getAudioEncoding()) ||
                Constants.MEDIA_ENC_ALAC.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isMPegFile(MusicTag tag) {
        // mp3
        return (Constants.MEDIA_ENC_MPEG.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isALACFile(MusicTag tag) {
        // m4a, mov, ,p4
        return (Constants.MEDIA_ENC_ALAC.equalsIgnoreCase(tag.getAudioEncoding()));
    }


    public static boolean isAIFFile(MusicTag tag) {
        // aif, aiff
        return (Constants.MEDIA_ENC_AIFF.equalsIgnoreCase(tag.getAudioEncoding()) || Constants.MEDIA_ENC_AIFF_ALT.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    public static boolean isManagedInLibrary(Context context, MusicTag tag) {
        String path = FileRepository.newInstance(context).buildCollectionPath(tag, true);
        return StringUtils.compare(path, tag.getPath());
    }

    public static boolean isAACFile(MusicTag musicTag) {
        return Constants.MEDIA_ENC_AAC.equalsIgnoreCase(musicTag.getAudioEncoding());
    }

    public static int getChannels(MusicTag tag) {
        String chStr = tag.getAudioChannels();
        return 2;
    }

    // Helper to determine if a format is lossless (for audiophile renderers)
    public static boolean isLosslessFormat(MusicTag tag) {
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
}
