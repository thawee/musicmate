package apincer.android.mmate.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.github.chrisbanes.photoview.PhotoView;

import apincer.android.mmate.R;

public class AspectRatioPhotoView extends PhotoView {
    private static final int DEFAULT_ASPECT_RATIO = 1;
    private int widthRatio;
    private int heightRatio;
    public AspectRatioPhotoView(Context context) {
        super(context);
    }

    public AspectRatioPhotoView(Context context, AttributeSet attr) {
        super(context, attr);
        init(attr);
    }

    public AspectRatioPhotoView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
        init(attr);
    }
/*
    public AspectRatioPhotoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    } */

    private void init(AttributeSet attrs) {
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.AspectRatioPhotoView);
            widthRatio = typedArray.getInteger(R.styleable.AspectRatioPhotoView_widthRatio, DEFAULT_ASPECT_RATIO);
            heightRatio = typedArray.getInteger(R.styleable.AspectRatioPhotoView_heightRatio, DEFAULT_ASPECT_RATIO);
            typedArray.recycle();
            validateRatio(widthRatio);
            validateRatio(heightRatio);

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        float sizePerRatio = (float) width / (float) widthRatio;
        int height = Math.round(sizePerRatio * heightRatio);
        setMeasuredDimension(width, height);
    }
    private void validateRatio(int ratio) {
        if(ratio <= 0) {
            throw new IllegalArgumentException("ratio > 0");
        }
    }
}
