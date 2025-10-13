package apincer.android.mmate.ui.view;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vanniktech.textbuilder.TextBuilder;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;

public class QualityIndicatorView extends LinearLayout {
    private TextView textView;

    public QualityIndicatorView(Context context) {
        super(context);
        init(context, null);
    }

    public QualityIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public QualityIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_quality_indicator, this, true);

        // Get reference to the TextView
        textView = findViewById(R.id.quality_indicator_text_view);
        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL);

        // Obtain the custom attributes from the XML layout
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.QualityIndicatorView,
                0, 0
        );

        try {
            // Read the string value from the 'label' attribute
            String labelText = typedArray.getString(R.styleable.QualityIndicatorView_label);

            // Set the text on the internal TextView
            if (labelText != null) {
                setLabel(labelText);
            }
        } finally {
            // IMPORTANT: Always recycle the TypedArray to free up resources
            typedArray.recycle();
        }
    }

    public void setLabel(String label) {
        if(label != null) {
            int textColor = MusicTagUtils.getQualityTextColor(getContext(), label);
            if(isEmpty(label)) {
                label = "-";
            }else if(label.startsWith("MQA")) {
                label = "MQA";
            }
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText(label, textColor);
            builder.into(textView);
            Drawable background = MusicTagUtils.getQualityBackground(getContext(), label);
            textView.setBackground(background);
        }
    }


    @SuppressLint("CheckResult")
    public void setMusicItem(MusicTag tag) {
        if(tag == null) {
            textView.setText("-");
        }else {
            int textColor = MusicTagUtils.getQualityTextColor(getContext(), tag.getQualityInd());
            String label = tag.getQualityInd();
            if(isEmpty(label)) {
                label = "-";
            }else if(label.startsWith("MQA")) {
                label = "MQA";
            }
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText(label, textColor);
            builder.into(textView);
            Drawable background = MusicTagUtils.getQualityBackground(getContext(), tag.getQualityInd());
            textView.setBackground(background);
        }
    }
}