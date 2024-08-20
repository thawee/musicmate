package apincer.android.mmate.utils;

import static apincer.android.mmate.Constants.MIN_SPL_16BIT_IN_DB;
import static apincer.android.mmate.Constants.MIN_SPL_24BIT_IN_DB;
import static apincer.android.mmate.Constants.QUALITY_AUDIOPHILE;
import static apincer.android.mmate.Constants.QUALITY_BAD;
import static apincer.android.mmate.Constants.QUALITY_BIT_CD;
import static apincer.android.mmate.Constants.QUALITY_GOOD;
import static apincer.android.mmate.Constants.QUALITY_RECOMMENDED;
import static apincer.android.mmate.Constants.QUALITY_SAMPLING_RATE_48;
import static apincer.android.mmate.Constants.QUALITY_SAMPLING_RATE_96;
import static apincer.android.mmate.utils.StringUtils.getAbvByUpperCase;
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
import androidx.core.content.res.ResourcesCompat;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;

public class MusicTagUtils {
    private static final String TAG = MusicTagUtils.class.getName();
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

    private static Bitmap createEncodingSamplingRateIcon(Context context, MusicTag tag) {
        int width = 340; // 24x85, 16x56, 18x63
        int height = 96;

       // int whiteColor = context.getColor(R.color.white);
        int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.grey900);
       // int resolutionColor = getResolutionColor(context,tag);
        int bgWhiteColor = getResolutionColor(context, tag); //context.getColor(R.color.grey100);
        int qualityColor = context.getColor(R.color.quality_no_rating);
        int labelColor = getEncodingColor(context, tag);
       // if(!(isDSD(tag) || isLossless(tag))) { // QUALITY_NORMAL.equals(tag.getMediaQuality())) {
       //     qualityColor = context.getColor(R.color.quality_bad);
       // }else
        if(Constants.QUALITY_AUDIOPHILE.equals(tag.getMediaQuality()) ||
                QUALITY_RECOMMENDED.equals(tag.getMediaQuality()) ||
                QUALITY_GOOD.equals(tag.getMediaQuality())
        ) {
            qualityColor = context.getColor(R.color.quality_good);
        }else if(QUALITY_BAD.equals(tag.getMediaQuality())) {
            qualityColor = context.getColor(R.color.quality_bad);
        }
        int upscaleColor = context.getColor(R.color.quality_scale_not_test);
       // if(!(isDSD(tag) || isLossless(tag))) {
       //     upscaleColor = context.getColor(R.color.quality_bad);
       // }else if (tag.getDynamicRange() > 0.0) {
        if (tag.getDynamicRange() > 0.0) {
            //if (isUpScaled(tag)) {
                upscaleColor = (isUpScaled(tag) ? context.getColor(R.color.quality_scale_not_matched) : context.getColor(R.color.quality_scale_matched));
           // }else {
                // not upscale, good
               // upscaleColor = context.getColor(R.color.quality_scale_not_test);
           // }
        }
        int upsampledColor = context.getColor(R.color.quality_scale_not_test);; //(tag.isUpsampled()?context.getColor(R.color.quality_bad):context.getColor(R.color.quality_good));
        // 16 bit should be 44.1 or 48 kHz only
        if(tag.getAudioBitRate() == QUALITY_BIT_CD && tag.getAudioSampleRate() > QUALITY_SAMPLING_RATE_48) {
            upsampledColor = context.getColor(R.color.quality_scale_not_matched);
        }

        String label;
        String samplingRate = StringUtils.formatAudioSampleRate(tag.getAudioSampleRate(),false);

