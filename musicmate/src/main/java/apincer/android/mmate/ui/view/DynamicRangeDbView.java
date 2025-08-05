package apincer.android.mmate.ui.view;

import static apincer.android.mmate.utils.MusicTagUtils.getDRScoreBackgroundColor;
import static apincer.android.mmate.utils.MusicTagUtils.getDRScoreColor;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRange;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeDbBackground;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeDbColor;
import static apincer.android.mmate.utils.MusicTagUtils.getDynamicRangeScore;
import static apincer.android.mmate.utils.StringUtils.trim;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vanniktech.textbuilder.TextBuilder;

import org.w3c.dom.Text;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;

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


    public void setMusicItem(MusicTag tag) {
        if(tag == null) {
            dynamicRangeDbTextView.setText("-");
        }else {
            /*if(tag.getAudioBitsDepth()==16) {
               // dynamicRangeDbTextView.setText(getDynamicRange(tag)+"/96 dB");
                dynamicRangeDbTextView.setText(getDynamicRange(tag)+" dB");
            }else if(tag.getAudioBitsDepth()==24) {
                //dynamicRangeDbTextView.setText(getDynamicRange(tag)+"/144 dB");
                dynamicRangeDbTextView.setText(getDynamicRange(tag)+" dB");
            } */
            int drsColor = getDRScoreColor(getContext(), (int) tag.getDynamicRangeScore());
            String drs = getDynamicRangeScore(tag);
            drs = trim(drs, "-");
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText("DR"+drs, drsColor);
            builder.addText(", ");
            //dynamicRangeDbTextView.setText("DR"+drs+", "+getDynamicRange(tag)+" dB");
            int color = getDynamicRangeDbColor(getContext(), tag);
            //dynamicRangeDbTextView.setTextColor(color);
            builder.addColoredText(getDynamicRange(tag)+" dB", color);
            builder.into(dynamicRangeDbTextView);

            // Drawable background = getDRScoreBackgroundColor(getContext(), (int) tag.getDynamicRangeScore());
           // dynamicRangeDbTextView.setBackground(background);
        }
    }
}