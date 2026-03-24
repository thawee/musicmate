package apincer.android.mmate.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * A custom view that renders a mirrored, gradient-filled waveform
 * from normalized audio peak data.
 */
public class WaveformView extends View {
    private float[] waveformData;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float barSpacing = 2f; // Gap between bars in pixels

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Default color if gradient isn't initialized yet
        paint.setColor(0xFFBB86FC);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Updates the waveform data and triggers a redraw.
     * @param data Array of floats from 0.0 to 1.0.
     */
    public void setWaveformData(float[] data) {
        this.waveformData = data;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Create a vertical gradient: Top (Purple) to Center (White) to Bottom (Purple)
        Shader shader = new LinearGradient(0, 0, 0, h,
                new int[]{0xFFBB86FC, 0xFFFFFFFF, 0xFFBB86FC},
                new float[]{0.1f, 0.5f, 0.9f},
                Shader.TileMode.CLAMP);
        paint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (waveformData == null || waveformData.length == 0) return;

        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2f;

        // Calculate the width of each bar based on the number of points
        float barWidth = (width / waveformData.length) - barSpacing;
        paint.setStrokeWidth(barWidth);

        for (int i = 0; i < waveformData.length; i++) {
            float x = i * (barWidth + barSpacing) + (barWidth / 2f);

            // Scaled height (max height is centerY to keep it symmetrical)
            float val = waveformData[i] * (height / 2.2f);

            // Draw mirrored bars (top and bottom)
            // Using lines instead of rects for smoother rounded caps
            canvas.drawLine(x, centerY - val, x, centerY + val, paint);
        }
    }
}