package apincer.android.mmate.ui.widget

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

class RatioSegmentedProgressBarDrawable(
    @ColorInt private val foregroundColor: Int,
    @ColorInt private val backgroundColor: Int,
    segmentValueList: List<Long>,
    private val gapWidth: Float
) : Drawable() {
    private val numOfSegment: Int = segmentValueList.size
    private val paint = Paint()
    private val segment = RectF()
    private val eachRatioList = mutableListOf<Float>()
    private val cumulativeRatioList = mutableListOf<Float>()
    private var sumOfSegmentWidth = 0f
    private val maxValue: Long = segmentValueList.sum()

    init {
        cumulativeRatioList.add(0f)
        segmentValueList.forEachIndexed { index, value ->
            val ratio = value / maxValue.toFloat()
            eachRatioList.add(ratio)
            cumulativeRatioList.add(cumulativeRatioList[index] + ratio)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        sumOfSegmentWidth = bounds.width() - (gapWidth * (numOfSegment - 1))
    }

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val level = level / 10000f
        segment.set(0f, 0f, 0f, bounds.height().toFloat())
        paint.color = foregroundColor

        for (i in 0 until numOfSegment) {
            val segmentWidth = sumOfSegmentWidth * eachRatioList[i]

            segment.set(segment.left, 0f, segment.left + segmentWidth, segment.bottom)
            val segmentLowLevel = cumulativeRatioList[i]
            val segmentHighLevel = cumulativeRatioList[i + 1]
            if (level in segmentLowLevel..segmentHighLevel) {
                val middle = segment.left + (level - segmentLowLevel) * sumOfSegmentWidth
                canvas.drawRect(segment.left, segment.top, middle, segment.bottom, paint)
                paint.color = backgroundColor
                canvas.drawRect(middle, segment.top, segment.right, segment.bottom, paint)
            } else {
                canvas.drawRect(segment, paint)
            }

            segment.offset(segment.width() + gapWidth, 0f)
        }

    }

    override fun setAlpha(alpha: Int) {}


    @Deprecated("Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}
}