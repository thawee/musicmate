package apincer.android.mmate.coil;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import coil.size.Size;
import coil.transform.Transformation;
import kotlin.coroutines.Continuation;

public class ReflectionTransformation implements Transformation {
    @NonNull
    @Override
    public String getCacheKey() {
        return ReflectionTransformation.class.getName();
    }

    @Nullable
    @Override
    public Bitmap transform(@NonNull Bitmap bitmap, @NonNull Size size, @NonNull Continuation<? super Bitmap> continuation) {
        // gap space between original and reflected
        final int reflectionGap = 1;
        // get image size
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // this will not scale but will flip on the Y axis
        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);

        // create a Bitmap with the flip matrix applied to it.
        // we only want the bottom half of the image
        int newHeight = height/ 2;
        Bitmap reflectionImage = Bitmap.createBitmap(bitmap, 0, newHeight,
                width, newHeight, matrix, false);

        // create a new bitmap with same width but taller to fit reflection
        Bitmap bitmapWithReflection = Bitmap.createBitmap(width,
                (height + height), Bitmap.Config.ARGB_8888);

        // create a new Canvas with the bitmap that's big enough for
        // the image plus gap plus reflection
        Canvas canvas = new Canvas(bitmapWithReflection);
        // draw in the original image
        canvas.drawBitmap(bitmap, 0, 0, null);
        // draw in the gap
        Paint defaultPaint = new Paint();
        canvas.drawRect(0, height, width, height + reflectionGap, defaultPaint);
        // draw in the reflection
        canvas.drawBitmap(reflectionImage, 0, height + reflectionGap, null);

        // create a shader that is a linear gradient that covers the reflection
        Paint paint = new Paint();
        LinearGradient shader = new LinearGradient(0, bitmap.getHeight(), 0,
                bitmapWithReflection.getHeight() + reflectionGap, 0x70ffffff,
                0x00ffffff, Shader.TileMode.CLAMP);
        // set the paint to use this shader (linear gradient)
        paint.setShader(shader);
        // set the Transfer mode to be porter duff and destination in
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        // draw a rectangle using the paint with our linear gradient
        canvas.drawRect(0, height, width, bitmapWithReflection.getHeight()
                + reflectionGap, paint);

        return bitmapWithReflection;
    }
}
