package apincer.android.mmate.provider;

import static apincer.android.mmate.Constants.QUALITY_FAVORITE;
import static apincer.android.mmate.Constants.QUALITY_RECOMMENDED;
import static apincer.android.mmate.codec.MusicAnalyser.THRESHOLD_RESAMPLED;
import static apincer.android.mmate.codec.MusicAnalyser.THRESHOLD_UPSCALED;
import static apincer.android.mmate.utils.MusicTagUtils.getBPSAndSampleRate;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRange;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeAsString;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeScore;
import static apincer.android.mmate.utils.MusicTagUtils.getEncodingColor;
import static apincer.android.mmate.utils.MusicTagUtils.getFileEncodingColor;
import static apincer.android.mmate.utils.MusicTagUtils.getResolutionColor;
import static apincer.android.mmate.utils.MusicTagUtils.getSourceRescId;
import static apincer.android.mmate.utils.MusicTagUtils.isAIFFile;
import static apincer.android.mmate.utils.MusicTagUtils.isALACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isDSD;
import static apincer.android.mmate.utils.MusicTagUtils.isFLACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isHiRes;
import static apincer.android.mmate.utils.MusicTagUtils.isLossless;
import static apincer.android.mmate.utils.MusicTagUtils.isMPegFile;
import static apincer.android.mmate.utils.MusicTagUtils.isMQA;
import static apincer.android.mmate.utils.MusicTagUtils.isMQAStudio;
import static apincer.android.mmate.utils.MusicTagUtils.isWavFile;
import static apincer.android.mmate.utils.StringUtils.getAbvByUpperCase;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trim;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.ColorUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class IconProviders {
    private static final String TAG = "IconProviders";

    public static Drawable getFileFormatBackground(Context context, MusicTag tag) {
        if(isDSD(tag) ||
                isWavFile(tag) ||
                isAIFFile(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_uncompress);
        }else if(
                isALACFile(tag) ||
                        isFLACFile(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_compress_lossless);
        }else {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossy);
        }
    }

    @Deprecated
    public static Bitmap getSourceIcon(Context context, MusicTag tag) {
        int borderColor = Color.GRAY; //Color.TRANSPARENT;//Color.GRAY; //context.getColor(R.color.black);
        int qualityColor = Color.TRANSPARENT; //getResolutionColor(context,item); //getSampleRateColor(context,item);
        String letter = ""; //tag.getMediaType();
        if(StringUtils.isEmpty(letter)) {
            return null;
        }
        int size =24;
        int rescId = getSourceRescId(letter);

        if(rescId ==-1) {
            int width = 96; //48;
            int height = 48;
            int whiteColor = Color.WHITE;
            letter = getAbvByUpperCase(letter);
            if(StringUtils.isEmpty(letter)) {
                return null;
            }
            return createButtonFromText (context, width, height, letter, whiteColor, borderColor,qualityColor);
        }else {
            return createBitmapFromDrawable(context, size, size,rescId,qualityColor, qualityColor);
        }
    }

    public static File getTrackQualityIcon(Context context, MusicTag tag) {
        //TrackQuality_DR_DRS_UP_RE.png
        File dir = context.getExternalCacheDir();
        String quality = String.format("%s_%s_%s_%s_%s.png", getTrackQuality(tag), getDynamicRange(tag), getDynamicRangeScore(tag), getUpscaledScore(tag), getResampledScore(tag));
        String path = "/Icons/Sq/"+quality;

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            try {
                FileUtils.createParentDirs(pathFile);

               // Bitmap bitmap = createTrackQualityIcon(context, tag);
                Bitmap bitmap = createQualityIcon(context, tag);

               // Bitmap bitmap = BitmapHelper.createHexagonBitmap(400, 400); // createTrackQualityIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath()));
            } catch (Exception e) {
                Log.e(TAG,"getTrackQualityIcon",e);
            }
        }
        return pathFile;
    }

    private static int getResampledScore(MusicTag tag) {
      //  return (int) Math.round(tag.getResampledScore() * 100);
        return 1;
    }

    private static int getUpscaledScore(MusicTag tag) {
       // return (int) Math.round(tag.getUpscaledScore() * 100);
        return 1;
    }

    public static int getDRBackgroundColor(Context context, int drValue) {
        if (drValue == 0) return ContextCompat.getColor(context, R.color.dr_no_trans);
        else if (drValue < 7) return ContextCompat.getColor(context, R.color.dr_low_trans);
        else if (drValue < 13) return ContextCompat.getColor(context, R.color.dr_medium_trans);
        else if (drValue < 19) return ContextCompat.getColor(context, R.color.dr_high_trans);
        else return ContextCompat.getColor(context, R.color.dr_very_high_trans);
    }

    private static Object getTrackQuality(MusicTag tag) {
        return trim(tag.getMediaQuality(), "None");
    }

    public static File getFileResolutionIcon( Context context, MusicTag tag) {
        //Resolution_ENC_samplingrate|bitrate.png
        File dir = context.getExternalCacheDir();
        String path = "/Icons/Res/";

        if(tag.isDSD()) {
            // dsd use bitrate
            path = path+"DSD" + tag.getAudioBitRate();
        }else if(isMQA(tag)) {
            String mqa = "MQA";
            if (isMQAStudio(tag)) {
                mqa = "MQAS";
            }
            path = String.format("%s%s_%s_%s.png", path, mqa, tag.getAudioBitsDepth(), tag.getMqaSampleRate());
        }else {
          //  path = path + mqa +sep+tag.getAudioBitsDepth()+sep+ tag.getMqaSampleRate();
       // }else if(isHiRes(tag) || isLossless(tag)){
           // path = path + "PCM"+sep+tag.getAudioBitsDepth()+sep + tag.getAudioSampleRate();
            path = String.format("%s%s_%s_%s.png", path, tag.getAudioEncoding(), tag.getAudioBitsDepth(), tag.getAudioSampleRate());
       // }else {
            //path = path + tag.getFileFormat()+sep+tag.getAudioBitsDepth()+sep+ tag.getAudioBitRate();
       //     path = String.format("%s%s_%s_%s.png", path, tag.getFileType(), tag.getAudioBitsDepth(), tag.getAudioSampleRate());
        }
        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            // create file
            try {
                FileUtils.createParentDirs(pathFile);

                Bitmap bitmap = createFileResolutionIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath()));
            } catch (Exception e) {
                Log.e(TAG,"getEncResolutionIcon",e);
            }
        }
        return pathFile;
    }

    public static File getResolutionIcon( Context context, MusicTag tag) {
        //Resolution_ENC_samplingrate|bitrate.png
        File dir = context.getExternalCacheDir();
        String path = "/Icons/Res/";

        if(tag.isDSD()) {
            // dsd use bitrate
            path = path+"DSD" + tag.getAudioBitRate();
        }else if(isMQA(tag)) {
            String mqa = "MQA";
            if(isMQAStudio(tag)) {
                mqa = "MQAS";
            }
            path = String.format("%s%s_%s_%s.png", path, mqa, tag.getAudioBitsDepth(), tag.getMqaSampleRate());

            //  path = path + mqa +sep+tag.getAudioBitsDepth()+sep+ tag.getMqaSampleRate();
        }else if(isHiRes(tag) || isLossless(tag)){
            // path = path + "PCM"+sep+tag.getAudioBitsDepth()+sep + tag.getAudioSampleRate();
            path = String.format("%s%s_%s_%s.png", path, "PCM", tag.getAudioBitsDepth(), tag.getAudioSampleRate());
        }else {
            //path = path + tag.getFileFormat()+sep+tag.getAudioBitsDepth()+sep+ tag.getAudioBitRate();
            path = String.format("%s%s_%s_%s.png", path, tag.getFileType(), tag.getAudioBitsDepth(), tag.getAudioSampleRate());
        }
        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            // create file
            try {
                FileUtils.createParentDirs(pathFile);

                Bitmap bitmap = createEncodingSamplingRateIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath()));
            } catch (Exception e) {
                Log.e(TAG,"getEncResolutionIcon",e);
            }
        }
        return pathFile;
    }

    @Deprecated
    public static File getSourceQualityIcon(Context context, MusicTag tag) {
        //AudiophileRecords.png
        File dir = context.getExternalCacheDir();
        String quality = trimToEmpty(tag.getMediaQuality());
        String path = "/Icons/Quality"+quality+"Records.png";

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            //if(true) {
            // create file
            try {
                FileUtils.createParentDirs(pathFile);

                Bitmap bitmap = createSourceQualityIcon(context, quality);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                //if(is!=null) {
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath())); //new FileOutputStream(pathFile));
                //}
            } catch (Exception e) {
                Log.e(TAG,"getSourceQualityIcon",e);
            }
        }
        return pathFile;
    }


    @Deprecated
    public static Object getSourceQualityIconMini(Context context, MusicTag tag) {
        File dir = context.getExternalCacheDir();
        String quality = trimToEmpty(tag.getMediaQuality());
        String path = "/Icons/Quality"+quality+"RecordsMini.png";

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            // if(true) {
            // create file
            try {
                FileUtils.createParentDirs(pathFile);

                Bitmap bitmap = createSourceQualityIconMini(context, quality);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                //if(is!=null) {
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath())); //new FileOutputStream(pathFile));
                //}
            } catch (Exception e) {
                Log.e(TAG,"getSourceQualityIconMini",e);
            }
        }
        return pathFile;
    }

    /*** private functions **/
    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, int drawableId, int borderColor, int backgroundColor) {
        Bitmap icon = BitmapHelper.getBitmapFromVectorDrawable(context, drawableId);
        return createBitmapFromDrawable(context,width,height,icon, borderColor, backgroundColor);
    }

    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, Bitmap icon, int borderColor, int backgroundColor) {
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


    public static Bitmap createButtonFromText(Context context, int width, int height, String text, int textColor, int borderColor, int backgroundColor) {

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
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);
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

    private static Bitmap createFileResolutionIcon(Context context, MusicTag tag) {
        int width = 400; //340; // 24x105, 18x78 , 16x70
        int height = 96; //4.2
        int padding = 8;
        int paddingx2 = 16;
        int paddingx3 = 24;
        //  int paddingx4 = 32;

        int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.grey900);
        int bgWhiteColor = getResolutionColor(context, tag);
        int labelColor = getFileEncodingColor(context, tag);

        String label;
        String samplingRate = getBPSAndSampleRate(tag);

        if(tag.isDSD()) {
            // dsd use bitrate
            label = "DSD";
            long dsdRate = StringUtils.formatDSDRate(tag.getAudioSampleRate());
            samplingRate = String.valueOf(dsdRate);
           /* if(dsdRate < 256) {
                labelColor = context.getColor(R.color.resolution_dsd_64_128);
            }else {
                labelColor = context.getColor(R.color.resolution_dsd_256);
            } */
        }else if(isMQA(tag)) {
            label = "MQA";
            /* if(isMQAStudio(tag)) {
                labelColor = context.getColor(R.color.resolution_mqa_studio);
            }else {
                labelColor = context.getColor(R.color.resolution_mqa);
            } */
        }else {
            // any others format i.e. mpeg, aac
            label = StringUtils.trim(tag.getAudioEncoding(), tag.getFileType()).toUpperCase(Locale.US);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        // Initialize a new Round Rect object
        // draw border dark grey
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        //draw back color
        int bgPadding = 4;
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding, // Top
                myCanvas.getWidth() - bgPadding, // Right
                myCanvas.getHeight() - bgPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        // padding = 8;
        int topPadding = 12;
        rectangle = new RectF(
                padding, // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - topPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgWhiteColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box for encoding string
        int wboxPadding = 12;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);

        float blackBGStartPos = (float) (bounds.width() - ((float) bounds.width() /2.2)); // 2.6
        float blackBGShiftPos = (float) ((float) bounds.height() / 3.6); //4.2;
        Path path = new Path();
        path.moveTo(blackBGStartPos, wboxPadding); //x1,y1 - top left
        path.lineTo(bounds.width()-8, wboxPadding);   //x2,y1 - top right
        path.lineTo(bounds.width()-8, bounds.height()-wboxPadding);   //x2,y2 - bottom right
        path.lineTo(blackBGStartPos, bounds.height()-wboxPadding); //x1,y2 - bottom left
        path.lineTo(blackBGStartPos+blackBGShiftPos, wboxPadding); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        // MQA, FLAC, ALAC, AIFF, WAVE, DSD. MPEG, AAC
        Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        int letterTextSize = 58; //48;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setColor(labelColor);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = bounds.top+paddingx3+textMathRect.height();
        float mPositionX=  blackBGStartPos+padding+ ((bounds.right - blackBGStartPos)/2);
        myCanvas.drawText(label,
                mPositionX,
                mLetterTop, //top
                mLetterPaint);

        // draw sampling rate, black color
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        letterTextSize = 62; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        mLetterPaint.setAntiAlias(true);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = bounds.top+paddingx3+textMathRect.height();
        mPositionX= blackBGStartPos/2; // center position to draw text
        myCanvas.drawText(samplingRate,
                mPositionX,
                mLetterTop,
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap createEncodingSamplingRateIcon(Context context, MusicTag tag) {
        int width = 400; //340; // 24x105, 18x78 , 16x70
        int height = 96; //4.2
        int padding = 8;
        int paddingx2 = 16;
        int paddingx3 = 24;
        //  int paddingx4 = 32;

        int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.grey900);
        int bgWhiteColor = getResolutionColor(context, tag);
        int labelColor = getEncodingColor(context, tag);

        String label;
        String samplingRate = getBPSAndSampleRate(tag);

        if(tag.isDSD()) {
            // dsd use bitrate
            label = "DSD";
            long dsdRate = StringUtils.formatDSDRate(tag.getAudioSampleRate());
            samplingRate = String.valueOf(dsdRate);
            if(dsdRate < 256) {
                labelColor = context.getColor(R.color.resolution_dsd_64_128);
            }else {
                labelColor = context.getColor(R.color.resolution_dsd_256);
            }
        }else if(isMQA(tag)) {
            label = "MQA";
            if(isMQAStudio(tag)) {
                labelColor = context.getColor(R.color.resolution_mqa_studio);
            }else {
                labelColor = context.getColor(R.color.resolution_mqa);
            }
        } else if (isLossless(tag) || isHiRes(tag)) {
            label = "PCM";
        } else if (isMPegFile(tag)) {
            label = "MP3";
        }else {
            // any others format i.e. mpeg, aac
            label = StringUtils.trim(tag.getAudioEncoding(), tag.getFileType()).toUpperCase(Locale.US);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        // Initialize a new Round Rect object
        // draw border dark grey
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        //draw back color
        int bgPadding = 4;
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding, // Top
                myCanvas.getWidth() - bgPadding, // Right
                myCanvas.getHeight() - bgPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        // padding = 8;
        int topPadding = 12;
        rectangle = new RectF(
                padding, // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - topPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgWhiteColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        int wboxPadding = 12;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);

        float blackBGStartPos = (float) (bounds.width() - ((float) bounds.width() /2.6)); // 2.8
        float blackBGShiftPos = (float) ((float) bounds.height() / 4.2); //3;
        Path path = new Path();
        path.moveTo(blackBGStartPos, wboxPadding); //x1,y1 - top left
        path.lineTo(bounds.width()-8, wboxPadding);   //x2,y1 - top right
        path.lineTo(bounds.width()-8, bounds.height()-wboxPadding);   //x2,y2 - bottom right
        path.lineTo(blackBGStartPos, bounds.height()-wboxPadding); //x1,y2 - bottom left
        path.lineTo(blackBGStartPos+blackBGShiftPos, wboxPadding); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        // MQA, PCM, DSD. MPEG, AAC
        Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        int letterTextSize = 48; //28;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        //mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setColor(labelColor);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = bounds.top+paddingx3+textMathRect.height();
        float mPositionX=  blackBGStartPos+padding+ ((bounds.right - blackBGStartPos)/2); //bounds.exactCenterY()-(bounds.exactCenterY()/6);
        myCanvas.drawText(label,
                //bounds.exactCenterX()+(bounds.exactCenterX()/2), // left
                mPositionX,
                mLetterTop, //top
                mLetterPaint);

        // draw quality,up-scaled, up-sampled indicator
        float y = (float) (myCanvas.getHeight() / 2) + 30;
        // float startx = (float) (myCanvas.getWidth() * 0.56)+8;
        float startx = blackBGStartPos+36;
        int barHigh = 12;
        int barWidth = 32; //40;
        int barSpace = 8;
        //quality
        startx = startx - paddingx2;
        float endx = startx+barWidth;
        paint = new Paint();
        // paint.setColor(qualityColor);
        paint.setColor(labelColor);
        paint.setStrokeWidth(barHigh);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);

        path = new Path();
        //path.moveTo(startx +2, y + 4);
        path.moveTo(startx, y);
        path.lineTo(endx, y);
        myCanvas.drawPath(path, paint);

        //upscale
        startx = endx+barSpace;
        endx = startx+barWidth;
        paint = new Paint();
        //paint.setColor(upscaleColor);
        paint.setColor(labelColor);
        paint.setStrokeWidth(barHigh);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);

        path = new Path();
        path.moveTo(startx, y);
        path.lineTo(endx, y);
        myCanvas.drawPath(path, paint);

        //upsampled
        startx = endx+barSpace;
        endx = startx+barWidth;
        paint = new Paint();
        // paint.setColor(resampledColor);
        paint.setColor(labelColor);
        paint.setStrokeWidth(barHigh);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);

        path = new Path();
        path.moveTo(startx, y);
        path.lineTo(endx, y);
        myCanvas.drawPath(path, paint);

        // draw sampling rate, black color
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        // font =  ResourcesCompat.getFont(context, R.font.cozette_bold);
        letterTextSize = 62; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        mLetterPaint.setAntiAlias(true);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = bounds.top+paddingx3+textMathRect.height();
        mPositionX= blackBGStartPos/2; // center position to draw text
        myCanvas.drawText(samplingRate,
                mPositionX,
                mLetterTop,
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap createSourceQualityIcon(Context context, String qualityText) {
        int width = 280; // 16x46, 24x70
        int height = 96;
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
        int recordsColor = context.getColor(R.color.quality_label);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.quality_good);
        String label1 = trimToEmpty(qualityText);
        String label2 = "R e c o r d s";

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        //draw back color
        padding = 0;
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

        // draw audiophile background
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw grey shade colors
        int []colors = new int[7];

        colors[6] = context.getColor(R.color.material_color_blue_grey_300);
        colors[5] = context.getColor(R.color.material_color_blue_grey_400);
        colors[4] = context.getColor(R.color.material_color_blue_grey_500);
        colors[3] = context.getColor(R.color.material_color_blue_grey_600);
        colors[2] = context.getColor(R.color.material_color_blue_grey_700);
        colors[1] = context.getColor(R.color.material_color_blue_grey_800);
        colors[0] = context.getColor(R.color.material_color_blue_grey_900);

        int barWidth = 6;
        int rndNo =0;
        padding=8;
        int bottomPos = height-padding-4;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            path.moveTo(12+(rndNo*barWidth), padding);
            path.lineTo(12+(rndNo*barWidth), bottomPos-(rndNo*barWidth));
            path.lineTo( width-padding, bottomPos-(rndNo*barWidth));

            float radius = 36.0f;

            CornerPathEffect cornerPathEffect =
                    new CornerPathEffect(radius);

            paint.setPathEffect(cornerPathEffect);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw label "Records", grey color
        Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
        int letterTextSize = 24; //28;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(recordsColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label2, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() ;
        float mPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
        myCanvas.drawText(label2,
                (float) (bounds.width()-(textMathRect.width()*6.5)), // left
                mLetterTop + mPositionY+10, //bounds.exactCenterY(), //top
                mLetterPaint);

        // draw label "Audiophile", quality color
        font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        if(Constants.QUALITY_AUDIOPHILE.equals(label1)) {
            // Audiophile
            letterTextSize = 40; //82;
            qualityColor = context.getColor(R.color.quality_audiophile);
        }else if(QUALITY_RECOMMENDED.equals(label1)) {
            letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_recommended);
        }else if (QUALITY_FAVORITE.equals(label1)) {
            letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_good);
        }else if(Constants.QUALITY_BAD.equals(label1)) {
            letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_bad);
        }else {
            letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_no_rating); //recordsColor;
        }

        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(qualityColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(label1, 0, 1, textMathRect);
        // mLetterTop = (textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()-16;//+(bounds.exactCenterY()/4);
        myCanvas.drawText(label1,
                bounds.exactCenterX() +20, //left
                mPositionY,// - mLetterTop, //bounds.exactCenterY(), // top
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap createQualityIcon(Context context, MusicTag tag) {
        int width = 340; // 16x46, 24x70
        int height = 100;
        String qualityText = trimToEmpty(tag.getMediaQuality());
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
        int drColor = context.getColor(R.color.white); //context.getColor(R.color.yellows_lemon); //context.getColor(R.color.quality_label);
        int drsColor = context.getColor(R.color.grey200); //context.getColor(R.color.white);
        int drsHexagonColor = getDRBackgroundColor(context, (int) tag.getDynamicRangeScore());
        int drsHexagonBroaderColor = context.getColor(R.color.material_color_green_400);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.quality_good);
        int greyColor = context.getColor(R.color.grey200);
        String label1 =  isEmpty(qualityText)?"No Rated":qualityText;
        String drLabel = getDynamicRangeAsString(tag); //String.format("DR %s | %s", getTrackDRScore(tag), getDynamicRangeAsString(tag));
        String drsLabel = getDynamicRangeScore(tag); //getTrackDRScore(tag);
        // Define colors for upscaled and resampled indicators based on scores
        int upscaledColor;
        int resampledColor;

        // Get scores from tag (values between 0.0-1.0)
        double upscaledScore = 1; //tag.getUpscaledScore(); // Assuming this method exists
        double resampledScore = 1; //tag.getResampledScore(); // Assuming this method exists

        // Set upscaled color based on score
        if (upscaledScore <= 0.0) {
            // Not upscaled
            upscaledColor = context.getColor(R.color.quality_scale_matched);
        } else if (upscaledScore > THRESHOLD_UPSCALED) {
            // Fully upscaled
            upscaledColor = context.getColor(R.color.quality_scale_not_matched);
        } else {
            // Partially upscaled - good
            upscaledColor = context.getColor(R.color.quality_good);
        }

        // Set resampled color based on score
        if (resampledScore <= 0.0) {
            // Not resampled
            resampledColor = context.getColor(R.color.quality_scale_matched);
        } else if (resampledScore > THRESHOLD_RESAMPLED) {
            // Fully resampled
            resampledColor = context.getColor(R.color.quality_scale_not_matched);
        } else {
            // Partially resampled - good
            resampledColor = context.getColor(R.color.quality_good);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        // draw border dark grey
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        //draw back color
        // padding = 0;
        int bgPadding = 4;
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding, // Top
                myCanvas.getWidth() - bgPadding, // Right
                myCanvas.getHeight() - bgPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw audiophile background
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw grey shade colors
        int []colors = new int[7];

        colors[6] = context.getColor(R.color.material_color_blue_grey_300);
        colors[5] = context.getColor(R.color.material_color_blue_grey_400);
        colors[4] = context.getColor(R.color.material_color_blue_grey_500);
        colors[3] = context.getColor(R.color.material_color_blue_grey_600);
        colors[2] = context.getColor(R.color.material_color_blue_grey_700);
        colors[1] = context.getColor(R.color.material_color_blue_grey_800);
        colors[0] = context.getColor(R.color.material_color_blue_grey_900);

        int barWidth = 6;
        int rndNo =0;
        int bottomPos = height-12;
        int marginLeft = 42; //24; //12;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            path.moveTo(marginLeft+(rndNo*barWidth), padding);
            path.lineTo(marginLeft+(rndNo*barWidth), bottomPos-(rndNo*barWidth));
            path.lineTo( width-padding, bottomPos-(rndNo*barWidth));
            // path.close();

            float radius = 32.0f;

            CornerPathEffect cornerPathEffect =
                    new CornerPathEffect(radius);

            paint.setPathEffect(cornerPathEffect);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw hexagon icon for DRS
        Path path = new Path();
        float radius = 42;
        float positionX = 46;
        float positionY = (float) bounds.height() /2;
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            float x = (float) (positionX + radius * Math.cos(angle));
            float y = (float) (positionY + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        Paint paint = new Paint();
        paint.setColor(drsHexagonColor);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawPath(path, paint);

        // add hex border
        path = new Path();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            float x = (float) (positionX + radius * Math.cos(angle));
            float y = (float) (positionY + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        paint = new Paint();
        paint.setColor(drsHexagonBroaderColor);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeWidth(4);
        paint.setStyle(Paint.Style.STROKE);
        myCanvas.drawPath(path, paint);

        // draw label Dynamic Range Scored, grey color
        if(!isEmpty(drsLabel)) {
            Typeface drsFont = ResourcesCompat.getFont(context, R.font.k2d_bold);
            int drsTextSize = 42; //46;
            int drsMargin = 34; //32;
            Paint drsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            drsPaint.setColor(drsColor);
            drsPaint.setTypeface(drsFont);
            drsPaint.setAntiAlias(true);
            drsPaint.setDither(true);
            drsPaint.setTextSize(drsTextSize);
            drsPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect drsMathRect = new Rect();
            drsPaint.getTextBounds(drsLabel, 0, 1, drsMathRect);
            // float drsPositionTop = (float) (drsMathRect.height() + drsMargin);
            float drsPositionTop= bounds.exactCenterY()+12;
            // float drsPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
            myCanvas.drawText(drsLabel,
                    drsMargin + padding, // left
                    drsPositionTop, //top
                    drsPaint);
        }

        // draw label Dynamic Range (dB), grey color
        if(!isEmpty(drLabel)) {
            Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
            int letterTextSize = 36; //24; //28;
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(drColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setAntiAlias(true);
            mLetterPaint.setDither(true);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(drLabel, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height();
            float mPositionY = bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
            //float mPositionX = (float) (bounds.width() - 64);
            float mPositionX = (float) (bounds.width() - 120);
            myCanvas.drawText(drLabel,
                    mPositionX, // left
                    mLetterTop + mPositionY + 4, //bounds.exactCenterY(), //top
                    mLetterPaint);
        }

        // Draw upscaled indicator square
       // float mLetterTop = bounds.exactCenterY();
        float mPositionY = bounds.exactCenterY() +padding; //-(bounds.exactCenterY()/2);
        float mPositionX = (float) (bounds.width() - 64);
        int squareWidth = 16;
        int squareHeight = 24;
        int squareSpacing = 8;
        int squaresY = (int)(mPositionY);
        int upscaledX = (int)mPositionX + padding;

        Paint squarePaint = new Paint();
        squarePaint.setAntiAlias(true);
        squarePaint.setStyle(Paint.Style.FILL);

        // Draw upscaled square
        squarePaint.setColor(upscaledColor);
        RectF upscaledRect = new RectF(
                upscaledX,
                squaresY,
                upscaledX + squareWidth,
                squaresY + squareHeight
        );
        myCanvas.drawRect(upscaledRect, squarePaint);

        // Draw upscaled square border
        Paint borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setAntiAlias(true);
        myCanvas.drawRect(upscaledRect, borderPaint);

        // Draw "U" label inside upscaled square
        Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
        Paint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTypeface(font);
        labelPaint.setTextSize(12);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        myCanvas.drawText("U", upscaledX + (float) squareWidth /2, squaresY + squareHeight - 4, labelPaint);

        // Draw resampled square
        int resampledX = upscaledX + squareWidth + squareSpacing;
        squarePaint.setColor(resampledColor);
        RectF resampledRect = new RectF(
                resampledX,
                squaresY,
                resampledX + squareWidth,
                squaresY + squareHeight
        );
        myCanvas.drawRect(resampledRect, squarePaint);

        // Draw resampled square border
        myCanvas.drawRect(resampledRect, borderPaint);

        // Draw "R" label inside resampled square
        myCanvas.drawText("R", resampledX + (float) squareWidth /2, squaresY + squareHeight - 4, labelPaint);

        // draw label "Audiophile", quality color
        font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        int letterTextSize = 32; //82;
        if(Constants.QUALITY_AUDIOPHILE.equals(label1)) {
            qualityColor = context.getColor(R.color.quality_audiophile);
        }else if(QUALITY_RECOMMENDED.equals(label1)) {
            //  letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_recommended);
        }else if (QUALITY_FAVORITE.equals(label1)) {
            // letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_good);
        }else if(Constants.QUALITY_BAD.equals(label1)) {
            //  letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_bad);
        }else {
            //   letterTextSize = 30; //82;
            qualityColor = context.getColor(R.color.quality_no_rating); //recordsColor;
        }

        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(qualityColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label1, 0, 1, textMathRect);
        // mLetterTop = (textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()-16;//+(bounds.exactCenterY()/4);
        myCanvas.drawText(label1,
                bounds.exactCenterX() +36, //left
                mPositionY,// - mLetterTop, //bounds.exactCenterY(), // top
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap createQualityIconBak(Context context, MusicTag tag) {
        int width = 280; // 16x46, 24x70
        int height = 96;
        String qualityText = trimToEmpty(tag.getMediaQuality());
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
        int drColor = context.getColor(R.color.white); //context.getColor(R.color.yellows_lemon); //context.getColor(R.color.quality_label);
        int drsColor = context.getColor(R.color.grey200); //context.getColor(R.color.white);
        int drsHexagonColor = getDRBackgroundColor(context, (int) tag.getDynamicRangeScore());
        int drsHexagonBroaderColor = context.getColor(R.color.material_color_green_400);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.quality_good);
        int greyColor = context.getColor(R.color.grey200);
        String label1 =  isEmpty(qualityText)?"No Rated":qualityText;
        String drLabel = getDynamicRangeAsString(tag); //String.format("DR %s | %s", getTrackDRScore(tag), getDynamicRangeAsString(tag));
        String drsLabel = getDynamicRangeScore(tag); //getTrackDRScore(tag);
        // Define colors for upscaled and resampled indicators based on scores
        int upscaledColor;
        int resampledColor;

        // Get scores from tag (values between 0.0-1.0)
        double upscaledScore = 1; //tag.getUpscaledScore(); // Assuming this method exists
        double resampledScore = 1; //tag.getResampledScore(); // Assuming this method exists

        // Set upscaled color based on score
        if (upscaledScore <= 0.0) {
            // Not upscaled
            upscaledColor = context.getColor(R.color.quality_scale_matched);
        } else if (upscaledScore > THRESHOLD_UPSCALED) {
            // Fully upscaled
            upscaledColor = context.getColor(R.color.quality_scale_not_matched);
        } else {
            // Partially upscaled - good
            upscaledColor = context.getColor(R.color.quality_good);
        }

        // Set resampled color based on score
        if (resampledScore <= 0.0) {
            // Not resampled
            resampledColor = context.getColor(R.color.quality_scale_matched);
        } else if (resampledScore > THRESHOLD_RESAMPLED) {
            // Fully resampled
            resampledColor = context.getColor(R.color.quality_scale_not_matched);
        } else {
            // Partially resampled - good
            resampledColor = context.getColor(R.color.quality_good);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        // draw border dark grey
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        //draw back color
        // padding = 0;
        int bgPadding = 4;
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding, // Top
                myCanvas.getWidth() - bgPadding, // Right
                myCanvas.getHeight() - bgPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw audiophile background
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw grey shade colors
        int []colors = new int[7];

        colors[6] = context.getColor(R.color.material_color_blue_grey_300);
        colors[5] = context.getColor(R.color.material_color_blue_grey_400);
        colors[4] = context.getColor(R.color.material_color_blue_grey_500);
        colors[3] = context.getColor(R.color.material_color_blue_grey_600);
        colors[2] = context.getColor(R.color.material_color_blue_grey_700);
        colors[1] = context.getColor(R.color.material_color_blue_grey_800);
        colors[0] = context.getColor(R.color.material_color_blue_grey_900);

        int barWidth = 6;
        int rndNo =0;
        int bottomPos = height-12;
        int marginLeft = 42; //24; //12;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            path.moveTo(marginLeft+(rndNo*barWidth), padding);
            path.lineTo(marginLeft+(rndNo*barWidth), bottomPos-(rndNo*barWidth));
            path.lineTo( width-padding, bottomPos-(rndNo*barWidth));
            // path.close();

            float radius = 32.0f;

            CornerPathEffect cornerPathEffect =
                    new CornerPathEffect(radius);

            paint.setPathEffect(cornerPathEffect);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw hexagon icon for DRS
        Path path = new Path();
        float radius = 42;
        float positionX = 46;
        float positionY = (float) bounds.height() /2;
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            float x = (float) (positionX + radius * Math.cos(angle));
            float y = (float) (positionY + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        Paint paint = new Paint();
        paint.setColor(drsHexagonColor);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawPath(path, paint);

        // add hex border
        path = new Path();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            float x = (float) (positionX + radius * Math.cos(angle));
            float y = (float) (positionY + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        paint = new Paint();
        paint.setColor(drsHexagonBroaderColor);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeWidth(4);
        paint.setStyle(Paint.Style.STROKE);
        myCanvas.drawPath(path, paint);

        // draw label Dynamic Range Scored, grey color
        if(!isEmpty(drsLabel)) {
            Typeface drsFont = ResourcesCompat.getFont(context, R.font.k2d_bold);
            int drsTextSize = 42; //46;
            int drsMargin = 34; //32;
            Paint drsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            drsPaint.setColor(drsColor);
            drsPaint.setTypeface(drsFont);
            drsPaint.setAntiAlias(true);
            drsPaint.setDither(true);
            drsPaint.setTextSize(drsTextSize);
            drsPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect drsMathRect = new Rect();
            drsPaint.getTextBounds(drsLabel, 0, 1, drsMathRect);
            // float drsPositionTop = (float) (drsMathRect.height() + drsMargin);
            float drsPositionTop= bounds.exactCenterY()+12;
            // float drsPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
            myCanvas.drawText(drsLabel,
                    drsMargin + padding, // left
                    drsPositionTop, //top
                    drsPaint);
        }

        // draw label Dynamic Range (dB), grey color
        if(!isEmpty(drLabel)) {
            Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
            int letterTextSize = 36; //24; //28;
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(drColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setAntiAlias(true);
            mLetterPaint.setDither(true);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(drLabel, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height();
            float mPositionY = bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
            //float mPositionX = (float) (bounds.width() - 64);
            float mPositionX = (float) (bounds.width() - 120);
            myCanvas.drawText(drLabel,
                    mPositionX, // left
                    mLetterTop + mPositionY + 4, //bounds.exactCenterY(), //top
                    mLetterPaint);
        }

        // Draw upscaled indicator square
        // float mLetterTop = bounds.exactCenterY();
        float mPositionY = bounds.exactCenterY() +padding; //-(bounds.exactCenterY()/2);
        float mPositionX = (float) (bounds.width() - 64);
        int squareWidth = 16;
        int squareHeight = 24;
        int squareSpacing = 8;
        int squaresY = (int)(mPositionY);
        int upscaledX = (int)mPositionX + padding;

        Paint squarePaint = new Paint();
        squarePaint.setAntiAlias(true);
        squarePaint.setStyle(Paint.Style.FILL);

        // Draw upscaled square
        squarePaint.setColor(upscaledColor);
        RectF upscaledRect = new RectF(
                upscaledX,
                squaresY,
                upscaledX + squareWidth,
                squaresY + squareHeight
        );
        myCanvas.drawRect(upscaledRect, squarePaint);

        // Draw upscaled square border
        Paint borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setAntiAlias(true);
        myCanvas.drawRect(upscaledRect, borderPaint);

        // Draw "U" label inside upscaled square
        Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
        Paint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTypeface(font);
        labelPaint.setTextSize(12);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        myCanvas.drawText("U", upscaledX + (float) squareWidth /2, squaresY + squareHeight - 4, labelPaint);

        // Draw resampled square
        int resampledX = upscaledX + squareWidth + squareSpacing;
        squarePaint.setColor(resampledColor);
        RectF resampledRect = new RectF(
                resampledX,
                squaresY,
                resampledX + squareWidth,
                squaresY + squareHeight
        );
        myCanvas.drawRect(resampledRect, squarePaint);

        // Draw resampled square border
        myCanvas.drawRect(resampledRect, borderPaint);

        // Draw "R" label inside resampled square
        myCanvas.drawText("R", resampledX + (float) squareWidth /2, squaresY + squareHeight - 4, labelPaint);

        // draw label "Audiophile", quality color
        font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        int letterTextSize = 32; //82;
        qualityColor = switch (label1) {
            case Constants.QUALITY_AUDIOPHILE -> context.getColor(R.color.quality_audiophile);
            case QUALITY_RECOMMENDED ->
                //  letterTextSize = 30; //82;
                    context.getColor(R.color.quality_recommended);
            case QUALITY_FAVORITE ->
                // letterTextSize = 30; //82;
                    context.getColor(R.color.quality_good);
            case Constants.QUALITY_BAD ->
                //  letterTextSize = 30; //82;
                    context.getColor(R.color.quality_bad);
            default ->
                //   letterTextSize = 30; //82;
                    context.getColor(R.color.quality_no_rating); //recordsColor;

        };

        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(qualityColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label1, 0, 1, textMathRect);
        // mLetterTop = (textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()-16;//+(bounds.exactCenterY()/4);
        myCanvas.drawText(label1,
                bounds.exactCenterX() +36, //left
                mPositionY,// - mLetterTop, //bounds.exactCenterY(), // top
                mLetterPaint);

        return myBitmap;
    }

    @Deprecated
    public static Bitmap createSourceQualityIcon(Context context) {
        int width = 192;
        int height = 96;
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
        int blackColor = context.getColor(R.color.black);

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        //draw back color
        padding = 0;
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

        // draw audiophile background
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw grey shade colors
        int []colors = new int[7];

        colors[6] = context.getColor(R.color.material_color_blue_grey_300);
        colors[5] = context.getColor(R.color.material_color_blue_grey_400);
        colors[4] = context.getColor(R.color.material_color_blue_grey_500);
        colors[3] = context.getColor(R.color.material_color_blue_grey_600);
        colors[2] = context.getColor(R.color.material_color_blue_grey_700);
        colors[1] = context.getColor(R.color.material_color_blue_grey_800);
        colors[0] = context.getColor(R.color.material_color_blue_grey_900);

        int barWidth = 6;
        int rndNo =0;
        padding=8;
        int bottomPos = height-padding-4;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            path.moveTo(12+(rndNo*barWidth), padding);
            path.lineTo(12+(rndNo*barWidth), bottomPos-(rndNo*barWidth));
            path.lineTo( width-padding, bottomPos-(rndNo*barWidth));

            float radius = 36.0f;

            CornerPathEffect cornerPathEffect =
                    new CornerPathEffect(radius);

            paint.setPathEffect(cornerPathEffect);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw label "Records", grey color
        // draw label "Audiophile", quality color
        // font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        // if(Constants.QUALITY_AUDIOPHILE.equals(label1)) {
        // Audiophile
        //     letterTextSize = 40; //82;
        // }else {
        //     letterTextSize = 30; //82;
        //      qualityColor = recordsColor;
        //  }
        //  mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        //  mLetterPaint.setColor(qualityColor);
        //  mLetterPaint.setTypeface(font);
        //  mLetterPaint.setAntiAlias(true);
        //  mLetterPaint.setDither(true);
        // mLetterPaint.setTextSize(letterTextSize);
        //  mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.

        return myBitmap;
    }


    public static Bitmap createSourceQualityIconMini(Context context, String quality) {
        int width =  132;//280; // 16x46, 24x70
        int height = 96;
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
        int recordsColor = context.getColor(R.color.audiophile_label2);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.audiophile_label1);
        String label1 =  StringUtils.getAbvByUpperCase(quality); //;"Audiophile";
        String label2 = "R e c o r d s";

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        //draw back color
        padding = 0;
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

        // draw audiophile background
        padding = 8;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw grey shade colors
        int []colors = new int[7];

        colors[6] = context.getColor(R.color.material_color_blue_grey_300);
        colors[5] = context.getColor(R.color.material_color_blue_grey_400);
        colors[4] = context.getColor(R.color.material_color_blue_grey_500);
        colors[3] = context.getColor(R.color.material_color_blue_grey_600);
        colors[2] = context.getColor(R.color.material_color_blue_grey_700);
        colors[1] = context.getColor(R.color.material_color_blue_grey_800);
        colors[0] = context.getColor(R.color.material_color_blue_grey_900);

        int barWidth = 6;
        int rndNo =0;
        padding=8;
        int bottomPos = height-padding-4;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            path.moveTo(12+(rndNo*barWidth), padding);
            path.lineTo(12+(rndNo*barWidth), bottomPos-(rndNo*barWidth));
            path.lineTo( width-padding, bottomPos-(rndNo*barWidth));

            float radius = 36.0f;

            CornerPathEffect cornerPathEffect =
                    new CornerPathEffect(radius);

            paint.setPathEffect(cornerPathEffect);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw label "Records", grey color
        Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
        int letterTextSize = 21; //28;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(recordsColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label2, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() ;
        float mPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/2);
        myCanvas.drawText(label2,
                (float) (bounds.width()-(textMathRect.width()*5.0)), // left
                mLetterTop + mPositionY+10, //bounds.exactCenterY(), //top
                mLetterPaint);

        // draw label "Audiophile", quality color
        font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
        if(Constants.QUALITY_AUDIOPHILE.equals(quality)) {
            // Audiophile
            letterTextSize = 40; //82;
        }else {
            letterTextSize = 30; //82;
            qualityColor = recordsColor;
        }
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(qualityColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(label1, 0, 1, textMathRect);
        // mLetterTop = (textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()-8;//+(bounds.exactCenterY()/4);
        myCanvas.drawText(label1,
                bounds.exactCenterX() +20, //left
                mPositionY,// - mLetterTop, //bounds.exactCenterY(), // top
                mLetterPaint);

        return myBitmap;
    }

    @Deprecated
    public static Bitmap createTrackQualityIcon(Context context, MusicTag tag) {
        int width =  144; //280; // 16x46, 24x70
        int height = 96; //1.5

        int greyColor = context.getColor(R.color.grey200);
        int darkBackground = Color.BLACK; // context.getColor(R.color.material_color_blue_grey_900);
        int lightBackground = context.getColor(R.color.material_color_blue_grey_900); //context.getColor(R.color.material_color_blue_grey_400);
        int circleBackground = context.getColor(R.color.black_transparent_80); //context.getColor(R.color.material_color_blue_grey_700);
        int drScoreColor = context.getColor(R.color.white);

        int upscaleColor = context.getColor(R.color.quality_scale_not_test);
        int resampledColor = context.getColor(R.color.quality_scale_not_test); //(tag.isUpsampled()?context.getColor(R.color.quality_bad):context.getColor(R.color.quality_good));

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        int centerX = (bounds.left + bounds.right) / 2;
        int centerY = ((bounds.top + bounds.bottom) / 2 ) - 12;

        // draw border grey color
        // draw border dark grey
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );
        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        //draw dark bg color
        padding = 0;
        int bgPadding = 4;
        rectangle = new RectF(
                bgPadding, // Left
                cornerRadius*2, // Top
                myCanvas.getWidth() - bgPadding, // Right
                myCanvas.getHeight() - bgPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkBackground);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw top light bg color
        int separatorY = bounds.bottom - ((bounds.bottom - bounds.top)/2);
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding, // Top
                myCanvas.getWidth() - bgPadding, // Right
                separatorY //myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(lightBackground);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);
        rectangle = new RectF(
                bgPadding, // Left
                bgPadding+cornerRadius, // Top
                myCanvas.getWidth() - bgPadding, // Right
                separatorY+cornerRadius //myCanvas.getHeight() - padding // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 0,0, bgPaint);

        // draw left arc for upscaled indicator
        int []colors = new int[12];
        colors[colors.length-1] = upscaleColor;
        for(int i=(colors.length-2);i>=0;i--) {
            colors[i] = ColorUtils.TranslateLight(colors[i+1], 8);
        }
        int barWidth = 4;
        int rndNo =0;
        int positionX = centerX; //(int) (centerX - (radius*2.2));
        int topY = cornerRadius-4; //(colors.length*barWidth);
        int bottomY = separatorY+8;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            int offset = rndNo*barWidth+12;
            Path path = new Path();
            path.moveTo(positionX-offset, topY);
            path.lineTo( positionX-offset, bottomY);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // resampled
        colors[colors.length-1] = resampledColor;
        for(int i=(colors.length-2);i>=0;i--) {
            colors[i] = ColorUtils.TranslateLight(colors[i+1], 8);
        }
        rndNo =0;
        for(int color: colors) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(barWidth+2);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            int offset = rndNo*barWidth+12;
            Path path = new Path();
            path.moveTo(positionX+offset, topY);
            path.lineTo( positionX+offset, bottomY);
            myCanvas.drawPath(path, paint);
            rndNo++;
        }

        // draw circle @ center
        int radius = (bounds.bottom - bounds.top)/3;
        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(circleBackground);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawCircle(centerX,centerY, radius, bgPaint);

        // draw dynamic range score
        String drScore = getDynamicRangeScore(tag);
        if(isEmpty(drScore)) {
            drScore = StringUtils.SYMBOL_MUSIC_NOTE;
        }
        Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
        int letterTextSize = 40;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(drScoreColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setAntiAlias(true);
        mLetterPaint.setDither(true);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(drScore, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height();
        float mLetterRight = textMathRect.width();
        myCanvas.drawText(drScore,
                (centerX - (mLetterRight / 2) + 4), // left
                centerY + (mLetterTop / 2), //top
                mLetterPaint);
        // }
        // draw dynamic range value
        if(tag.getDynamicRange()!=0.00) {
            String dr = getDynamicRangeAsString(tag);
            font = ResourcesCompat.getFont(context, R.font.k2d_bold);
            letterTextSize = 32;
            mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(drScoreColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setAntiAlias(true);
            mLetterPaint.setDither(true);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            textMathRect = new Rect();
            mLetterPaint.getTextBounds(dr, 0, 1, textMathRect);
            mLetterTop = textMathRect.height();
            mLetterRight = textMathRect.width();
            myCanvas.drawText(dr,
                    (centerX - (mLetterRight / 2) + 4), // left
                    separatorY + ((float) separatorY / 2) + (mLetterTop / 2) + 2, //top
                    mLetterPaint);
        }

        // draw right arc for resampled indicator
        // barWidth = 8;
        // colors[0] = context.getColor(R.color.material_color_blue_grey_400);
        // colors[1] = resampledColor; //context.getColor(R.color.material_color_blue_grey_900);

        // fix border that replaced
        Paint paint = new Paint();
        paint.setColor(greyColor);
        paint.setStrokeWidth(bgPadding);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);

        // left border
        Path path = new Path();
        path.moveTo(0, cornerRadius);
        path.lineTo(0, bounds.bottom-cornerRadius);
        myCanvas.drawPath(path, paint);

        // right border
        path = new Path();
        path.moveTo(bounds.right, cornerRadius);
        path.lineTo(bounds.right, bounds.bottom-cornerRadius);
        myCanvas.drawPath(path, paint);

        // top border
        path = new Path();
        path.moveTo(cornerRadius, 0);
        path.lineTo(bounds.right-cornerRadius, 0);
        myCanvas.drawPath(path, paint);

        return myBitmap;
    }

    @Deprecated
    public static void drawRhombus(Canvas canvas, Paint paint, int x, int y, int width) {
        int halfWidth = width / 2;

        Path path = new Path();
        path.moveTo(x, y + halfWidth); // Top
        path.lineTo(x - halfWidth, y); // Left
        path.lineTo(x, y - halfWidth); // Bottom
        path.lineTo(x + halfWidth, y); // Right
        path.lineTo(x, y + halfWidth); // Back to Top
        path.close();

        canvas.drawPath(path, paint);
    }

    @Deprecated
    public static void drawTriangle(Canvas canvas, Paint paint, int x, int y, int width) {
        int halfWidth = width / 2;

        Path path = new Path();
        path.moveTo(x, y - halfWidth); // Top
        path.lineTo(x - halfWidth, y + halfWidth); // Bottom left
        path.lineTo(x + halfWidth, y + halfWidth); // Bottom right
        path.lineTo(x, y - halfWidth); // Back to Top
        path.close();

        canvas.drawPath(path, paint);
    }

}
