package apincer.android.mmate.ui.view;

import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRange;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeDbBackground;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;

// Assuming your dependencies are set up for these resources
// For example, if R.dimen.dimen_1_dp is from your module.
// Ensure your_package_name.R includes these if they are project specific.
// import your.package_name.R;


public class DynamicRangeDbView extends LinearLayout {

    private TextView dynamicRangeDbTextView;
    private String drDbTextValue;

    public DynamicRangeDbView(Context context) {
        super(context);
        init(context, null);
    }

    public DynamicRangeDbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DynamicRangeDbView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_dynamic_range_db, this, true);

        // Get reference to the TextView
        dynamicRangeDbTextView = findViewById(R.id.dynamic_range_db_text_view);

        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL); // Or an appropriate default if not already set by background/etc.
        // You might need to set gravity for the LinearLayout itself if it's not implied by its children
        // setGravity(Gravity.CENTER_VERTICAL); // Example

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.DynamicRangeDbView,
                    0, 0);

            try {
                drDbTextValue = a.getString(R.styleable.DynamicRangeDbView_drDbText);
                if (drDbTextValue != null) {
                    setText(drDbTextValue);
                }
            } finally {
                a.recycle();
            }
        }
    }

    /**
     * Sets the text for the dynamic range dB value.
     * @param text The text to display (e.g., "90/96 dB").
     */
    public void setText(String text) {
        if (dynamicRangeDbTextView != null) {
            dynamicRangeDbTextView.setText(text);
        }
        this.drDbTextValue = text; // Keep track of the value
    }

    /**
     * Gets the current text of the dynamic range dB value.
     * @return The current text.
     */
    public String getText() {
        return drDbTextValue;
    }

    public void setMusicItem(MusicTag tag) {
        if(tag == null) {
            setText("");
        }else {
            if(tag.getAudioBitsDepth()==16) {
                dynamicRangeDbTextView.setText(getDynamicRange(tag)+"/96 dB");
            }else if(tag.getAudioBitsDepth()==24) {
                dynamicRangeDbTextView.setText(getDynamicRange(tag)+"/144 dB");
            }

            Drawable background = getDynamicRangeDbBackground(getContext(), tag);
            dynamicRangeDbTextView.setBackground(background);
        }
    }
}