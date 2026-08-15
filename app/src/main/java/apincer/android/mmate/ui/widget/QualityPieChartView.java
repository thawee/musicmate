package apincer.android.mmate.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class QualityPieChartView extends View {

    public static class PieEntry {
        public String label;
        public float value;
        public int color;
        public PieEntry(String label, float value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final List<PieEntry> entries = new ArrayList<>();
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private float totalValue = 0f;

    public QualityPieChartView(Context context) {
        super(context);
        init();
    }

    public QualityPieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        legendPaint.setStyle(Paint.Style.FILL);
        legendTextPaint.setColor(Color.WHITE);
        legendTextPaint.setTextSize(dpToPx(12));
    }

    public void setEntries(List<PieEntry> newEntries) {
        this.entries.clear();
        this.entries.addAll(newEntries);
        totalValue = 0f;
        for (PieEntry e : entries) {
            totalValue += e.value;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty() || totalValue == 0) return;

        float width = getWidth();
        float height = getHeight();
        
        // Leave room for 2 lines of legend
        float legendAreaHeight = dpToPx(48); 
        float chartAreaHeight = height - legendAreaHeight;
        
        // The thickness of the donut
        float chartSize = Math.min(width, chartAreaHeight) * 0.9f;
        float thickness = chartSize * 0.20f; // 20% of chart size is the ring thickness
        arcPaint.setStrokeWidth(thickness);
        
        float cx = width / 2f;
        float cy = chartAreaHeight / 2f;
        
        // The radius of the stroke's center path
        float radius = (chartSize - thickness) / 2f;
        
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
        
        float startAngle = -90f;
        float gapAngle = 1.5f; // small gap between slices for premium look
        
        for (PieEntry entry : entries) {
            float sweepAngle = (entry.value / totalValue) * 360f;
            if (sweepAngle > gapAngle) {
                arcPaint.setColor(entry.color);
                canvas.drawArc(rectF, startAngle + gapAngle/2, sweepAngle - gapAngle, false, arcPaint);
            } else if (sweepAngle > 0) {
                arcPaint.setColor(entry.color);
                canvas.drawArc(rectF, startAngle, sweepAngle, false, arcPaint);
            }
            startAngle += sweepAngle;
        }
        
        // Center text
        textPaint.setTextSize(dpToPx(14));
        canvas.drawText("Songs", cx, cy - dpToPx(4), textPaint);
        
        textPaint.setTextSize(dpToPx(18));
        textPaint.setFakeBoldText(true);
        DecimalFormat format = new DecimalFormat("#,###");
        canvas.drawText(format.format(totalValue), cx, cy + dpToPx(16), textPaint);
        
        // Draw Legend (simple wrapping layout)
        float legendX = (width - measureLegendWidth()) / 2f; // Center the legend
        if (legendX < dpToPx(16)) legendX = dpToPx(16);
        float legendY = chartAreaHeight + dpToPx(16);
        float boxSize = dpToPx(10);
        
        float startX = legendX;
        for (PieEntry entry : entries) {
            float textWidth = legendTextPaint.measureText(entry.label);
            if (legendX + boxSize + dpToPx(6) + textWidth > width - dpToPx(16)) {
                legendX = startX;
                legendY += dpToPx(20);
            }
            
            legendPaint.setColor(entry.color);
            // Draw rounded rect for legend
            canvas.drawRoundRect(legendX, legendY - boxSize, legendX + boxSize, legendY, dpToPx(2), dpToPx(2), legendPaint);
            canvas.drawText(entry.label, legendX + boxSize + dpToPx(6), legendY - dpToPx(1), legendTextPaint);
            
            legendX += boxSize + dpToPx(6) + textWidth + dpToPx(16);
        }
    }
    
    private float measureLegendWidth() {
        float w = 0;
        float boxSize = dpToPx(10);
        for (PieEntry entry : entries) {
            w += boxSize + dpToPx(6) + legendTextPaint.measureText(entry.label) + dpToPx(16);
        }
        return w - dpToPx(16); // remove last margin
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
