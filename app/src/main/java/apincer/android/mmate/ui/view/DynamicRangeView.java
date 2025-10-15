package apincer.android.mmate.ui.view;

import static apincer.music.core.utils.TagUtils.getDynamicRangeScore;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trim;
import static apincer.android.mmate.utils.MusicTagUtils.getDRScoreColor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vanniktech.textbuilder.TextBuilder;

import apincer.android.mmate.R;
import apincer.music.core.database.MusicTag;

public class DynamicRangeView extends LinearLayout {
    private TextView dynamicRangeDbTextView;

    public DynamicRangeView(Context context) {
        super(context);
        init(context, null);
    }

    public DynamicRangeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DynamicRangeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
           // setVisibility(GONE);
        }else {
            int drsColor = getDRScoreColor(getContext(), (int) tag.getDynamicRangeScore());
            String drs = getDynamicRangeScore(tag);
            if(isEmpty(drs)) {
                drs = " -- ";
               // setVisibility(VISIBLE);
               // return;
            }else {
                drs = "DR"+trim(drs, "");
            }
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText(drs, drsColor);

            /*
            builder.addColoredText(" / ", ContextCompat.getColor(getContext(), R.color.grey400));
            int color = getDynamicRangeDbColor(getContext(), tag);
            String dr = getDynamicRange(tag);
            if(isEmpty(dr)) {
                dr = " -- ";
            }else {
                dr = dr +" dB";
            }
            builder.addColoredText(dr, color);
            */
            builder.into(dynamicRangeDbTextView);
        }
    }
}