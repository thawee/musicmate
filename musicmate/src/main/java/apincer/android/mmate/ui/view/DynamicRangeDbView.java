package apincer.android.mmate.ui.view;

import static apincer.android.mmate.utils.MusicTagUtils.getDRScoreColor;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRange;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeDbColor;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeScore;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trim;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vanniktech.textbuilder.TextBuilder;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;

public class DynamicRangeDbView extends LinearLayout {
    private TextView dynamicRangeDbTextView;

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
        setOrientation(LinearLayout.HORIZONTAL);
    }


    @SuppressLint("CheckResult")
    public void setMusicItem(MusicTag tag) {
        if(tag == null) {
            dynamicRangeDbTextView.setText("-");
        }else {
            int drsColor = getDRScoreColor(getContext(), (int) tag.getDynamicRangeScore());
            String drs = getDynamicRangeScore(tag);
            if(isEmpty(drs)) {
                drs = "--";
            }else {
                drs = "DR"+trim(drs, "");
            }
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText(drs, drsColor);
            builder.addColoredText(" / ", ContextCompat.getColor(getContext(), R.color.grey400));
            //dynamicRangeDbTextView.setText("DR"+drs+", "+getDynamicRange(tag)+" dB");
            int color = getDynamicRangeDbColor(getContext(), tag);
            //dynamicRangeDbTextView.setTextColor(color);
            builder.addColoredText(getDynamicRange(tag)+" dB", color);
            builder.into(dynamicRangeDbTextView);

        }
    }
}