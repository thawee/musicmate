package apincer.android.mmate.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

public class ReflectionContainer extends FrameLayout {
    private Bitmap reflectionBitmap;
    private Canvas reflectionCanvas;
    private Paint maskPaint;
    private Matrix flipMatrix;
    private float reflectionFraction = 0.3f; // Reflect 30% of the height
    private int reflectionGap = 0; // Gap between view and reflection

    public ReflectionContainer(Context context) {
        super(context);
        init();
    }

    public ReflectionContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReflectionContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        flipMatrix = new Matrix();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            
            int reflectHeight = (int) (childHeight * reflectionFraction);
            int totalHeight = childHeight + reflectionGap + reflectHeight;
            
            setMeasuredDimension(resolveSize(childWidth, widthMeasureSpec), resolveSize(totalHeight, heightMeasureSpec));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (getChildCount() > 0) {
            View child = getChildAt(0);
            int w = child.getWidth();
            int h = child.getHeight();
            
            if (w <= 0 || h <= 0) return;

            int reflectHeight = (int) (h * reflectionFraction);

            if (reflectionBitmap == null || reflectionBitmap.getWidth() != w || reflectionBitmap.getHeight() != h) {
                if (reflectionBitmap != null) reflectionBitmap.recycle();
                reflectionBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                reflectionCanvas = new Canvas(reflectionBitmap);
                
                LinearGradient shader = new LinearGradient(0, 0, 0, reflectHeight, 
                        0x60ffffff, 0x00ffffff, Shader.TileMode.CLAMP);
                maskPaint.setShader(shader);
            }

            reflectionBitmap.eraseColor(0x00000000);
            child.draw(reflectionCanvas);

            canvas.save();
            canvas.translate(child.getLeft(), child.getBottom() + reflectionGap);
            
            // Draw into a layer to apply DST_IN blend mode correctly
            int saveCount = canvas.saveLayer(0, 0, w, reflectHeight, null);
            
            // Draw flipped bitmap
            flipMatrix.reset();
            flipMatrix.preScale(1, -1);
            flipMatrix.postTranslate(0, h);
            canvas.drawBitmap(reflectionBitmap, flipMatrix, null);
            
            // Apply gradient mask
            canvas.drawRect(0, 0, w, reflectHeight, maskPaint);
            
            canvas.restoreToCount(saveCount);
            canvas.restore();
        }
    }
}
