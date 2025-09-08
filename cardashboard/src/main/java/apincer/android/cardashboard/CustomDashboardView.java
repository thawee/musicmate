package apincer.android.cardashboard;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A custom View that draws a simple, dynamic gauge.
 * This class demonstrates how to create custom UI elements for the car display.
 */
public class CustomDashboardView extends View {

    private Paint paint;
    private RectF arcBounds;
    private float value;

    public CustomDashboardView(Context context) {
        super(context);
        init();
    }

    public CustomDashboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcBounds = new RectF();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We'll make our view a square for simplicity.
        int size = Math.min(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
        );
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int size = Math.min(w, h);
        int padding = 20;
        arcBounds.set(padding, padding, size - padding, size - padding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the background circle for the gauge
        paint.setColor(Color.parseColor("#424242")); // Dark gray for a sleek look
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - 10, paint);

        // Draw the value arc
        paint.setColor(Color.parseColor("#2196F3")); // Blue for the gauge value
        paint.setStrokeCap(Paint.Cap.ROUND);
        float sweepAngle = value * 360f / 100f; // Assume value is a percentage (0-100)
        canvas.drawArc(arcBounds, 270, sweepAngle, false, paint);

        // Draw the value text
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(60);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.format("%.1f%%", value), getWidth() / 2f, getHeight() / 2f + 20, paint);
    }

    /**
     * Sets the value of the gauge and triggers a redraw.
     * @param value The value to display on the gauge (0-100).
     */
    public void setValue(float value) {
        if (this.value != value) {
            this.value = value;
            invalidate(); // Triggers onDraw to be called again
        }
    }
}