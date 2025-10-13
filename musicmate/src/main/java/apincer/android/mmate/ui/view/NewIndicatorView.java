package apincer.android.mmate.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import apincer.android.mmate.R;
import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.utils.TagUtils;

public class NewIndicatorView extends LinearLayout {
    private TextView textView;

    public NewIndicatorView(Context context) {
        super(context);
        init(context, null);
    }

    public NewIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NewIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_new_indicator, this, true);

        // Get reference to the TextView
        textView = findViewById(R.id.new_indicator_text_view);
        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL);
    }

    @SuppressLint("CheckResult")
    public void setMusicItem(MusicTag tag) {
        if(tag == null || tag.isMusicManaged()) {
            setVisibility(GONE);
        }else {
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.backgound_new_indicator);
            textView.setBackground(drawable);
            if (TagUtils.isOnDownloadDir(tag)) {
                textView.setTextColor(ResourcesCompat.getColor(getResources() ,R.color.new_download_indicator_text, getContext().getTheme()));
            } else {
                textView.setTextColor(ResourcesCompat.getColor(getResources() ,R.color.new_indicator_text, getContext().getTheme()));
            }
            setVisibility(VISIBLE);
        }
    }
}