package apincer.android.mmate.utils;

import static apincer.android.mmate.Constants.MIN_SPL_16BIT_IN_DB;
import static apincer.android.mmate.Constants.MIN_SPL_24BIT_IN_DB;
import static apincer.android.mmate.Constants.QUALITY_SAMPLING_RATE_96;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;

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
        return String.format("%s / %s", tag.getAudioBitsDepth(), StringUtils.formatAudioSampleRate(sampleRate,false));
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

    public static boolean isPCM24Bits(MusicTag tag) {
        return ( !isLossy(tag) && (tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD));
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
            if(!StringUtils.isEmpty(track)) {
                title = track + StringUtils.SEP_TITLE + title;
            }
        }
        return title;
    }

    public static String getFormattedSubtitle(MusicTag tag) {
        String album = StringUtils.trimTitle(tag.getAlbum());
        String artist = StringUtils.trimTitle(tag.getArtist());
        if (StringUtils.isEmpty(artist)) {
            artist = StringUtils.trimTitle(tag.getAlbumArtist());
        }
        if (StringUtils.isEmpty(album) && StringUtils.isEmpty(artist)) {
            return StringUtils.UNKNOWN_CAP + StringUtils.SEP_SUBTITLE + StringUtils.UNKNOWN_CAP;
        } else if (StringUtils.isEmpty(album)) {
            return artist;
        } else if (StringUtils.isEmpty(artist)) {
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



    @Deprecated
    public static String getAlbumArtistOrArtist(MusicTag tag) {
        String albumArtist = StringUtils.trimTitle(tag.getAlbumArtist());
        if (StringUtils.isEmpty(albumArtist)) {
            albumArtist = StringUtils.trimTitle("["+tag.getArtist()+"]");
        }
        if (StringUtils.isEmpty(albumArtist)) {
            return "N/A";
        } {
            return albumArtist;
        }
    }

    @Deprecated
    public static String getTrackDRandGainString(MusicTag tag) {
        String text;
        if(tag.getDynamicRangeScore()==0.00) {
            text = " - ";
        }else {
            text = String.format(Locale.US, "DR%.0f", tag.getDynamicRangeScore());
        }
        text = text + "/";

        double gain = tag.getTrackRG();
       // double gain = tag.getTrackLoudness();

        if(gain ==0.00) {
            text = text + " - ";
        }else if (gain > 0.00){
            text = text + String.format(Locale.US, "+%.2f", gain);
        }else {
            text = text + String.format(Locale.US, "%.2f", gain);
        }

        return text;
    }

    @Deprecated
    public static String getTrackReplayGainString(MusicTag tag) {
        String text = "";
        double gain = tag.getTrackRG();
        // double gain = tag.getTrackLoudness();

        if(gain ==0.00) {
            text = text + " - ";
        }else if (gain > 0.00){
            text = text + String.format(Locale.US, "+%.2f", gain);
        }else {
            text = text + String.format(Locale.US, "%.2f", gain);
        }

        return text;
    }

    public static String getTrackDRScore(MusicTag tag) {
        String text;
        if(tag.getDynamicRangeScore()==0.00) {
            text = "-";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDynamicRangeScore());
        }

        return text;
    }

    public static String getDynamicRangeAsString(MusicTag tag) {
        String text;
        if(tag.getDynamicRange()==0.00) {
            text = " - dB";
        }else {
           // text = String.format(Locale.US, "%.2f dB", tag.getDynamicRange());
            text = String.format(Locale.US, "%.0f dB", tag.getDynamicRange());
        }

        return text;
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

    public static boolean isOnDownloadDir(MusicTag tag) {
        return !tag.isMusicManaged();
       // return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    @Deprecated
    public static String getTrackQuality(MusicTag tag) {
        if(tag.isDSD()) {
            return Constants.TITLE_DSD_AUDIO;
        }else if(isMQAStudio(tag)) {
            return Constants.TITLE_MASTER_STUDIO_AUDIO;
        }else if(isMQA(tag)) {
            return Constants.TITLE_MASTER_AUDIO;
        }else if(isHiRes(tag)) {
            return Constants.TITLE_HIRES;
        }else if(isLossless(tag)) {
            return Constants.TITLE_HIFI_LOSSLESS;
        }else {
            return Constants.TITLE_HIGH_QUALITY;
        }
        //Hi-Res Audio
        //Lossless Audio
        //High Quality
        //DSD
    }

    @Deprecated
    public static String getTrackQualityDetails(MusicTag tag) {
        if(tag.isDSD()) {
            return "Enjoy rich music which the detail and wide range of the music, its warm tone is very enjoyable.";
        }else if(isMQAStudio(tag)) {
            return "Enjoy the original recordings, directly from mastering engineers, producers or artists to their listeners.";
        }else if(isMQA(tag)) {
            return "Enjoy the original recordings, directly from the master recordings, in the highest quality.";
        }else if(isHiRes(tag)) {
            return "Enjoy rich music which reproduces fine details of musical instruments.";
        }else if(isLossless(tag)) {
            return "Enjoy music which reproduce details of music smooth as CD quality that you can hear.";
        }else {
            return "Enjoy music which compromise between data usage and sound fidelity.";
        }
    }

    public static String getDefaultAlbum(MusicTag tag) {
        // if album empty, add single
        String defaultAlbum;
        if(StringUtils.isEmpty(tag.getAlbum()) && !StringUtils.isEmpty(tag.getArtist())) {
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

    /*
    public static Bitmap createLoudnessIcon(Context context,  MusicTag tag) {
        int width = 356; //340; // for 56x16,  89x24
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
       // int darkGreyColor = context.getColor(R.color.grey900);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.material_color_blue_grey_100);
        String lra = String.format(Locale.getDefault(),"%.1f", tag.getTrackRange());
        String il= String.format(Locale.getDefault(),"%.1f", tag.getTrackLoudness());

        String rg =   String.format(Locale.getDefault(),"%.1f", tag.getTrackRG());

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        //int cornerRadius = 8;
        int overflowRadius = 12;
        //int bottomMargin = 18;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint paint;
        RectF rectangle;

        // draw border grey box, black color block inside
        rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                (myCanvas.getHeight()) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        padding = 4;
        rectangle = new RectF(
                padding-2, // Left
                padding, // Top
                myCanvas.getWidth()-padding-2, // Right
                (myCanvas.getHeight()-padding) // Bottom
        );

        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw grey box
        padding = 8;
        int topPadding = 16;
        rectangle = new RectF(
                padding,  // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - topPadding) // Bottom
        );

        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw LRA (DR) box - border
        padding =8;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);

        int x1 = (bounds.width()/3)+4;
        int x2=((bounds.width()/3) * 2)+12;
        int y1 = padding;
        int y2 =bounds.height()-padding;

        Path path = new Path();
        path.moveTo(x1, y1); //x1,y1 - top left
        path.lineTo(x2, y1);   //x2,y1 - top right
        path.lineTo(x2-4, y2);   //x2,y2 - bottom right
        path.lineTo(x1-4, y2); //x1,y2 - bottom left
        path.lineTo(x1, y1); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        //  //draw LRA (DR) - background
        padding =4;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(qualityColor);
        paint.setStyle(Paint.Style.FILL);
        x1 = x1+padding;
        x2 = x2-padding;
        y1 = y1+padding;
        y2 = y2-padding;

        path = new Path();
        path.moveTo(x1, y1); //x1,y1 - top left
        path.lineTo(x2, y1);   //x2,y1 - top right
        path.lineTo(x2-4, y2);   //x2,y2 - bottom right
        path.lineTo(x1-4, y2); //x1,y2 - bottom left
        path.lineTo(x1, y1); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 50; //28;

        // draw integrated loudness text, black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(il, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height()); // / 2.5f);
       // float mPositionY= (float) (bounds.exactCenterY() *0.3);
        float mPositionY = bounds.exactCenterY();
        myCanvas.drawText(il,
                bounds.left + (bounds.exactCenterX() / 3) + 2, //left
               // (float) (bounds.bottom-textMathRect.height())-20, // top
                bounds.top + mPositionY + 16, // top
                mLetterPaint);

        // draw true peak text, black color
        letterTextSize = 50; //28;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        //mLetterPaint.getTextBounds(tp, 0, 1, textMathRect);
        mLetterPaint.getTextBounds(rg, 0, 1, textMathRect);
        mPositionY = bounds.exactCenterY();
        //myCanvas.drawText(tp,
        myCanvas.drawText(rg,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()*0.66)), //left
                bounds.top + mPositionY + 16, // top
                mLetterPaint);

        // draw LRA (DR) text, black color
        letterTextSize = 56;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(lra, 0, 1, textMathRect);
        mLetterTop = textMathRect.height();
        myCanvas.drawText(lra,
                bounds.exactCenterX() +2, //left
                bounds.top + mLetterTop + 19, // top
                mLetterPaint);

        return myBitmap;
    } */
/*
    public static Bitmap createLoudnessIcon1(Context context,  MusicTag tag) {
        int width = 340; // for xx
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey900);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.grey200);
        String lra = StringUtils.trim(tag.getTrackRange(),"--");
        String il= StringUtils.trim(tag.getTrackLoudness(),"--");
        String tp= StringUtils.trim(tag.getTrackTruePeek(),"--");
        if(!"--".equalsIgnoreCase(il)) {
            try {
                double intg =NumberFormat.getInstance().parse(il).doubleValue();
                if(intg < -23.0) {
                    // for studio -23 or less
                    qualityColor = context.getColor(R.color.grey300);
                }else if (intg <= -11.0) {
                    // for streaming -11 to -23
                    // spotify, -14 lufs
                    // apple music, -16 lufs
                    qualityColor = context.getColor(R.color.grey400);
                }else {
                    qualityColor = context.getColor(R.color.warningColor);
                }
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 8;
        int overflowRadius = 12;
        //int bottomMargin = 18;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint paint;
        RectF rectangle;

        // draw border grey box, black color block inside
        rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                (myCanvas.getHeight()) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        padding = 4;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth()-padding, // Right
                (myCanvas.getHeight()-padding) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw grey box
        int jointCornerRadius = 14;
        padding = 12;
        int topPadding = 16;
        rectangle = new RectF(
                padding,  // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - topPadding) // Bottom
        );

        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw Integrated grey box
        padding =8;
        rectangle = new RectF(
                (myCanvas.getWidth()/3)-12, // Left
                padding, // Top
                (float) ((myCanvas.getWidth()/3) * 2)+12, // Right
                (float) (myCanvas.getHeight() - padding) // Bottom
        );
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(darkGreyColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        padding =12;
        rectangle = new RectF(
                (myCanvas.getWidth()/3)-8, // Left
                padding, // Top
                (float) ((myCanvas.getWidth()/3) * 2)+8, // Right
                (float) (myCanvas.getHeight() - padding) // Bottom
        );
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(qualityColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 50; //28;

        // draw LRA text, black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(lra, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height()); // / 2.5f);
        float mPositionY= (float) (bounds.exactCenterY() *0.3);
        myCanvas.drawText(lra,
                bounds.left+ (bounds.exactCenterX()/3), //left
                bounds.top+mPositionY +mLetterTop+8, //top
                mLetterPaint);

        // draw true peak text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(tp, 0, 1, textMathRect);
        // mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY = bounds.exactCenterY();
        myCanvas.drawText(tp,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()*0.66)), //left
                bounds.top + mPositionY + 16, // top
                mLetterPaint);

        // draw integrated loudness text, black color
        letterTextSize = 56;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(il, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY= bounds.exactCenterY();
        myCanvas.drawText(il,
                bounds.exactCenterX() -4, //left
                bounds.top + mPositionY + mLetterTop + 16, // top
                mLetterPaint);

        return myBitmap;
    } */

    public static String getEncodingType(MusicTag tag) {
        if(tag.isDSD()) {
            return Constants.TITLE_DSD;
        }else if(isMQA(tag)) {
                  return Constants.TITLE_MQA;
        }else if(isHiRes(tag)) {
            return Constants.TITLE_HIRES;
        }else if(isLossless(tag)) {
            return Constants.TITLE_HIFI_LOSSLESS;
        }else {
            return Constants.TITLE_HIGH_QUALITY;
        }
    }

    public static boolean isWavFile(MusicTag musicTag) {
        return (Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isFLACFile(MusicTag musicTag) {
        return (Constants.MEDIA_ENC_FLAC.equalsIgnoreCase(musicTag.getAudioEncoding()));
    }

    public static boolean isDSDFile(String path) {
        return (path.endsWith("."+Constants.FILE_EXT_DSF));
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
        return (Constants.MEDIA_ENC_AIFF.equalsIgnoreCase(tag.getAudioEncoding()));
    }

    /*
    public static boolean isUpScaled(MusicTag tag) {
        if(tag.getDynamicRange()==0) return false;
        if(tag.getAudioBitsDepth() <=16) {
            // less than 90 is upscaled
            return tag.getDynamicRange() <= MIN_SPL_16BIT_IN_DB; // SNR 96.33 db
        }else  if(tag.getAudioBitsDepth() >=24) {
            // less than 120 is upscaled
            return tag.getDynamicRange() <= MIN_SPL_24BIT_IN_DB; // SNR 144.49 db
       // }else  if(tag.getAudioBitsDepth() ==32) {
            // less than 190 is upscaled
        //    return tag.getMeasuredDR() <= 190; // SNR 192.66 db
        }
        return false;
    } */

    /*
    public static boolean isBadUpScaled(MusicTag tag) {
        if(tag.getDynamicRange()==0) return false;
        if(tag.getAudioBitsDepth() <=16) {
            // less than 90 is upscaled
            return tag.getDynamicRange() <= SPL_8BIT_IN_DB; // SNR 96.33 db
        }else  if(tag.getAudioBitsDepth() >=24) {
            // less than 120 is upscaled
            return tag.getDynamicRange() <= SPL_16BIT_IN_DB; // SNR 144.49 db
            // }else  if(tag.getAudioBitsDepth() ==32) {
            // less than 190 is upscaled
            //    return tag.getMeasuredDR() <= 190; // SNR 192.66 db
        }
        return false;
    } */

    public static String getExtension(MusicTag tag) {
        String ext = tag.getFileFormat();
        if("wave".equals(ext)) {
            ext = "wav";
        } else if("mpeg".equals(ext)) {
            ext = "mp3";
        } else if("aac".equals(ext)) {
            ext = "m4a";
        } else if("aiff".equals(ext)) {
            ext = "aif";
        } else if("alac".equals(ext)) {
            ext = "m4a";
        }
        return ext;
    }

    public static boolean isISaanPlaylist(MusicTag tag) {
       // return ("Luk Thung".equalsIgnoreCase(tag.getGenre()) ||
       //         "Mor Lum".equalsIgnoreCase(tag.getGenre()));
        return ("Mor Lum".equalsIgnoreCase(tag.getGenre()));

       //  return (trimToEmpty(tag.getGenre()).toUpperCase().contains("ISAAN") ||
       //          "Mor Lum".equalsIgnoreCase(tag.getGenre()));
    }

    public static boolean isBaanThungPlaylist(MusicTag tag) {
        return ("Luk Thung".equalsIgnoreCase(tag.getGenre()));
       // return ("Thai Indie".equalsIgnoreCase(tag.getGenre()) ||
       //         "Luk Thung".equalsIgnoreCase(tag.getGenre()));
    }

    public static boolean isRelaxedPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping()).toUpperCase();
        String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (!isClassicPlaylist(tag)) && (grouping.contains("LOUNGE") ||
                genre.contains("ACOUSTIC"));
    }

    public static boolean isManagedInLibrary(Context context, MusicTag tag) {
        String path = FileRepository.newInstance(context).buildCollectionPath(tag, true);
        return StringUtils.compare(path, tag.getPath());
    }

    public static boolean isRelaxedThaiPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping()).toUpperCase();
        String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (!isClassicPlaylist(tag)) &&
                (grouping.equalsIgnoreCase("THAI LOUNGE") ||
                        (genre.equalsIgnoreCase("ACOUSTIC") &&
                                grouping.equalsIgnoreCase("THAI")));
    }

    public static boolean isRelaxedEnglishPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping()).toUpperCase();
        String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (!isClassicPlaylist(tag)) &&
                (grouping.equalsIgnoreCase("LOUNGE") ||
                        (genre.equalsIgnoreCase("ACOUSTIC") &&
                                grouping.equalsIgnoreCase("ENGLISH")));
    }

    public static boolean isClassicPlaylist(MusicTag tag) {
        String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (genre.contains("CLASSIC") ||
                genre.contains("INSTRUMENT") ||
                genre.contains("CONCERTOS"));
    }

    public static boolean isIndiePlaylist(MusicTag tag) {
        String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (genre.contains("INDIE"));
    }

    public static boolean isFinFinPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping());
        //String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return ((grouping.equalsIgnoreCase("English") ||
                grouping.equalsIgnoreCase("Thai")) &&
                !(isClassicPlaylist(tag) ||
                  isISaanPlaylist(tag) ||
                  isBaanThungPlaylist(tag) ||
                  isRelaxedPlaylist(tag))
        );
    }

    public static boolean isFinFinThaiPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping());
        //String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (grouping.equalsIgnoreCase("Thai") &&
                !(isClassicPlaylist(tag) ||
                        isISaanPlaylist(tag) ||
                        isBaanThungPlaylist(tag) ||
                        isRelaxedPlaylist(tag))
        );
    }

    public static boolean isFinFinEnglishPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping());
        //String genre = StringUtils.trimToEmpty(tag.getGenre()).toUpperCase();
        return (grouping.equalsIgnoreCase("English") &&
                !(isClassicPlaylist(tag) ||
                        isISaanPlaylist(tag) ||
                        isBaanThungPlaylist(tag) ||
                        isRelaxedPlaylist(tag))
        );
    }

    public static boolean isAACFile(MusicTag musicTag) {
        return Constants.MEDIA_ENC_AAC.equalsIgnoreCase(musicTag.getAudioEncoding());
    }
}
