package apincer.android.mmate.ui.view;

import static apincer.android.mmate.utils.MusicTagUtils.getRating;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vanniktech.textbuilder.TextBuilder;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;

public class RatingIndicatorView extends LinearLayout {
    private TextView textView;
    private String mode;

    public RatingIndicatorView(Context context) {
        super(context);
        init(context, null);
    }

    public RatingIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RatingIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_rating_indicator, this, true);

        // Get reference to the TextView
        textView = findViewById(R.id.rating_indicator_text_view);
        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL);

        // Obtain the custom attributes from the XML layout
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.RatingIndicatorView,
                0, 0
        );

        try {
            // Read the string value from the 'label' attribute
            String labelText = typedArray.getString(R.styleable.RatingIndicatorView_mode);

            // Set the text on the internal TextView
            if (labelText != null) {
                mode = labelText;
            }
        } finally {
            // IMPORTANT: Always recycle the TypedArray to free up resources
            typedArray.recycle();
        }
    }

    @SuppressLint("CheckResult")
    public void setMusicItem(MusicTag tag) {
        if("mini".equals(mode)) {
            TextBuilder builder = new TextBuilder(getContext());
            int rating = getRating(tag);
            if(rating >= 3) {
                setVisibility(VISIBLE);
                // builder.addText("\u2764");
                // builder.addText("\u2661");
                // builder.addText("\u1f90d");
                // Just copy and paste the emoji
                builder.addText("🤍");
                builder.into(textView);
            }else {
                setVisibility(GONE);
            }
        }else {
            TextBuilder builder = new TextBuilder(getContext());
            int ratingColor = getContext().getColor(R.color.rating_text);
            int rating = getRating(tag);
            int i = 0;
            for (; i < rating; i++) {
                builder.addColoredText("✭", ratingColor);
            }
            while (i < 5) {
                // builder.addText("*");
                builder.addText("✩");
                i++;
            }
            builder.into(textView);
        }
    }
}