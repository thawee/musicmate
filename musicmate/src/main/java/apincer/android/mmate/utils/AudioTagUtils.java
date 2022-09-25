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

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import timber.log.Timber;

public class AudioTagUtils {

    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, int drawableId, int borderColor, int backgroundColor) {
        Bitmap icon = BitmapHelper.getBitmapFromVectorDrawable(context, drawableId);
        return createBitmapFromDrawable(context,width,height,icon, borderColor, backgroundColor);
    }
/*
    public static Bitmap createBitmapFromDrawable(Context context, int width, int height, Drawable drawable, int borderColor, int backgroundColor) {
        Bitmap icon = BitmapHelper.drawableToBitmap(drawable);
        return createBitmapFromDrawable(context,width,height,icon, borderColor, backgroundColor);
    }*/

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

    /*
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
        Typeface font =  ResourcesCompat.getFont(context, R.font.djb_get_digital_6g5g);
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
    }*/

    /*
    public static Bitmap createBitmapFromTextK2D(Context context, int width, int height, String text, int textColor, int borderColor, int backgroundColor) {

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
        int letterTextSize = 20;
        // int letterTextSize = 18;
        Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
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
    }*/

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

    /*
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
        Typeface font =  ResourcesCompat.getFont(context, R.font.square_sans_serif_7);
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
    }*/

    /*
    public static Bitmap getHiResIcon(Context context, AudioTag tag) {
        if(!(tag.isMQA() || isPCMHiRes(tag) ||tag.isDSD())) {
            return null;
        }
        int width = 340;
        int height = 96;
        int whiteColor = context.getColor(R.color.white);
        int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
       // int qualityColor = getResolutionColor(context,tag);
        String label = "PCM";
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);
        if(tag.isDSD()) {
            label = "DSD";
        }else if(tag.isMQA()) {
            label = "MQA";
            samplingRate = StringUtils.getFormatedAudioSampleRate(parseMQASampleRate(tag.getMQASampleRate()),false);
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 8;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw quality background box
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
       // bgPaint.setColor(qualityColor);
        bgPaint.setColor(greyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        padding = 8;
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/1.8),//padding, // Left
                0, // Top
                myCanvas.getWidth(), // - padding, // Right
                //(myCanvas.getHeight()/2) - 2 // Bottom
                myCanvas.getHeight()-padding
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 52; //28;
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 4f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(label,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.7)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw MQA and MQA studio
        if(tag.isMQA()) {
            int mqaQualityColor = context.getColor(R.color.green);
            if(tag.isMQAStudio()) {
                mqaQualityColor = context.getColor(R.color.blue);
            }
            Paint mqaPaint =  new Paint();
            mqaPaint.setAntiAlias(true);
            mqaPaint.setColor(mqaQualityColor);
            mqaPaint.setStyle(Paint.Style.FILL);
            rectangle = new RectF(
                    (float) (myCanvas.getWidth()/1.8)+20,//padding, // Left
                    (((myCanvas.getHeight()/3) *2)-8), // Top
                    myCanvas.getWidth() - 20, // Right
                    myCanvas.getHeight()-20 // Bottom
            );
            myCanvas.drawRoundRect(rectangle, 12,12, mqaPaint);
        }

        // draw sampling rate, black color
        //font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        letterTextSize = 70; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/6);
        myCanvas.drawText(samplingRate,
                (float) (bounds.exactCenterX()-(bounds.exactCenterX()/2.2)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }*/

    private static Bitmap getEncodingSamplingRateIcon2(Context context, AudioTag tag) {
        int width = 340;
        int height = 96;
        int whiteColor = context.getColor(R.color.white);
        //int greyColor = context.getColor(R.color.grey200);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.black_transparent_80);
        int qualityColor = getResolutionColor(context,tag);
        int mqaColor = context.getColor(R.color.green);
        String label = "PCM";
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);
        if(tag.isDSD()) {
            label = "DSD";
        }else if(tag.isMQA()) {
            label = "MQA";
            samplingRate = StringUtils.getFormatedAudioSampleRate(parseMQASampleRate(tag.getMQASampleRate()),false);
            if(tag.isMQAStudio()) {
                mqaColor = context.getColor(R.color.blue);
            }else {
                mqaColor = context.getColor(R.color.green);
            }
        }else if(!tag.isLossless()) {
            label = tag.getAudioEncoding();
            samplingRate = StringUtils.getFormatedAudioBitRateNoUnit(tag.getAudioBitRate());
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 12;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw quality background box
        RectF rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        // bgPaint.setColor(qualityColor);
        bgPaint.setColor(qualityColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        padding = 8;
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/1.8),//padding, // Left
                0, // Top
                myCanvas.getWidth(), // - padding, // Right
                //(myCanvas.getHeight()/2) - 2 // Bottom
                myCanvas.getHeight()-padding
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 52; //28;
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 4f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(label,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.7)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw MQA and MQA studio
        if(tag.isMQA()) {
            qualityColor = mqaColor;
        }else {
            qualityColor = whiteColor;
        }
            Paint mqaPaint =  new Paint();
            mqaPaint.setAntiAlias(true);
            mqaPaint.setColor(qualityColor);
            mqaPaint.setStyle(Paint.Style.FILL);
            rectangle = new RectF(
                    (float) (myCanvas.getWidth()/1.8)+22,//padding, // Left
                    (((myCanvas.getHeight()/3) *2)-10), // Top
                    myCanvas.getWidth() - 18, // Right
                    myCanvas.getHeight()-22 // Bottom
            );
            myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);
        //}

        // draw sampling rate, black color
        //font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        letterTextSize = 70; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/6);
        myCanvas.drawText(samplingRate,
                (float) (bounds.exactCenterX()-(bounds.exactCenterX()/2.2)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap getEncodingSamplingRateIcon(Context context, AudioTag tag) {
        int width = 340;
        int height = 96;
        int whiteColor = context.getColor(R.color.white);
        int darkGreyColor = context.getColor(R.color.grey900);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.black_transparent_80);
        int qualityColor = getResolutionColor(context,tag);
        int mqaColor = context.getColor(R.color.green);
        String label = "PCM";
        String labelBPS = "";
        boolean longEnc = false;
        //String bpsLabel = String.valueOf(tag.getAudioBitsPerSample());
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);

        if(tag.isDSD()) {
            label = "DSD";
        }else if(tag.isMQA()) {
            label = "MQA";
            samplingRate = StringUtils.getFormatedAudioSampleRate(parseMQASampleRate(tag.getMQASampleRate()),false);
            if(tag.isMQAStudio()) {
                mqaColor = context.getColor(R.color.blue);
            }else {
                mqaColor = context.getColor(R.color.green);
            }
        } else if(tag.isLossless()) {
            //
            label = "PCM"; //+ tag.getAudioBitsPerSample();
            labelBPS = String.valueOf(tag.getAudioBitsPerSample());
            longEnc = true;
        }else {
            // compress media
            label = tag.getAudioEncoding();
            samplingRate = StringUtils.getFormatedAudioBitRateNoUnit(tag.getAudioBitRate());
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
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
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        padding = 4;
        int topPadding = 8;
        rectangle = new RectF(
                padding, // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - topPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        // bgPaint.setColor(qualityColor);
        bgPaint.setColor(qualityColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        padding = 8;
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/1.9),//padding, // Left
                0, // Top
                myCanvas.getWidth(), // - padding, // Right
                //(myCanvas.getHeight()/2) - 2 // Bottom
                myCanvas.getHeight()-padding
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw enc, black color
        // AAC, MPEG, PCM 16, PCM 24, MQA, DSD
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        if(longEnc) {
            // PCM xx
            int letterTextSize = 32; //42;
            int bpsSise = 48;

            // pcm
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(whiteColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height() / 4f;
            float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
            myCanvas.drawText(label,
                    (float) (bounds.exactCenterX()+(bounds.exactCenterX()/2.4)),
                    mLetterTop + mPositionY, //bounds.exactCenterY(),
                    mLetterPaint);

            // bps
            mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(whiteColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setTextSize(bpsSise);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            textMathRect = new Rect();
            mLetterPaint.getTextBounds(labelBPS, 0, 1, textMathRect);
            mLetterTop = textMathRect.height() / 4f;
            mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
            myCanvas.drawText(labelBPS,
                    (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.2)), //left
                    mLetterTop + mPositionY, //bounds.exactCenterY(), // top
                    mLetterPaint);

        }else {
            // MQA, DSD, AAC, MPEG
            int letterTextSize = 48; //28;
            Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mLetterPaint.setColor(whiteColor);
            mLetterPaint.setTypeface(font);
            mLetterPaint.setTextSize(letterTextSize);
            mLetterPaint.setTextAlign(Paint.Align.CENTER);
            // Text draws from the baselineAdd some top padding to center vertically.
            Rect textMathRect = new Rect();
            mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
            float mLetterTop = textMathRect.height() / 4f;
            float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
            myCanvas.drawText(label,
                    (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.7)),
                    mLetterTop + mPositionY, //bounds.exactCenterY(),
                    mLetterPaint);
        }

        // draw quality bar or MQA and MQA studio
        if(tag.isMQA()) {
            qualityColor = mqaColor;
        }else {
            qualityColor = whiteColor;
        }
        Paint mqaPaint =  new Paint();
        mqaPaint.setAntiAlias(true);
        mqaPaint.setColor(qualityColor);
        mqaPaint.setStyle(Paint.Style.FILL);
        float leftRec = (float) (myCanvas.getWidth()/1.8)+28;
        float rightRec = myCanvas.getWidth() - 18;
        if(longEnc) {
            leftRec = (float) (myCanvas.getWidth()/1.8)+12;
            rightRec = myCanvas.getWidth() - 4;
        }
        rectangle = new RectF(
                leftRec,//padding, // Left
                (((myCanvas.getHeight()/3) *2)-4), // Top
                rightRec, // Right
                myCanvas.getHeight()-22 // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);
        //}

        // draw sampling rate, black color
        //font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 64; //82;
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
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }


    private static Bitmap getEncodingSamplingRateIcon3(Context context, AudioTag tag) {
        int width = 340;
        int height = 96;
        int whiteColor = context.getColor(R.color.white);
        int darkGreyColor = context.getColor(R.color.grey900);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.black_transparent_80);
        int qualityColor = getResolutionColor(context,tag);
        int mqaColor = context.getColor(R.color.green);
        String label = "PCM";
        String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);
        if(tag.isDSD()) {
            label = "DSD";
        }else if(tag.isMQA()) {
            label = "MQA";
            samplingRate = StringUtils.getFormatedAudioSampleRate(parseMQASampleRate(tag.getMQASampleRate()),false);
            if(tag.isMQAStudio()) {
                mqaColor = context.getColor(R.color.blue);
            }else {
                mqaColor = context.getColor(R.color.green);
            }
        }else if(!tag.isLossless()) {
            label = tag.getAudioEncoding();
            samplingRate = StringUtils.getFormatedAudioBitRateNoUnit(tag.getAudioBitRate());
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
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
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw quality background box
        padding = 4;
        int topPadding = 8;
        rectangle = new RectF(
                padding, // Left
                topPadding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - topPadding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        // bgPaint.setColor(qualityColor);
        bgPaint.setColor(qualityColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw right back box
        padding = 8;
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/1.8),//padding, // Left
                0, // Top
                myCanvas.getWidth(), // - padding, // Right
                //(myCanvas.getHeight()/2) - 2 // Bottom
                myCanvas.getHeight()-padding
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 52; //28;
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 4f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(label,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.7)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw quality bar or MQA and MQA studio
        if(tag.isMQA()) {
            qualityColor = mqaColor;
        }else {
            qualityColor = whiteColor;
        }
        Paint mqaPaint =  new Paint();
        mqaPaint.setAntiAlias(true);
        mqaPaint.setColor(qualityColor);
        mqaPaint.setStyle(Paint.Style.FILL);
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/1.8)+28,//padding, // Left
                (((myCanvas.getHeight()/3) *2)-4), // Top
                myCanvas.getWidth() - 18, // Right
                myCanvas.getHeight()-22 // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);
        //}

        // draw sampling rate, black color
        //font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        letterTextSize = 70; //82;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 6f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/6);
        myCanvas.drawText(samplingRate,
                (float) (bounds.exactCenterX()-(bounds.exactCenterX()/2.2)),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
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

    public static Drawable getResolutionBackground(Context context, AudioTag tag) {
        // DSD - DSD
        // Hi-Res Lossless - >= 24 bits and >= 48 kHz
        // Lossless - >= 24 bits and >= 48 kHz
        // High Quality - compress
        if(isDSD(tag)) {
            return context.getDrawable(R.drawable.shape_background_hd);
            // }else if(tag.isMQA()) {
            //     return context.getColor(R.color.quality_hd);
        }else if(isPCMHiRes(tag)) {
            return context.getDrawable(R.drawable.shape_background_hd);
        }else if(is24Bits(tag)) {
            return context.getDrawable(R.drawable.shape_background_24bits);
        }else if(isPCMLossless(tag)){
            return context.getDrawable(R.drawable.shape_background_lossless);
        }else {
            return context.getDrawable(R.drawable.shape_background_unknown);
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
/*
    public static Bitmap getSampleRateIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, Constants.INFO_SAMPLE_RATE_WIDTH, Constants.INFO_HEIGHT, tag.getAudioBitCountAndSampleRate(), textColor,borderColor, qualityColor);

        return icon;
    }

    public static Bitmap getAudioResolutionsIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromTextK2D(context, Constants.INFO_RESOLUTIONS_WIDTH, Constants.INFO_RESOLUTIONS_HEIGHT, tag.getAudioResolutions(), textColor,borderColor, qualityColor);

        return icon;
    }

    public static Bitmap getDurationIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 80, Constants.INFO_HEIGHT, tag.getAudioDurationAsString(), textColor,borderColor, qualityColor);

        return icon;
    }
*/

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

    public static File getCachedCoverArt( Context context, AudioTag tag) {
        //PH|SD_AR|R_album|filename.png
        File dir = context.getExternalCacheDir();
        String sid = tag.getStorageId();
        String path = FileSystem.getStorageName(sid);
        if(!StringUtils.isEmpty(tag.getAlbumArtist())) {
            path = path +"_"+StringUtils.trimToEmpty(tag.getAlbumArtist());
        }else {
            path = path +"_"+StringUtils.trimToEmpty(tag.getArtist());
        }
        if(!StringUtils.isEmpty(tag.getAlbum()))  {
            path = path +"_"+StringUtils.trimToEmpty(tag.getAlbum())+".png";
        }else {
            path = path +"_"+StringUtils.trimToEmpty(tag.getTitle())+".png";
        }
        path = path.replaceAll("/", "_");
        path = "/CoverArts/"+path;

        File pathFile = new File(dir, path);
        if(!pathFile.exists()) {
            // create file
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();

                byte []is = AudioFileRepository.getArtworkAsByte(tag);
                if(is!=null) {
                    IOUtils.write(is, new FileOutputStream(pathFile));
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return pathFile;
    }

    public static File getCachedEncResolutionIcon( Context context, AudioTag tag) {
        //Resolution_ENC_samplingrate|bitrate.png
        File dir = context.getExternalCacheDir();
        String path = "/Icons/";

        if(tag.isDSD()) {
            path = path+"DSD_" + tag.getAudioSampleRate();
        }else if(tag.isMQA()) {
            path = path+"MQA";
            if (tag.isMQAStudio()) {
                path = path + "Studio";
            }
            path = path + "_" + tag.getAudioSampleRate();
        }else if(tag.isLossless()){
            path = path + "PCM"+tag.getAudioBitsPerSample()+"_" + tag.getAudioSampleRate();
        }else {
            path = path + tag.getAudioEncoding()+"_" + tag.getAudioBitRate();
        }

        File pathFile = new File(dir, path+".png");
        if(!pathFile.exists()) {
        //if(true) {
            // create file
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();

                Bitmap bitmap = getEncodingSamplingRateIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                if(is!=null) {
                    IOUtils.write(is, new FileOutputStream(pathFile));
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return pathFile;
    }

    /*
    private static File getCachedLoudnessIcon( Context context, AudioTag tag) {
        //LUFS_LRA_Integrated.png
        File dir = context.getExternalCacheDir();
        String path = "/Icons/LUFS";
        path = path+"_" + StringUtils.trim(tag.getLoudnessRange(),"_");
        path = path+"_"+StringUtils.trim(tag.getLoudnessIntegrated(),"_");
        path = path+"_"+StringUtils.trim(tag.getLoudnessTruePeek(),"_");

        File pathFile = new File(dir, path+".png");
        if(!pathFile.exists()) {
            // create file
            try {
                dir = pathFile.getParentFile();
                dir.mkdirs();

                Bitmap bitmap = getLoudnessIcon(context, tag);
                byte []is = BitmapHelper.convertBitmapToByteArray(bitmap);
                if(is!=null) {
                    IOUtils.write(is, new FileOutputStream(pathFile));
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return pathFile;
    } */

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

    /*
    public static Bitmap getFileSizeIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.black);
        int textColor = context.getColor(R.color.black);
        int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 100, 32, StringUtils.formatStorageSize(tag.getFileSize()), textColor,borderColor, qualityColor);

        return icon;
    } */

    public static int getSourceRescId(String letter) {
        //String letter = item.getSource();
        if (StringUtils.isEmpty(letter)) {
            letter = Constants.SRC_NONE;
        }
        if (letter.equalsIgnoreCase(Constants.SRC_JOOX)) {
            return R.drawable.icon_joox;
        } else if (letter.equalsIgnoreCase(Constants.SRC_QOBUZ)) {
            return R.drawable.icon_qobuz;
        } else if (letter.equalsIgnoreCase(Constants.SRC_CD)) { // || letter.equalsIgnoreCase(Constants.SRC_CD_LOSSLESS)) {
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
    public static Bitmap getSourceIcon(Context context, String source) {
        int borderColor = Color.GRAY; //Color.TRANSPARENT;//Color.GRAY; //context.getColor(R.color.black);
        int qualityColor = Color.TRANSPARENT; //getResolutionColor(context,item); //getSampleRateColor(context,item);
        String letter = source; //item.getSource();
        if(StringUtils.isEmpty(letter)) {
            letter = Constants.SRC_NONE;
        }
        int size =24;
        int rescId = getSourceRescId(letter);
        if(Constants.SRC_NONE.equals(letter)) {
           return null;
        }else if(rescId ==-1) {
            int width = 48;
            int height = 48;
            int whiteColor = Color.WHITE;
            if(letter.equalsIgnoreCase(Constants.SRC_HD_TRACKS)) {
                letter = "HD";
            }else if(letter.equalsIgnoreCase(Constants.SRC_2L)) {
                letter = "2L";
            }else if(letter.equalsIgnoreCase(Constants.SRC_NATIVE_DSD)) {
                letter = "ND";
            }else if(letter.equalsIgnoreCase(Constants.SRC_ONKYO)) {
                letter = "e";
            }else {
                letter = StringUtils.getChars(letter, 1);
            }
            return createButtonFromText (context, width, height, letter, whiteColor, borderColor,qualityColor);
        }else {
            return createBitmapFromDrawable(context, size, size,rescId,qualityColor, qualityColor);
        }
    }
    public static boolean isOnDownloadDir(AudioTag tag) {
        return (!tag.getPath().contains("/Music/")) || tag.getPath().contains("/Telegram/");
    }

    private static long parseMQASampleRate(String mqaRate) {
        if(StringUtils.isDigitOnly(mqaRate)) {
            return Long.parseLong(mqaRate);
        }
        return 0;
    }

    public static int getDSDSampleRateModulation(AudioTag tag) {
        return (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);
    }

        public static String getTrackQuality(AudioTag tag) {
        if(tag.isDSD()) {
            return Constants.TITLE_DSD_AUDIO;
        }else if(tag.isMQAStudio()) {
            return Constants.TITLE_MASTER_STUDIO_AUDIO;
        }else if(tag.isMQA()) {
            return Constants.TITLE_MASTER_AUDIO;
        }else if(isPCMHiRes(tag)) {
            return Constants.TITLE_HIRES;
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
            return "Enjoy rich music which the detail and wide range of the music, its warm tone is very enjoyable.";
        }else if(tag.isMQAStudio()) {
            return "Enjoy the original recordings, directly from mastering engineers, producers or artists to their listeners.";
        }else if(tag.isMQA()) {
            return "Enjoy the original recordings, directly from the master recordings, in the highest quality.";
        }else if(isPCMHiRes(tag)) {
            return "Enjoy rich music which reproduces fine details of musical instruments.";
        }else if(isPCMLossless(tag)) {
            return "Enjoy music which reproduce details of music smooth as CD quality that you can hear.";
        }else {
            return "Enjoy music which compromise between data usage and sound fidelity.";
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
            defaultAlbum = "Single"; //getFirstArtist(tag.getArtist())+" - Single";
        }else {
            defaultAlbum = StringUtils.trimToEmpty(tag.getAlbum());
        }
        return defaultAlbum;
    }

    /*
    private static String getFirstArtist(String artist) {
        if(artist.indexOf(";")>0) {
            return artist.substring(0,artist.indexOf(";"));
        }else if(artist.indexOf(",")>0) {
            return artist.substring(0,artist.indexOf(","));
        }
        return artist;
    }*/

    /*
    public static Bitmap getBitsPerSampleIcon(Context context,  AudioTag tag) {
        int width = 132; //128;
        int height = 96;
       // int borderColor = context.getColor(R.color.grey400);
      //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
     //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String bps = "";
        String samplingRate= "";
        if(tag.isDSD()) {
            int rateModulation = (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);
             bps = "DSD";
             samplingRate = "x"+rateModulation;
        }else {
            // bps = StringUtils.getFormatedBitsPerSample(tag.getAudioBitsPerSample());
            bps = tag.getAudioBitsPerSample()+" B";
            //samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(tag.getAudioSampleRate());
            samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(), true);
        }
        // samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),true);
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
        Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_bold);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize+2);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(bps, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 10f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(bps,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw sampling rate, white color
        font =  ResourcesCompat.getFont(context, R.font.adca);
        //font =  ResourcesCompat.getFont(context, R.font.fff_forward);
        letterTextSize = 30;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(samplingRate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/2.5));
        myCanvas.drawText(samplingRate,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    }*/

    public static Bitmap getLoudnessIcon(Context context,  AudioTag tag) {
        int width = 340; // for xx
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey900);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.grey200);
        String lra = StringUtils.trim(tag.getLoudnessRange(),"--");
        String il= StringUtils.trim(tag.getLoudnessIntegrated(),"--");
        String tp= StringUtils.trim(tag.getLoudnessTruePeek(),"--");
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
        int padding = 0;
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
                (float) (bounds.left+ (bounds.exactCenterX()/3)), //left
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
        mPositionY = (float) (bounds.exactCenterY());
        myCanvas.drawText(tp,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()*0.66)), //left
                (float) (bounds.top+mPositionY)+16, // top
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
        mPositionY= (float) (bounds.exactCenterY());
        myCanvas.drawText(il,
                (float) (bounds.exactCenterX())-4, //left
                (float) (bounds.top+mPositionY +mLetterTop)+16, // top
                mLetterPaint);

        return myBitmap;
    }

    public static Bitmap getLoudnessIcon4(Context context,  AudioTag tag) {
        int width = 340; // for xx
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey400);
        // int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.black_transparent_25);
        int qualityColor = context.getColor(R.color.grey200);; //context.getColor(R.color.material_color_green_800); //getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String lra = StringUtils.trim(tag.getLoudnessRange(),"--");
        String il= StringUtils.trim(tag.getLoudnessIntegrated(),"--");
        String tp= StringUtils.trim(tag.getLoudnessTruePeek(),"--");
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
        int padding = 0;
        int cornerRadius = 8;
        int overflowRadius = 12;
        int bottomMargin = 18;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint paint;
        RectF rectangle;

        // draw big black box
        padding = 2;
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
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw LRA grey box
        int jointCornerRadius = 14;
        padding = 8;
        rectangle = new RectF(
                0,  // Left
                padding, // Top
                (float) (myCanvas.getWidth()/3) - padding, // Right
                (float) (myCanvas.getHeight() - (bottomMargin+padding)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, overflowRadius,overflowRadius, paint);

        // draw True Peak grey box
        padding =8;
        rectangle = new RectF(
                ((myCanvas.getWidth()/3)*2)+padding, // Left
                padding, // Top
                (myCanvas.getWidth()), // Right
                (float) (myCanvas.getHeight() - (bottomMargin+padding)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        // draw Integrated grey box
        padding =8;
        rectangle = new RectF(
                (myCanvas.getWidth()/3), // Left
                padding, // Top
                (float) (myCanvas.getWidth()/3) * 2, // Right
                (float) (myCanvas.getHeight() - padding) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(qualityColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        // draw bottom bar
        padding =8;
        rectangle = new RectF(
                0, // Left
                (float) (myCanvas.getHeight() - (bottomMargin)), // Top
                (float) myCanvas.getWidth(), // Right
                (float) (myCanvas.getHeight()) // Bottom
        );
        // int borderWidth = 2;
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
                (float) (bounds.left+ (bounds.exactCenterX()/3)), //left
                bounds.top+mPositionY +mLetterTop, //top
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
        mPositionY = (float) (bounds.exactCenterY());
        myCanvas.drawText(tp,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()*0.68)), //left
                (float) (bounds.top+mPositionY)+12, // top
                mLetterPaint);
/*
        // draw true peak label, black color
        String tpLabel ="T P";
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(24);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(tpLabel, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY = (float) (bounds.exactCenterY()*1.4);
        myCanvas.drawText(tpLabel,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX())*0.3), //left
                (float) (bounds.top+mPositionY +mLetterTop), // top
                mLetterPaint);*/

        // draw integrated loudness text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(il, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY= (float) (bounds.exactCenterY());
        myCanvas.drawText(il,
                (float) (bounds.exactCenterX())-4, //left
                (float) (bounds.top+mPositionY +mLetterTop)+8, // top
                mLetterPaint);

        /*
        // draw integrated loudness value, white color
        font =  ResourcesCompat.getFont(context, R.font.adca);
        String unit = "L   U   F   S";
        letterTextSize = 32;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(unit, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/1.6));
        myCanvas.drawText(unit,
                (float) (bounds.left+ (bounds.exactCenterX())), //left
                mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                mLetterPaint);

         */

        return myBitmap;
    }

    public static Bitmap getLoudnessIcon3(Context context,  AudioTag tag) {
        int width = 296; // for 48
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey400);
        // int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        int bgBlackColor = context.getColor(R.color.black_transparent_25);
        int qualityColor = context.getColor(R.color.grey200);; //context.getColor(R.color.material_color_green_800); //getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String lra = StringUtils.trim(tag.getLoudnessRange(),"--");
        String il= StringUtils.trim(tag.getLoudnessIntegrated(),"--");
        String tp= StringUtils.trim(tag.getLoudnessTruePeek(),"--");
        if(!"--".equalsIgnoreCase(il)) {
            try {
                double intg =NumberFormat.getInstance().parse(il).doubleValue();
                if(intg < -23.0) {
                    // for studio -23 or less
                    qualityColor = context.getColor(R.color.white);
                }else if (intg <= -11.0) {
                    // for streaming -11 to -23
                    // spotify, -14 lufs
                    // apple music, -16 lufs
                    qualityColor = context.getColor(R.color.grey200);
                }else {
                    qualityColor = context.getColor(R.color.warningColor);
                }
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 8;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        Paint paint;
        RectF rectangle;

        // Initialize a new Round Rect object
        // draw grey border
        /*
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

         */

        // draw big black box
        padding = 2;
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
        paint.setColor(bgBlackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);


        /*
        // draw LRA grey box
        int jointCornerRadius = 14;
       // padding = 8;
        rectangle = new RectF(
                padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth()/2)-12, // + padding, //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);
         */

        // draw LRA grey box
        int jointCornerRadius = 14;
        // padding = 8;
        rectangle = new RectF(
                padding, //(myCanvas.getWidth()/2) + padding, // Left
                (myCanvas.getHeight()/3), // Top
                (float) (myCanvas.getWidth()/2) -8, // + padding, //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - 4) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(darkGreyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        /*
        // draw Integrated grey box
        rectangle = new RectF(
                (myCanvas.getWidth()/2) + 4,//padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth() - padding), //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

         */


        // draw True Peak grey box
        rectangle = new RectF(
                (myCanvas.getWidth()/2) + 8,//padding, //(myCanvas.getWidth()/2) + padding, // Left
                (myCanvas.getHeight()/3), // Top
                (myCanvas.getWidth() - 4), //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - 4) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(darkGreyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        // draw Integrated grey box
        rectangle = new RectF(
                (myCanvas.getWidth()/3),//padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (float) (myCanvas.getWidth() - (myCanvas.getWidth()/4)), //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(qualityColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 50; //28;
        padding = 4;

        /*
        // draw loudness quality,
        Paint mqaPaint =  new Paint();
        mqaPaint.setAntiAlias(true);
        mqaPaint.setColor(qualityColor);
        mqaPaint.setStyle(Paint.Style.FILL);
        rectangle = new RectF(
                padding, //(float) (myCanvas.getWidth()/2)-10,//padding, // Left
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.8)), // Top
                myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - padding) // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);

         */

        // draw LRA text, black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(lra, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height()); // / 2.5f);
        float mPositionY= (float) (bounds.exactCenterY() *0.7);
        myCanvas.drawText(lra,
                (float) (bounds.left+ (bounds.exactCenterX()/2.5))-6, //left
                bounds.top+mPositionY +mLetterTop+6, //top
                mLetterPaint);

        // draw true peak text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(tp, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY = (float) (bounds.exactCenterY()*1.5);
        myCanvas.drawText(tp,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()*0.7)), //left
                (float) (bounds.top+mPositionY +mLetterTop), // top
                mLetterPaint);

        // draw true peak label, black color
        String tpLabel ="T P";
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(24);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(tpLabel, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY = (float) (bounds.exactCenterY()*1.4);
        myCanvas.drawText(tpLabel,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX())*0.3), //left
                (float) (bounds.top+mPositionY +mLetterTop), // top
                mLetterPaint);

        // draw integrated loudness text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(il, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        mPositionY= (float) (bounds.exactCenterY() *0.7);
        myCanvas.drawText(il,
                (float) (bounds.exactCenterX()) +12, //-(bounds.exactCenterX()/8)), //left
                (float) (bounds.top+mPositionY +mLetterTop+8), // top
                mLetterPaint);

        /*
        // draw integrated loudness value, white color
        font =  ResourcesCompat.getFont(context, R.font.adca);
        String unit = "L   U   F   S";
        letterTextSize = 32;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(unit, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/1.6));
        myCanvas.drawText(unit,
                (float) (bounds.left+ (bounds.exactCenterX())), //left
                mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                mLetterPaint);

         */

        return myBitmap;
    }

    private static Bitmap getLoudnessIcon1(Context context,  AudioTag tag) {
        int width = 288; // for 48
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey400);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.grey400);; //context.getColor(R.color.material_color_green_800); //getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String range = StringUtils.trim(tag.getLoudnessRange(),"--");
        String integrated= StringUtils.trim(tag.getLoudnessIntegrated(),"--");
        if(!"--".equalsIgnoreCase(integrated)) {
            try {
                double intg =NumberFormat.getInstance().parse(integrated).doubleValue();
                //int intg = Integer.parseInt(integrated);
                if(intg < -23.0) {
                    // for studio -23 or less
                    qualityColor = context.getColor(R.color.grey600);
                }else if (intg <= -11.0) {
                    // for streaming -11 to -23
                    // spotify, -14 lufs
                    // apple music, -16 lufs
                    qualityColor = context.getColor(R.color.grey400);
                }else {
                    qualityColor = context.getColor(R.color.meteial_color_deep_orange_A400);
                }
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 8;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw grey border
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw big black box
        padding = 2;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth()- padding, // Right
                (myCanvas.getHeight() - padding) // Bottom
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw LRA grey box
        int jointCornerRadius = 14;
        // padding = 8;
        rectangle = new RectF(
                padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth()/2)-12, // + padding, //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw Integrated grey box
        rectangle = new RectF(
                (myCanvas.getWidth()/2) + 4,//padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth() - padding), //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 50; //28;
        padding = 4;

        // draw loudness quality,
        Paint mqaPaint =  new Paint();
        mqaPaint.setAntiAlias(true);
        mqaPaint.setColor(qualityColor);
        mqaPaint.setStyle(Paint.Style.FILL);
        rectangle = new RectF(
                padding, //(float) (myCanvas.getWidth()/2)-10,//padding, // Left
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.8)), // Top
                myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - padding) // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);

        // draw LRA text, black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(range, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height() / 2.5f);
        float mPositionY= (float) (bounds.exactCenterY()/1.5);
        myCanvas.drawText(range,
                bounds.left+ (bounds.exactCenterX()/2)-12, //left
                bounds.top+mPositionY +mLetterTop, //top
                mLetterPaint);

        // draw integrated loudness text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(integrated, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        //mPositionY = (float) (bounds.exactCenterY()/1.5);
        myCanvas.drawText(integrated,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.8)), //left
                (float) (bounds.top+mPositionY +mLetterTop+(padding*2.5)), // top
                mLetterPaint);

        // draw integrated loudness value, white color
        font =  ResourcesCompat.getFont(context, R.font.adca);
        String unit = "L   U   F   S";
        letterTextSize = 32;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(unit, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/1.6));
        myCanvas.drawText(unit,
                (float) (bounds.left+ (bounds.exactCenterX())), //left
                mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                mLetterPaint);

        return myBitmap;
    }

    private static Bitmap getLoudnessIcon2(Context context,  AudioTag tag) {
        int width = 288; // for 48
        int height = 96; // 16
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey400);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.grey400);; //context.getColor(R.color.material_color_green_800); //getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String range = StringUtils.trim(tag.getLoudnessRange(),"--");
        String integrated= StringUtils.trim(tag.getLoudnessIntegrated(),"--");
        if(!"--".equalsIgnoreCase(integrated)) {
            try {
                double intg =NumberFormat.getInstance().parse(integrated).doubleValue();
                //int intg = Integer.parseInt(integrated);
                if(intg < -16.0) {
                    // for studio, -16 to -23
                    qualityColor = context.getColor(R.color.blue);
                }else if (intg <= -8.0) {
                    // for streaming -9 to -16
                    // spotify, -14 lufs
                    // apple music, -16 lufs
                    //
                    qualityColor = context.getColor(R.color.green);
                }
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 8;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw grey border
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw big black box
        padding = 2;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth()- padding, // Right
                (myCanvas.getHeight() - padding) // Bottom
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw LRA grey box
        int jointCornerRadius = 14;
        // padding = 8;
        rectangle = new RectF(
                padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth()/2)-24, // + padding, //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw Integrated grey box
        rectangle = new RectF(
                (myCanvas.getWidth()/2) + 24,//padding, //(myCanvas.getWidth()/2) + padding, // Left
                padding, // Top
                (myCanvas.getWidth() - padding), //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.7)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, cornerRadius,jointCornerRadius, paint);

        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        int letterTextSize = 50; //28;
        padding = 4;

        // draw loudness quality,
        Paint mqaPaint =  new Paint();
        mqaPaint.setAntiAlias(true);
        mqaPaint.setColor(qualityColor);
        mqaPaint.setStyle(Paint.Style.FILL);
        rectangle = new RectF(
                (float) (myCanvas.getWidth()/2)-10,//padding, // Left
                8, // Top
                (myCanvas.getWidth()/2) + 10, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2.4)) // Bottom
        );
        myCanvas.drawRoundRect(rectangle, 8,8, mqaPaint);

        // draw LRA text, black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(range, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height() / 2.5f);
        float mPositionY= (float) (bounds.exactCenterY()/1.5);
        myCanvas.drawText(range,
                bounds.left+ (bounds.exactCenterX()/2)-12, //left
                bounds.top+mPositionY +mLetterTop, //top
                mLetterPaint);

        // draw integrated loudness text, black color
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(integrated, 0, 1, textMathRect);
        mLetterTop = textMathRect.height(); // /1.5f;
        //mPositionY = (float) (bounds.exactCenterY()/1.5);
        myCanvas.drawText(integrated,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/1.8)), //left
                (float) (bounds.top+mPositionY +mLetterTop+(padding*2.5)), // top
                mLetterPaint);

        // draw integrated loudness value, white color
        font =  ResourcesCompat.getFont(context, R.font.adca);
        String unit = "L   U   F   S";
        letterTextSize = 32;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(unit, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/1.6));
        myCanvas.drawText(unit,
                (float) (bounds.left+ (bounds.exactCenterX())), //left
                mLetterTop + mPositionY, //bounds.exactCenterY(), //top
                mLetterPaint);

        return myBitmap;
    }

    /*
    private static Bitmap getLoudnessIcon2(Context context,  AudioTag tag) {
        int width = 156; //132;
        int height = 96;
        int greyColor = context.getColor(R.color.grey200);
        int darkGreyColor = context.getColor(R.color.grey400);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        int qualityColor = context.getColor(R.color.material_color_green_800); //getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String range = StringUtils.trim(tag.getLoudnessRange(),"--");
        String integrated= StringUtils.trim(tag.getLoudnessIntegrated(),"--");

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 0;
        int cornerRadius = 8;
        Rect bounds = new Rect(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        // Initialize a new Round Rect object
        // draw big grey box
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw left black box
        padding = 2;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth()- padding, // Right
                (myCanvas.getHeight() - padding) // Bottom
        );
        // int borderWidth = 2;
        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(blackColor);
        paint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        // draw LRA grey box
        padding = 2;
        int greyCornerRadius = 12;
        rectangle = new RectF(
                padding*4, //(myCanvas.getWidth()/2) + padding, // Left
                padding*4, // Top
                (myCanvas.getWidth()/2) + padding, //myCanvas.getWidth() - padding, // Right
                (float) (myCanvas.getHeight() - (myCanvas.getHeight()/2)) // Bottom
        );
        // int borderWidth = 2;
        paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(greyColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        myCanvas.drawRoundRect(rectangle, greyCornerRadius,greyCornerRadius, paint);

        int letterTextSize = 36; //28;
        //Typeface font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);

        // draw LRA , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(qualityColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        padding = 4;
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(range, 0, 1, textMathRect);
        float mLetterTop = (textMathRect.height() / 2f);
        float mPositionY= (float) (bounds.exactCenterY()/1.5);
        myCanvas.drawText(range,
                bounds.left+ (bounds.exactCenterX()/2)+padding,
                bounds.top+mPositionY+(padding*3), //bounds.exactCenterY(),
                mLetterPaint);

        // draw integrated loudness unit , white color
        //font =  ResourcesCompat.getFont(context, R.font.adca_font);
        font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        String unit = "LUFS";
        letterTextSize = 22;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(unit, 0, 1, textMathRect);
        // mLetterTop = textMathRect.height() / 10f;
        mPositionY= bounds.exactCenterY();
        myCanvas.drawText(unit,
                (float) (bounds.exactCenterX()+(bounds.exactCenterX()/2)),
                (float) (mPositionY-(mPositionY/3)), //bounds.exactCenterY(),
                mLetterPaint);

        // draw integrated loudness value, white color
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        letterTextSize = 32;
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(integrated, 0, 1, textMathRect);
        mLetterTop = (textMathRect.height() / 2f);
        mPositionY= (float) (bounds.exactCenterY()+(bounds.exactCenterY()/1.5));
        myCanvas.drawText(integrated,
                (float) (bounds.left+ (bounds.exactCenterX())),
                mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        return myBitmap;
    } */

    /*
    public static Bitmap getHiResDSDIcon(Context context,  AudioTag tag) {
        if(!tag.isDSD()) return null;
        int width = 128;
        int height = 96;
        // int borderColor = context.getColor(R.color.grey400);
        //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String label = "DSD";
        int rateModulation = (int) (tag.getAudioSampleRate()/Constants.QUALITY_SAMPLING_RATE_44);
        String samplingRate = "x "+rateModulation;

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
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 8f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(label,
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
    } */

    /*
    public static Bitmap getHiResPCMIcon(Context context,  AudioTag tag) {
        if(tag.isMQA() || tag.isDSD()) return null;
        int width = 128;
        int height = 96;
        // int borderColor = context.getColor(R.color.grey400);
        //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String label = "PCM";
        String samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(tag.getAudioSampleRate());

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
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(label, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 8f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(label,
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
    }*/

    /*
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
        String samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(tag.getAudioSampleRate());
        if(tag.isMQAStudio()) {
            samplingRate = "Studio";
        }else {
            String mqaRate = tag.getMQASampleRate();
            long rate = AudioTagUtils.parseMQASampleRate(mqaRate);
            if (rate >0) {
                samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(rate);
            }
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
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

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
    } */
/*
    public static Bitmap getAudiophileIcon(Context context, AudioTag tag) {
        int borderColor = context.getColor(R.color.grey400);
        int textColor = context.getColor(R.color.black);
       // int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        int qualityColor = context.getColor(R.color.audiophile_background);
        Bitmap icon = AudioTagUtils.createBitmapFromText(context, 128, 36, "Audiophile", textColor,borderColor, qualityColor);

        return icon;
    } */

    public static boolean isOnPrimaryStorage(Context context, AudioTag tag) {
        return StorageId.PRIMARY.equals(DocumentFileCompat.getStorageId(context, tag.getPath()));
    }

    public static String getEncodingType(AudioTag tag) {
        if(tag.isDSD()) {
            return Constants.AUDIO_SQ_DSD;
        }else if(tag.isMQA()) {
                  return Constants.AUDIO_SQ_PCM_MQA;
        }else if(isPCMHiRes(tag)) {
            return Constants.TITLE_HIRES;
            //}else if(isPCMHiResLossless(tag)) {
            //    return Constants.TITLE_HR_LOSSLESS;
        }else if(isPCMLossless(tag)) {
            return Constants.TITLE_HIFI_LOSSLESS;
        }else {
            return Constants.TITLE_HIFI_QUALITY;
        }
    }

    /*
    private static Bitmap getEncodingSamplingRateIcon2(Context context, AudioTag tag) {
        int width = 128;
        int height = 96;
        // int borderColor = context.getColor(R.color.grey400);
        //  int textColor = context.getColor(R.color.black);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        //   int qualityColor = getResolutionColor(context,tag); //getSampleRateColor(context,item);
        String encoding = "";
        String samplingRate = "";
        if(tag.isDSD()) {
            int rateModulation = (int) (tag.getAudioSampleRate() / Constants.QUALITY_SAMPLING_RATE_44);
            encoding = "DSD" + rateModulation;
            samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(tag.getAudioSampleRate());
            //samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),true);
        } else if(tag.isMQA()) {
            // MQA
            encoding = "MQA";
            long mqaRate = parseMQASampleRate(tag.getMQASampleRate());
            samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(mqaRate);
            //if(mqaRate == tag.getAudioSampleRate()) {
            //    samplingRate = tag.getAudioBitsPerSample()+"/"+StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);
            //}else {
            //samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(), false) + "/" + StringUtils.getFormatedAudioSampleRate(mqaRate, false);
            //}
        }else {
            encoding = tag.getAudioEncoding();
           // samplingRate = tag.getAudioBitsPerSample()+"/"+StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),false);
            samplingRate = StringUtils.getFormatedAudioSampleRateAbvUnit(tag.getAudioSampleRate());
        }
        //String samplingRate = StringUtils.getFormatedAudioSampleRate(tag.getAudioSampleRate(),true);
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
        //Typeface font =  ResourcesCompat.getFont(context, R.font.adca_font);
        Typeface font =  ResourcesCompat.getFont(context, R.font.oswald_bold);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(encoding, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 10f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(encoding,
                bounds.exactCenterX(), mLetterTop + mPositionY, //bounds.exactCenterY(),
                mLetterPaint);

        // draw sampling rate, white color
        //font =  ResourcesCompat.getFont(context, R.font.led_font);
        font =  ResourcesCompat.getFont(context, R.font.k2d_bold);
        letterTextSize = 30;
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
    }*/

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