        if(tag.isDSD()) {
            // dsd use bitrate
            label = "DSD";
            long dsdRate = StringUtils.formatDSDRate(tag.getAudioSampleRate());
            samplingRate = String.valueOf(dsdRate);
            if(dsdRate < 256) {
                //labelColor = context.getColor(R.color.mqa_studio);
                labelColor = context.getColor(R.color.resolution_dsd_64_128);
            }else {
                // labelColor = context.getColor(R.color.mqa_master);
                labelColor = context.getColor(R.color.resolution_dsd_256);
            }
        }else if(isMQA(tag)) {
            label = "MQA"; //tag.getMqaInd();
            samplingRate = StringUtils.formatAudioSampleRate(tag.getMqaSampleRate(),false);
            if(isMQAStudio(tag)) {
                //labelColor = context.getColor(R.color.mqa_studio);
                labelColor = context.getColor(R.color.resolution_mqa_studio);
            }else {
               // labelColor = context.getColor(R.color.mqa_master);
                labelColor = context.getColor(R.color.resolution_mqa);
            }
        } else {
            // PCM, draw bit
            //label = tag.getAudioBitsDepth() +"B"; //"PCM";
            label = "PCM";
           // labelColor = resolutionColor;
            //labelBPS = String.valueOf(tag.getAudioBitsDepth());
           // isPCM = true;
            if(!isLossless(tag)) {
                // compress rate
               // label = tag.getFileFormat().toUpperCase(Locale.US); ////tag.getAudioEncoding();
               // samplingRate = StringUtils.formatAudioBitRateShortUnit(tag.getAudioBitRate());
               // label = StringUtils.formatAudioBitRateShortUnit(tag.getAudioBitRate());
                label = tag.getAudioEncoding().toUpperCase(Locale.US); ////tag.getAudioEncoding();
               // labelColor = bgWhiteColor;
            }
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
        padding = 4;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        padding = 8;
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
        padding = 12;

        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo((float) (bounds.width()/2), padding); //x1,y1 - top left
        path.lineTo(bounds.width()-8, padding);   //x2,y1 - top right
        path.lineTo(bounds.width()-8, bounds.height()-padding);   //x2,y2 - bottom right
        path.lineTo((float) (bounds.width()/2.1), bounds.height()-padding); //x1,y2 - bottom left
        path.lineTo((float) (bounds.width()/2), padding); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        // draw enc, black color
        // AAC, MPEG, PCM 16, PCM 24, MQA, DSD

        /*
        if(isPCM) {
            // PCM xx
            Typeface font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
            int letterTextSize = 40; //34; //42;

          //  if(isLossless) {
                // draw label bit label
                Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(resolutionColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(letterTextSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                Rect textMathRect = new Rect();
                mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
                float mLetterTop = textMathRect.height() / 3f;
                float mPositionY = bounds.exactCenterY(); //-(bounds.exactCenterY()/16));
                myCanvas.drawText(label,
                        //(float) (bounds.exactCenterX() + (bounds.exactCenterX() / 4))+2, // left
                        (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                        mLetterPaint);

                // draw bps
                int bpsSize = 52;
                font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
                mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(resolutionColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(bpsSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                textMathRect = new Rect();
                mLetterPaint.getTextBounds(labelBPS, 0, 1, textMathRect);
                // mLetterTop = textMathRect.height() / 2.5f;
                mPositionY = (float) (bounds.bottom) - textMathRect.height();
                myCanvas.drawText(labelBPS,
                        // (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        bounds.exactCenterX() + (bounds.exactCenterX() / 4) + 2, // left
                        mPositionY,  // top
                        mLetterPaint); */
        /*    }else {
                // draw encoding
                int bpsSize = 52;
                font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
                Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(resolutionColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(bpsSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                Rect textMathRect = new Rect();
                mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
                // mLetterTop = textMathRect.height() / 2.5f;
                float mPositionY = (float) (bounds.bottom) - textMathRect.height();
                myCanvas.drawText(label,
                        // (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        bounds.exactCenterX() + (bounds.exactCenterX() / 4) + 36, // left
                        mPositionY,  // top
                        mLetterPaint);
            } */

            // draw PCM bar
          //  float y = (float) (myCanvas.getHeight() / 2) + 14;
         //   float startx = (float) (myCanvas.getWidth() * 0.56);

            // draw bottom bar curve
            /*
            paint = new Paint();
            paint.setColor(qualityColor);
           // paint.setColor(barColor);
            paint.setStrokeWidth(8);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            path = new Path();
            path.moveTo(startx - 14, y + 4);
            path.lineTo(startx - 16, y + 12);
            path.lineTo(width - 12, y + 12);
            myCanvas.drawPath(path, paint); */
       // } else
       /* if (tag.isDSD()) {
            // DSD,
            Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
            int letterTextSize = 60; //28;
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
            float mLetterTop = textMathRect.height() / 3f;
            float mPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/18);
            myCanvas.drawText(label,
                    bounds.exactCenterX()+(bounds.exactCenterX()/2), // left
                    mLetterTop + mPositionY+4,  //top
                    mLetterPaint);
        }else { */
            // MQA, PCM, DSD
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
            float mLetterTop = textMathRect.height() / 3f;
            float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/6);
            myCanvas.drawText(label,
                    bounds.exactCenterX()+(bounds.exactCenterX()/2), // left
                    mLetterTop + mPositionY, //top
                    mLetterPaint);

