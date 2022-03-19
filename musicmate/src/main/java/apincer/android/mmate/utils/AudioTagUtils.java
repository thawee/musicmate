package apincer.android.mmate.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.core.content.res.ResourcesCompat;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;

public class AudioTagUtils {

    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, int drawableId, int borderColor, int backgroundColor) {
        Bitmap icon = BitmapHelper.getBitmapFromVectorDrawable(context, drawableId);
        return createBitmapFromDrawable(context,width,height,icon, borderColor, backgroundColor);
    }

    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, Drawable drawable, int borderColor, int backgroundColor) {
        Bitmap icon = BitmapHelper.drawableToBitmap(drawable);
        return createBitmapFromDrawable(context,width,height,icon, borderColor, backgroundColor);
    }

    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, Bitmap icon, int borderColor, int backgroundColor) {
        //DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        //int width = displayMetrics.widthPixels;
        // Bitmap icon =  BitmapFactory.decodeResource(getResources(), drawableId);

        if(icon!=null) {
            if (width < icon.getWidth() || height < icon.getHeight()) {
                width = icon.getWidth() + (int) (icon.getWidth() * .1);
                height = icon.getHeight() + (int) (icon.getHeight() * .1);
            }
        }
        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(backgroundColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, 4,4, bgPaint);

        padding = 1;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );
        int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, 4,4, paint);


        //int letterTextSize = 20;
        //Typeface font =  ResourcesCompat.getFont(context, R.font.led_font);
        if(icon!=null) {
            Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            float centerX = (width - icon.getWidth()) * 0.5f;
            float centerY = (height - icon.getHeight()) * 0.5f;
            iconPaint.setAntiAlias(true);
            iconPaint.setFilterBitmap(true);
            iconPaint.setDither(true);
            myCanvas.drawBitmap(icon, centerX, centerY, iconPaint);
        }
        return myBitmap;
    }

    public static Bitmap createBitmapFromText(Context context, int width, int height, String text, int textColor, int borderColor, int backgroundColor) {

        if(StringUtils.isEmpty(text)) return null;

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(backgroundColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        padding = 1;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );
        int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

       //int letterTextSize = 20;
        int letterTextSize = 24;
       // int letterTextSize = 18;
        Typeface font =  ResourcesCompat.getFont(context, R.font.led_font);
        //Typeface font =  ResourcesCompat.getFont(context, R.font.square_sans_serif7_font);
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(textColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(text, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 2f;
        myCanvas.drawText(text,
                bounds.exactCenterX(), mLetterTop + bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap createButtonFromText(Context context, int width, int height, String text, int textColor, int borderColor, int backgroundColor) {
        //DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        //int width = displayMetrics.widthPixels;

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(backgroundColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        padding = 1;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );
        int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        //int letterTextSize = 20;
        int letterTextSize = 24;
        // int letterTextSize = 18;
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca_font);
        //Typeface font =  ResourcesCompat.getFont(context, R.font.square_sans_serif7_font);
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(textColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(text, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 2f;
        myCanvas.drawText(text,
                bounds.exactCenterX(), mLetterTop + bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap createBitmapFromTextSquare(Context context, int width, int height, String text, int textColor, int borderColor, int backgroundColor) {
        //DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        //int width = displayMetrics.widthPixels;

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(backgroundColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        padding = 2;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );
        int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 20;
       // int letterTextSize = 16;
        // int letterTextSize = 18;
        Typeface font =  ResourcesCompat.getFont(context, R.font.square_sans_serif7_font);
        //Typeface font =  ResourcesCompat.getFont(context, R.font.square_sans_serif7_font);
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(textColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(text, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height() / 2f) + 5;
        myCanvas.drawText(text,
                bounds.exactCenterX(), mLetterTop + bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap getResIcon(Context context, AudioTag tag) {
     /*   Bitmap icon = null;
      //  int borderColor = context.getColor(R.color.black);
        //int hraColor = getSampleRateColor(context,item);
        int hraColor = Color.WHITE;
        int borderColor = Color.WHITE; //getResolutionColor(context,item);
       // int bgColor = Color.TRANSPARENT;
        //int bgColor = getEncodingColor(context, item); //getResolutionColor(context,item);
        int bgColor = getResolutionColor(context,tag);
        if(tag.isMQA() ) {
            icon = AudioTagUtils.createButtonFromText(context, 68,32,"MQA",hraColor,borderColor, bgColor);
        }else if (isDSD(tag)) {
            //icon = MediaItemUtils.createButtonFromText(context, 64, 32, item.getMetadata().getDSDRate(), hraColor, hraColor, Color.TRANSPARENT);
            icon = AudioTagUtils.createButtonFromText(context, 64,32,"HD",hraColor,borderColor,bgColor);
        } else if (isHiResLossless(tag)) {
            icon = AudioTagUtils.createButtonFromText(context, 64,32,"HR",hraColor,borderColor,bgColor);
        }else if (isLossless(tag)) {
            icon = AudioTagUtils.createButtonFromText(context, 64,32,"PCM",hraColor,borderColor,bgColor);
        }
        return icon; */

        // for DSD, MQA only
/*
        int borderColor = Color.TRANSPARENT;
        int qualityColor = Color.TRANSPARENT;
        int size =24;
        if(tag.isMQA() ) {
            return createBitmapFromDrawable(context, size, size,R.drawable.ic_format_mqa_white,borderColor, qualityColor);
      //  }else if (isDSD(tag)) {
      //      return createBitmapFromDrawable(context, size, size,R.drawable.ic_format_dsd_white,borderColor, qualityColor);
      //  } else if (isHiRes(tag)) {
      //      return createBitmapFromDrawable(context, size, size,R.drawable.ic_format_hires_white,borderColor, qualityColor);
       // }else if (isLossless(tag)) {
       //     return createBitmapFromDrawable(context, size, size,R.drawable.ic_format_mp3_black,borderColor, qualityColor);
       // }else {
       //     return createBitmapFromDrawable(context, size, size,R.drawable.ic_waves_white,borderColor, qualityColor);
        } */
        if(tag.isMQA() ) {
            return getMQASamplingRateIcon(context, tag);
        }else if (isDSD(tag)) {
            return getBitsPerSampleIcon(context, tag);
        }

        return null;
    }

    public static int getResolutionColor(Context context, AudioTag tag) {
        // DSD - DSD
        // Hi-Res Lossless - >= 24 bits and >= 48 kHz
        // Lossless - >= 24 bits and >= 48 kHz
        // High Quality - compress
        if(isDSD(tag)) {
            return context.getColor(R.color.quality_hd);
       // }else if(tag.isMQA()) {
       //     return context.getColor(R.color.quality_hd);
        }else if(isPCMHiRes(tag)) {
            return context.getColor(R.color.quality_hd);
        }else if(is24Bits(tag)) {
            return context.getColor(R.color.quality_h24bits);
        }else if(isPCMLossless(tag)){
            return context.getColor(R.color.quality_sd);
        }else {
            return context.getColor(R.color.quality_unknown);
        }
    }

    public static boolean is24Bits(AudioTag tag) {
        return ( tag.isLossless() && (tag.getAudioBitsPerSample() >= Constants.QUALITY_BIT_DEPTH_HD));
    }

    public static boolean isDSD(AudioTag tag) {
        return tag.getAudioBitsPerSample()==Constants.QUALITY_BIT_DEPTH_DSD;
    }

   // public static boolean isPCMHiRes(AudioTag tag) {
        // >= 24/48
   //     return ( tag.isLossless() && (tag.getAudioBitsPerSample() >= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() >= Constants.QUALITY_SAMPLING_RATE_48));
   // }

   // public static boolean isPCMHiResLossless(AudioTag tag) {
        // = 24/48
   //     return ( tag.isLossless() && (tag.getAudioBitsPerSample() >= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() == Constants.QUALITY_SAMPLING_RATE_48));
   // }

    public static boolean isPCMHiRes(AudioTag tag) {
        // > 24/88
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ( tag.isLossless() && (tag.getAudioBitsPerSample() >= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() >= Constants.QUALITY_SAMPLING_RATE_88));
    }

    public static boolean isPCMLossless(AudioTag tag) {
        // 16/48
        // 24/48
        return ( tag.isLossless() &&
                ((tag.getAudioBitsPerSample() <= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() <= Constants.QUALITY_SAMPLING_RATE_48))); // ||
                // (tag.getAudioBitsPerSample() == Constants.QUALITY_BIT_DEPTH_SD && tag.getAudioSampleRate() <= Constants.QUALITY_SAMPLING_RATE_48)));
    }

    public static Bitmap getSampleRateIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, Constants.INFO_SAMPLE_RATE_WIDTH, Constants.INFO_HEIGHT, tag.getAudioBitCountAndSampleRate(), textColor,borderColor, qualityColor);

        return icon;
    }
    public static Bitmap getDurationIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 80, Constants.INFO_HEIGHT, tag.getAudioDurationAsString(), textColor,borderColor, qualityColor);

        return icon;
    }

    public static String getFormattedTitle(Context context, AudioTag tag) {
        String title =  StringUtils.trimToEmpty(tag.getTitle());
        if(Preferences.isShowTrackNumber(context)) {
            String track = StringUtils.trimToEmpty(tag.getTrack());
            if(track.startsWith("0")) {
                track = track.substring(1,track.length());
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

    public static String getFormattedSubtitle(AudioTag tag) {
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

    public static String getAlbumArtistOrArtist(AudioTag tag) {
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

    public static Bitmap getFileSizeIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 100, 32, StringUtils.formatStorageSize(tag.getFileSize()), textColor,borderColor, qualityColor);

        return icon;
    }

    public static int getSourceRescId(String letter) {
        //String letter = item.getSource();
        if (StringUtils.isEmpty(letter)) {
            letter = Constants.SRC_NONE;
        }
        if (letter.equalsIgnoreCase(Constants.SRC_JOOX)) {
            return R.drawable.icon_joox;
        } else if (letter.equalsIgnoreCase(Constants.SRC_QOBUZ)) {
            return R.drawable.icon_qobuz;
        } else if (letter.equalsIgnoreCase(Constants.SRC_CD)) {
            return R.drawable.icon_cd;
        } else if (letter.equalsIgnoreCase(Constants.SRC_SACD)) {
            return R.drawable.icon_sacd;
        } else if (letter.equalsIgnoreCase(Constants.SRC_VINYL)) {
            return R.drawable.icon_vinyl;
        } else if (letter.equalsIgnoreCase(Constants.SRC_SPOTIFY)) {
            return R.drawable.icon_spotify;
        } else if (letter.equalsIgnoreCase(Constants.SRC_TIDAL)) {
            return R.drawable.icon_tidal;
        } else if (letter.equalsIgnoreCase(Constants.SRC_APPLE)) {
            return R.drawable.icon_itune;
        } else if (letter.equalsIgnoreCase(Constants.SRC_YOUTUBE)) {
            return R.drawable.icon_youtube;
        }

        return -1;
    }
    public static Bitmap getSourceIcon(Context context, AudioTag item) {
        int borderColor = Color.GRAY; //Color.TRANSPARENT;//Color.GRAY; //context.getColor(R.color.black);
        int qualityColor = Color.TRANSPARENT; //getResolutionColor(context,item); //getSampleRateColor(context,item);
        String letter = item.getSource();
        if(StringUtils.isEmpty(letter)) {
            letter = Constants.SRC_NONE;
        }
        int size =24;
        int rescId = getSourceRescId(item.getSource());
        if(Constants.SRC_NONE.equals(letter)) {
           return null;
        }else if(rescId ==-1) {
            int width = 128;
            int height = 48;
            int whiteColor = Color.WHITE;
            letter = StringUtils.getChars(letter,8);
            return createButtonFromText (context, width, height, letter, whiteColor, borderColor,qualityColor);
        }else {
            return createBitmapFromDrawable(context, size, size,rescId,qualityColor, qualityColor);
        }

       /* if(letter.equalsIgnoreCase(Constants.SRC_JOOX)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_joox,borderColor, qualityColor);
        }else if(letter.equalsIgnoreCase(Constants.SRC_QOBUZ)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_qobuz,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_CD)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_cd,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_SACD)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_sacd,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_VINYL)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_vinyl,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_SPOTIFY)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_spotify,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_TIDAL)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_tidal,borderColor, qualityColor);
        }else  if(letter.equalsIgnoreCase(Constants.SRC_APPLE)) {
            return createBitmapFromDrawable(context, size, size,R.drawable.icon_itune,borderColor, qualityColor);
        }else if(!Constants.SRC_NONE.equals(letter)) {
            int width = 96;
            int height = 32;
            int whiteColor = Color.WHITE;
            letter = StringUtils.getChars(letter,6);
            return createButtonFromText (context, width, height, letter, whiteColor, borderColor,borderColor);
        } */
        //return null;
    }

    public static Bitmap getFileFormatIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
      //  int qualityColor = item.getSampleRateColor(context);
        //int bgColor = getEncodingColor(context, tag);
        int bgColor = getResolutionColor(context, tag);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 60, 32, tag.getAudioEncoding(), textColor,borderColor, bgColor); //context.getColor(getEncodingColorId(item)));

        return icon;
    }
    public static Bitmap getBitRateIcon(Context context, AudioTag tag) {
          int borderColor = context.getColor(R.color.black);
          int textColor = context.getColor(R.color.black);
          int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 120, 32, StringUtils.getFormatedAudioBitRate(tag.getAudioBitRate()), textColor,borderColor, qualityColor);

        return icon;
    }

    public static boolean isOnDownloadDir(AudioTag tag) {
        return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    /*
    public static Bitmap getMQASampleRateIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag);
        String mqaRate = tag.getMQASampleRate();
        long rate = AudioTagUtils.parseMQASampleRate(mqaRate);
        if (rate ==0 || rate == tag.getAudioSampleRate()) {
            return null;
        }
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 120, 32, "M " +StringUtils.getFormatedAudioSampleRate(rate, true), textColor,borderColor, qualityColor);

        return icon;
    } */

    public static long parseMQASampleRate(String mqaRate) {
        if(StringUtils.isDigitOnly(mqaRate)) {
            return Long.parseLong(mqaRate);
        }
        return 0;
    }
/*
    public static Bitmap getDSDSampleRateIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag);
        int rateModulation = (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);

        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 120, 32, "DSD " +rateModulation, textColor,borderColor, qualityColor);

        return icon;
    } */

    public static int getDSDSampleRateModulation(AudioTag tag) {
        return (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);
    }

        public static String getTrackQuality(AudioTag tag) {
        if(tag.isDSD()) {
            return Constants.TITLE_DSD_AUDIO;
      //  }else if(tag.isMQA()) {
      //      return Constants.TITLE_MQA_AUDIO;
        }else if(isPCMHiRes(tag)) {
            return Constants.TITLE_HIRES;
        //}else if(isPCMHiResLossless(tag)) {
        //    return Constants.TITLE_HR_LOSSLESS;
        }else if(isPCMLossless(tag)) {
            return Constants.TITLE_HIFI_LOSSLESS;
        }else {
            return Constants.TITLE_HIFI_QUALITY;
        }
        //Hi-Res Audio
        //Lossless Audio
        //High Quality
        //DSD
    }

    public static String getTrackQualityDetails(AudioTag tag) {
        if(tag.isDSD()) {
            return "You can hear the detail and wide range of the music, its warm tone is very enjoyable.";
       // }else if(tag.isMQA()) {
        //    return Constants.TITLE_MQA_AUDIO;
        }else if(isPCMHiRes(tag)) {
            return "Enjoy and Listening the rich music which reproduces fine details of musical instruments.";
        }else if(isPCMLossless(tag)) {
            return "Reproduce details of music smooth as CD quality that you can hear.";
 //       }else if(tag.isLossless()) {
  //          return "Same as CD sound quality, audio is digitalized and compressed in order to be stored on CD.";
        }else {
            return "Best compromise between data usage and sound fidelity.";
        }
    }

    public static boolean isHiResOrDSD(AudioTag tag) {
       // return is24Bits(tag) || isDSD(tag);
        return isPCMHiRes(tag) || isDSD(tag);
    }

    public static String getDefaultAlbum(AudioTag tag) {
        // if album empty, add single
        String defaultAlbum = Constants.DEFAULT_ALBUM_TEXT;
        if(StringUtils.isEmpty(tag.getAlbum()) && !StringUtils.isEmpty(tag.getArtist())) {
            defaultAlbum = getFirstArtist(tag.getArtist())+" - Single";
        }else {
            defaultAlbum = StringUtils.trimToEmpty(tag.getAlbum());
        }
        return defaultAlbum;
    }

    private static String getFirstArtist(String artist) {
        if(artist.indexOf(";")>0) {
            return artist.substring(0,artist.indexOf(";"));
        }else if(artist.indexOf(",")>0) {
            return artist.substring(0,artist.indexOf(","));
        }
        return artist;
    }

    public static Bitmap getBitsPerSampleIcon(Context context,  AudioTag tag) {
        int width = 128;
        int height = 96;
       // int borderColor = context.getColor(R.color.grey400);
      //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
     //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String bps = "";
        if(tag.isDSD()) {
            int rateModulation = (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);
             bps = "DSD"+rateModulation;
        }else {
             bps = StringUtils.getFormatedBitsPerSample(tag.getAudioBitsPerSample());
        }
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),true);
      //  Bitmap icon = AudioTagUtils.createBitmapFromText(context, 72, 36, StringUtils.getFormatedBitsPerSample(tag.getAudioBitsPerSample()), textColor,borderColor, qualityColor);
      //  return icon;
        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw black box
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw top white box
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                (myCanvas.getHeight()/2) - 2 // Bottom
        );
       // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(whiteColor);
        paint.setStyle(Paint.Style.FILL);
        //.setStyle(Paint.Style.STROKE);
        //paint.setStrokeWidth(borderWidth);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 30; //28;
       // Typeface font =  ResourcesCompat.getFont(context, R.font.led_font);
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca_font);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(bps, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 8f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(bps,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw sampling rate, white color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 2f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/3);
        myCanvas.drawText(samplingRate,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap getMQASamplingRateIcon(Context context,  AudioTag tag) {
        if(!tag.isMQA()) return null;
        int width = 128;
        int height = 96;
        // int borderColor = context.getColor(R.color.grey400);
        //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String mqaLabel = "MQA";
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),true);
        String mqaRate = tag.getMQASampleRate();
        long rate = AudioTagUtils.parseMQASampleRate(mqaRate);
        if (rate >0) {
            samplingRate = StringUtils.getFormatedAudioSampleRate(rate, true);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw black box
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw top white box
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                //(myCanvas.getHeight()/2) - 2 // Bottom
                (myCanvas.getHeight()/3)
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(whiteColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw orval
        rectangle = new RectF(
                padding, // Left
                (myCanvas.getHeight()/3) -4, // Top
                myCanvas.getWidth() - padding, // Right
                (myCanvas.getHeight()/2) - 2 // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(whiteColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawOval(rectangle, paint);

        int letterTextSize = 30; //28;
        // Typeface font =  ResourcesCompat.getFont(context, R.font.led_font);
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca_font);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(mqaLabel, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 8f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(mqaLabel,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw sampling rate, white color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 2f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/3);
        myCanvas.drawText(samplingRate,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap getAudiophileIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.grey400);
        int textColor = context.getColor(R.color.black);
       // int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        int qualityColor = context.getColor(R.color.audiophile_background);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 128, 36, "Audiophile", textColor,borderColor, qualityColor);

        return icon;
    }

/*
    public static int getEncodingColor(final Context context, AudioTag tag) {
        if(item.getTag().isDSD()) {
            return context.getColor(R.color.encoding_dsd);
        }else if(item.getTag().isMQA()) {
            return context.getColor(R.color.encoding_mqa);
        }else if(item.getTag().isPCM44()) {
            return context.getColor(R.color.encoding_pcm_44);
        }else if(item.getTag().isPCM48()) {
            return context.getColor(R.color.encoding_pcm_48);
        } else  if(item.getTag().isPCM88()) {
            return context.getColor(R.color.encoding_pcm_88);
        } else if(item.getTag().isPCM96()) {
            return context.getColor(R.color.encoding_pcm_96);
        } else if(item.getTag().isPCM96PLUS()) {
            return context.getColor(R.color.encoding_pcm_96_plus);
        }else {
            // bad
            return context.getColor(R.color.encoding_unknown);
        }
    } */

    /*
    public static int getEncodingColorId(MediaItem item) {
        if(item.getMetadata().isDSD()) {
            return  R.color.encoding_dsd;
        }else if(item.getMetadata().isMQA()) {
            return  R.color.encoding_mqa;
        }else if(item.getMetadata().isLossless()) {
            if("FLAC".equalsIgnoreCase(item.getMetadata().getAudioFormat()) ||
                    "ALAC".equalsIgnoreCase(item.getMetadata().getAudioFormat())) {
                return R.color.encoding_compress;
            }else {
                return R.color.encoding_uncompress;
            }
        }else {
            // MP3/AAC
            return R.color.encoding_unknown;
        }
    } */
}