            // draw mqa studio master indicator
            /*
            int barWidth = 6;
            float y = (float) (myCanvas.getHeight()/2)+22;
            float startx = (float) (myCanvas.getWidth() *0.57)+8;

            paint = new Paint();
            paint.setColor(barColor);
            paint.setStrokeWidth(barWidth);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            for(int i=0;i<5;i++) {
                drawRhombus(myCanvas, paint, (int) (startx+(i*24)), (int) y, 12);
            } */
       // }

        // draw quality,upscaled, upsampled indicator
        float y = (float) (myCanvas.getHeight() / 2) + 30;
        float startx = (float) (myCanvas.getWidth() * 0.56)+8;
        int barHigh = 12;
        int barWidth = 42;
        int barSpace = 6;
        //quality
        startx = startx - 16;
        float endx = startx+barWidth; //width-100;
        paint = new Paint();
        paint.setColor(qualityColor);
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
        paint.setColor(upscaleColor);
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
        paint.setColor(upsampledColor);
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
        letterTextSize = 60; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 4f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/4);
        myCanvas.drawText(samplingRate,
                (float) (bounds.exactCenterX()-(bounds.exactCenterX()/2.2)),
                mLetterTop + mPositionY,
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap createEncodingSamplingRateIconBAK(Context context, MusicTag tag) {
        int width = 340; // 24x85, 16x56, 18x63
        int height = 96;
        // int whiteColor = context.getColor(R.color.white);
        int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.grey900);
        int qualityColor = getResolutionColor(context,tag);
        int barColor = context.getColor(R.color.material_color_blue_grey_200);
        String label;
        String labelBPS = "";
        boolean isPCM = false;
        boolean isLossless = isLossless(tag);
        String samplingRate = StringUtils.formatAudioSampleRate(tag.getAudioSampleRate(),false);

        if(tag.isDSD()) {
            // dsd use bitrate
            label = "DSD";
            samplingRate = StringUtils.formatAudioSampleRateAbvUnit(tag.getAudioBitRate());
        }else if(isMQA(tag)) {
            label = "MQA"; //tag.getMqaInd();
            samplingRate = StringUtils.formatAudioSampleRate(tag.getMqaSampleRate(),false);
            if(isMQAStudio(tag)) {
                barColor = context.getColor(R.color.mqa_studio);
            }else {
                barColor = context.getColor(R.color.mqa_master);
            }
        } else {
            // PCM, draw bit
            label = "BIT"; //"PCM";
            labelBPS = String.valueOf(tag.getAudioBitsDepth());
            isPCM = true;
            // isLossless = tag.isLossless();
            if(!isLossless) {
                // compress rate
                label = tag.getFileFormat().toUpperCase(Locale.US); ////tag.getAudioEncoding();
                samplingRate = StringUtils.formatAudioBitRateShortUnit(tag.getAudioBitRate());
            }
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
        padding = 4;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        padding = 8;
        int topPadding = 12;
        rectangle = new RectF(
                padding, // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - topPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(qualityColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        padding = 12;

        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo((float) (bounds.width()/2), padding); //x1,y1 - top left
        path.lineTo(bounds.width()-8, padding);   //x2,y1 - top right
        path.lineTo(bounds.width()-8, bounds.height()-padding);   //x2,y2 - bottom right
        path.lineTo((float) (bounds.width()/2.1), bounds.height()-padding); //x1,y2 - bottom left
        path.lineTo((float) (bounds.width()/2), padding); //x1,y1 - top left
        myCanvas.drawPath(path, paint);

        // draw enc, black color
        // AAC, MPEG, PCM 16, PCM 24, MQA, DSD

        if(isPCM) {
            // PCM xx
            Typeface font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
            int letterTextSize = 40; //34; //42;

            if(isLossless) {
                // draw label bit label
                Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(qualityColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(letterTextSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                Rect textMathRect = new Rect();
                mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
                float mLetterTop = textMathRect.height() / 3f;
                float mPositionY = bounds.exactCenterY(); //-(bounds.exactCenterY()/16));
                myCanvas.drawText(label,
                        //(float) (bounds.exactCenterX() + (bounds.exactCenterX() / 4))+2, // left
                        (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                        mLetterPaint);

                // draw bps
                int bpsSize = 52;
                font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
                mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(qualityColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(bpsSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                textMathRect = new Rect();
                mLetterPaint.getTextBounds(labelBPS, 0, 1, textMathRect);
                // mLetterTop = textMathRect.height() / 2.5f;
                mPositionY = (float) (bounds.bottom) - textMathRect.height();
                myCanvas.drawText(labelBPS,
                        // (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        bounds.exactCenterX() + (bounds.exactCenterX() / 4) + 2, // left
                        mPositionY,  // top
                        mLetterPaint);
            }else {
                // draw encoding
                int bpsSize = 52;
                font = ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
                Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                //mLetterPaint.setColor(whiteColor);
                mLetterPaint.setColor(qualityColor);
                mLetterPaint.setTypeface(font);
                mLetterPaint.setAntiAlias(true);
                mLetterPaint.setTextSize(bpsSize);
                mLetterPaint.setTextAlign(Paint.Align.CENTER);
                // Text draws from the baselineAdd some top padding to center vertically.
                Rect textMathRect = new Rect();
                mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
                // mLetterTop = textMathRect.height() / 2.5f;
                float mPositionY = (float) (bounds.bottom) - textMathRect.height();
                myCanvas.drawText(label,
                        // (float) (bounds.exactCenterX() + (bounds.exactCenterX() / 1.4)), //left
                        bounds.exactCenterX() + (bounds.exactCenterX() / 4) + 36, // left
                        mPositionY,  // top
                        mLetterPaint);
            }

            // draw PCM bar
            float y = (float) (myCanvas.getHeight() / 2) + 14;
            float startx = (float) (myCanvas.getWidth() * 0.56);

            // draw bottom bar curve
            paint = new Paint();
            paint.setColor(qualityColor);
            // paint.setColor(barColor);
            paint.setStrokeWidth(8);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            path = new Path();
            path.moveTo(startx - 14, y + 4);
            path.lineTo(startx - 16, y + 12);
            path.lineTo(width - 12, y + 12);
            myCanvas.drawPath(path, paint);
        } else if (tag.isDSD()) {
            // DSD,
            Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
            int letterTextSize = 60; //28;
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            //mLetterPaint.setColor(whiteColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setColor(qualityColor);
            mLetterPaint.setAntiAlias(true);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height() / 3f;
            float mPositionY= bounds.exactCenterY(); //-(bounds.exactCenterY()/18);
            myCanvas.drawText(label,
                    bounds.exactCenterX()+(bounds.exactCenterX()/2), // left
                    mLetterTop + mPositionY+4,  //top
                    mLetterPaint);
        }else {
            // MQA
            Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_extra_bold_italic);
            int letterTextSize = 48; //28;
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            //mLetterPaint.setColor(whiteColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setColor(qualityColor);
            mLetterPaint.setAntiAlias(true);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height() / 3f;
            float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/6);
            myCanvas.drawText(label,
                    bounds.exactCenterX()+(bounds.exactCenterX()/2), // left
                    mLetterTop + mPositionY, //top
                    mLetterPaint);

            // draw mqa studio master indicator
            int barWidth = 6;
            float y = (float) (myCanvas.getHeight()/2)+22;
            float startx = (float) (myCanvas.getWidth() *0.57)+8;

            paint = new Paint();
            paint.setColor(barColor);
            paint.setStrokeWidth(barWidth);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);

            for(int i=0;i<5;i++) {
                drawRhombus(myCanvas, paint, (int) (startx+(i*24)), (int) y, 12);
            }
        }

        // draw sampling rate, black color
        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 60; //82;
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height() / 4f);
        float mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/4);
        myCanvas.drawText(samplingRate,
                (float) (bounds.exactCenterX()-(bounds.exactCenterX()/2.2)),
                mLetterTop + mPositionY,
                mLetterPaint);

        return myBitmap;
    }

    private static boolean isMQAStudio(MusicTag tag) {
        return trimToEmpty(tag.getMqaInd()).contains("MQA Studio");
    }

    public static boolean isMQA(MusicTag tag) {
        return trimToEmpty(tag.getMqaInd()).contains("MQA");
    }


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

    public static Bitmap createSourceQualityIcon(Context context, String qualityText, boolean upscaled, boolean upsampled) {
        int width = 192;
        int height = 96;
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
       // int recordsColor = context.getColor(R.color.audiophile_label2);
        int blackColor = context.getColor(R.color.black);
       // int qualityColor = context.getColor(R.color.audiophile_label1);
       // String label1 = trimToEmpty(qualityText); //;"Audiophile";
       // String label2 = "R e c o r d s";

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
       /* Typeface font = ResourcesCompat.getFont(context, R.font.k2d_bold);
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
*/
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
       // textMathRect = new Rect();
       // mLetterPaint.getTextBounds(label1, 0, 1, textMathRect);
       // mLetterTop = (textMathRect.height() / 6f);
       // mPositionY= bounds.exactCenterY()-16;//+(bounds.exactCenterY()/4);
       // myCanvas.drawText(label1,
       //         bounds.exactCenterX() +20, //left
       //         mPositionY,// - mLetterTop, //bounds.exactCenterY(), // top
        //        mLetterPaint);

        return myBitmap;
    }

    public static Bitmap createSourceQualityIcon(Context context, String qualityText) {
        int width = 280; // 16x46, 24x70
        int height = 96;
        // int greyColor = context.getColor(R.color.grey200);
        int bgColor = context.getColor(R.color.material_color_blue_grey_900);
       // int recordsColor = context.getColor(R.color.audiophile_label2);
        int recordsColor = context.getColor(R.color.quality_good);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.quality_good);
       // int qualityColor = context.getColor(R.color.audiophile_label1);
        String label1 = trimToEmpty(qualityText); //;"Audiophile";
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
        }else if(QUALITY_RECOMMENDED.equals(label1) ||
                (QUALITY_GOOD.equals(label1))) {
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
        }else if(isLossless(tag)){
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

    /*
    public static Drawable getResolutionBackground(Context context, MusicTag tag) {
        // DSD - DSD
        // Hi-Res Lossless - >= 24 bits and >= 48 kHz
        // Lossless - >= 24 bits and >= 48 kHz
        // High Quality - compress
        if(isDSD(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_hd);
            // }else if(tag.isMQA()) {
            //     return context.getColor(R.color.quality_hd);
        }else if(isPCMHiRes(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_hd);
        }else if(is24Bits(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_24bits);
        }else if(isLossless(tag)){
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossless);
        }else {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_unknown);
        }
    } */

    public static Drawable getFileFormatBackground(Context context, MusicTag tag) {
      /*  if(isDSD(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_dsd);
        }else if(isLossless(tag)){
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossless);
        }else {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossy);
        } */

        if(isDSD(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_dsd);
        }else if(isHiRes(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_hd);
        }else if(isPCM24Bits(tag)) {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_24bits);
        }else if(isLossless(tag)){
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossless);
        }else {
            return AppCompatResources.getDrawable( context, R.drawable.shape_background_lossy);
        }
    }

    public static boolean isPCM24Bits(MusicTag tag) {
        return ( isLossless(tag) && (tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD));
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

    /*
    @Deprecated
    public static boolean isPCMHiRes(MusicTag tag) {
        // > 24/88
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ( isLossless(tag) && (tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() >= QUALITY_SAMPLING_RATE_96));
    } */

    /*
    public static boolean isPCM48(MusicTag tag) {
        // > 24/88
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ((tag.getAudioBitsDepth() > Constants.QUALITY_BIT_DEPTH_DSD) && (tag.getAudioSampleRate() <= QUALITY_SAMPLING_RATE_48));
    } */

    public static boolean isHiRes(MusicTag tag) {
        // > 24/96
        // JAS,  96kHz/24bit format or above
        //https://www.jas-audio.or.jp/english/hi-res-logo-en
        return ((tag.getAudioBitsDepth() >= Constants.QUALITY_BIT_DEPTH_HD) && (tag.getAudioSampleRate() >= QUALITY_SAMPLING_RATE_96));
    }

    /*
    @Deprecated
    public static boolean isPCMLossless(MusicTag tag) {
        // 16 / *
        // 24/48
        return ((tag.getAudioBitsDepth() == Constants.QUALITY_BIT_CD ||
                ((tag.getAudioBitsDepth() <= Constants.QUALITY_BIT_DEPTH_HD && tag.getAudioSampleRate() < Constants.QUALITY_SAMPLING_RATE_88)))); // ||
                // (tag.getAudioBitsPerSample() == Constants.QUALITY_BIT_DEPTH_SD && tag.getAudioSampleRate() <= Constants.QUALITY_SAMPLING_RATE_48)));
    } */

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

    public static String escapeURI(String s) {
        int len = s.length();
        StringBuilder sb = new StringBuilder(len + 30);
        int start = 0;
        for (int i = 0; i < len; i++) {
            String ref = null;
            if(Character.isSpaceChar(s.charAt(i))) {
                sb.append("_");
            }else if(Character.isLetterOrDigit(s.charAt(i))) {
                sb.append(s.charAt(i));
            }else {
                sb.append("_");
            }
           /* switch (s.charAt(i)) {
                case ' ': ref = "%20"; break;
            }
            if (ref != null) {
                sb.append(s.substring(start, i)).append(ref);
                start = i + 1;
            } */
        }
        return start == 0 ? s : sb.append(s.substring(start)).toString();
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

    /*
    public static boolean isFileCouldBroken(MusicTag tag) {
        if(tag.getFileSize()==0) return true;
        if(!isLossless(tag)) return false;
        return tag.getFileSizeRatio() < Constants.MIN_FILE_SIZE_RATIO; // 42 % of calculated size
    } */

    /*
    public static int getFileSizeRatio(MusicTag tag) {
        double calSize = calculateFileSize(tag);
        if(calSize==0.00) return 0; // file is not valid, cannot read metatag
        return (int) ((tag.getFileSize()/calSize)*100);
    } */

    /*
    public static double calculateFileSize(MusicTag tag) {
        // audio file size = bit rate * duration of audio in seconds * number of channels
        //bit rate = bit depth * sample rate
        double calSize = tag.getAudioBitsDepth()*tag.getAudioSampleRate();
        calSize = calSize * tag.getAudioDuration()*getChannels(tag);
        return calSize/8;
    }*/

    private static long getChannels(MusicTag tag) {
        String channels = trimToEmpty(tag.getAudioChannels());
        if("Stereo".equalsIgnoreCase(channels)) {
            channels = "2";
        }
        if(StringUtils.isDigitOnly(channels)) {
            return StringUtils.toLong(channels);
        }else {
            Log.i(TAG, "Channels is string: "+channels);
        }
        return 2; // default channels
    }

    /*@Deprecated
    public static File getCoverArt( Context context, MusicTag tag) {
        //PH|SD_AR|R_album|filename.png
        //md5 digest
        File dir = context.getExternalCacheDir();
        File songDir = new File(tag.getPath());
        if(tag.isMusicManaged()) {
            songDir = songDir.getParentFile();
       // }else  { //(isEmpty(tag.getAlbum())) {
      //      songDir = songDir.getParentFile();
        }
        String path = DigestUtils.md5Hex(songDir.getAbsolutePath())+".png";

        path = "/CoverArts/"+path;

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();
                // extract covert to cache directory
                FileRepository.extractCoverArt(tag, pathFile);
            } catch (Exception e) {
                Log.e(TAG,"getCoverArt",e);
            }
        }
        return pathFile;
    }*/

    public static boolean isLossless(MusicTag tag) {
        return (isFLACFile(tag) || isAIFFile(tag) || isWavFile(tag) || isALACFile(tag)) && !isHiRes(tag) && !isMQA(tag);
    }

    public static boolean isLossy(MusicTag tag) {
        return isMPegFile(tag) || isAACFile(tag);
    }

    public static File getEncResolutionIcon( Context context, MusicTag tag) {
        //Resolution_ENC_samplingrate|bitrate.png
        File dir = context.getExternalCacheDir();
        String path = "/Icons/";

        if(tag.isDSD()) {
            // dsd use bitrate
            path = path+"DSD_" + tag.getAudioBitRate();
        }else if(isMQA(tag)) {
            path = path + "MQA_" + tag.getAudioSampleRate();
            //if(tag.getAudioBitRate() == QUALITY_BIT_CD && (tag.getAudioSampleRate() == tag.getMqaSampleRate())) {
            //    path = path + "_Cd";
            //}else
            if(isMQAStudio(tag)) {
                path = path + "_Studio";
            }else {
                path = path + "_Master";
            }
        }else if(isLossless(tag)){
            path = path + "PCM"+tag.getAudioBitsDepth()+"_" + tag.getAudioSampleRate();
        }else {
            path = path + tag.getFileFormat()+"_" + tag.getAudioBitRate();
        }

        // file quality
        if(QUALITY_AUDIOPHILE.equals(tag.getMediaQuality())) {
            path = path + "_A";
        } else if(QUALITY_RECOMMENDED.equals(tag.getMediaQuality())) {
            path = path +"_R";
        } else if(QUALITY_BAD.equals(tag.getMediaQuality())) {
            path = path +"_B";
        //}else {
            // normal
        //    path = path +"_N";
        }

        // upscale
        path = path+tag.getDynamicRange();
        // upsampled
        //path = path+(tag.isUpsampled()?"1":"0");

        File pathFile = new File(dir, path+".png");
        if(!pathFile.exists()) {
        //if(true) {
            // create file
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();

                Bitmap bitmap = createEncodingSamplingRateIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                //if(is!=null) {
                IOUtils.write(is, Files.newOutputStream(pathFile.toPath())); //new FileOutputStream(pathFile));
                //}
            } catch (Exception e) {
                Log.e(TAG,"getEncResolutionIcon",e);
            }
        }
        return pathFile;
    }

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
                dir = pathFile.getParentFile();
                dir.mkdirs();

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

    public static String getTrackDRandGainString(MusicTag tag) {
        String text = "";
        if(tag.getDynamicRangeMeter()==0.00) {
            text = " - ";
        }else {
            text = String.format(Locale.US, "DR%.0f", tag.getDynamicRangeMeter());
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

    public static String getTrackDR(MusicTag tag) {
        String text = "";
        if(tag.getDynamicRangeMeter()==0.00) {
            text = "-";
        }else {
            text = String.format(Locale.US, "%.0f", tag.getDynamicRangeMeter());
        }

        return text;
    }

    public static String getDynamicRangeAsString(MusicTag tag) {
        String text = "";
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
    public static Bitmap getSourceIcon(Context context, MusicTag tag) {
        int borderColor = Color.GRAY; //Color.TRANSPARENT;//Color.GRAY; //context.getColor(R.color.black);
        int qualityColor = Color.TRANSPARENT; //getResolutionColor(context,item); //getSampleRateColor(context,item);
        //String letter = source; //item.getSource();
        String letter = "";
        //if(isEmpty(tag.getPublisher())) {
            letter = tag.getMediaType();
        //}else {
        //    letter = tag.getPublisher();
       // }
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
    public static boolean isOnDownloadDir(MusicTag tag) {
        return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    /*@Deprecated
    public static int getDSDSampleRateModulation(MusicTag tag) {
        return (int) ((tag.getAudioBitRate()/1000.0)/(Constants.QUALITY_SAMPLING_RATE_44/1000.0));
    } */

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
            return Constants.AUDIO_SQ_DSD;
        }else if(isMQA(tag)) {
                  return Constants.AUDIO_SQ_PCM_MQA;
        }else if(isHiRes(tag)) {
            return Constants.TITLE_HIRES;
            //}else if(isPCMHiResLossless(tag)) {
            //    return Constants.TITLE_HR_LOSSLESS;
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

    /*
    public static Bitmap getMediaTypeIcon(Context context, String src) {
        //int borderColor = Color.GRAY; //Color.TRANSPARENT;//Color.GRAY; //context.getColor(R.color.black);
        int qualityColor = Color.TRANSPARENT;

        int size =24;
        int rescId = getSourceRescId(src);
        if(rescId!=-1) {
            return createBitmapFromDrawable(context, size, size, rescId, qualityColor, qualityColor);
        }
        return null;
    } */

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

    public static Object getSourceQualityIconMini(Context context, MusicTag tag) {
        File dir = context.getExternalCacheDir();
        String quality = trimToEmpty(tag.getMediaQuality());
        String path = "/Icons/Quality"+quality+"RecordsMini.png";

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
       // if(true) {
            // create file
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();

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

    private static Bitmap createSourceQualityIconMini(Context context, String quality) {
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
    }

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

    public static boolean isUpSampled(MusicTag tag) {
        return false;
    }

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

    public static boolean isFunPlaylist(MusicTag tag) {
        return ("Luk Thung".equalsIgnoreCase(tag.getGenre()) ||
                "Mor Lum".equalsIgnoreCase(tag.getGenre()));
    }

    public static boolean isRelaxedPlaylist(MusicTag tag) {
        String grouping = StringUtils.trimToEmpty(tag.getGrouping()).toUpperCase();
        return (grouping.contains("LOUNGE") ||
                grouping.contains("ACOUSTIC"));
    }

    public static boolean isHappyPlaylist(MusicTag tag) {
        return !(isRelaxedPlaylist(tag) || isFunPlaylist(tag));
       // return !isFunPlaylist(tag);
    }

    public static boolean isAACFile(MusicTag musicTag) {
        return Constants.MEDIA_ENC_AAC.equalsIgnoreCase(musicTag.getAudioEncoding());
    }
}
