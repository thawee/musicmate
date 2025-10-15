package apincer.android.mmate.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import apincer.android.mmate.R;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.StringUtils;

public class DurationView extends LinearLayout {
    private TextView textView;
    private String mode;

    public DurationView(Context context) {
        super(context);
        init(context, null);
    }

    public DurationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DurationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_duration, this, true);

        // Get reference to the TextView
        textView = findViewById(R.id.duration_text_view);
        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL);

    }

    @SuppressLint("CheckResult")
    public void setMusicItem(MusicTag tag) {
        if(tag == null || tag.getAudioDuration() == 0) {
            textView.setText("00:00");
        }else {
            textView.setText(StringUtils.formatDuration(tag.getAudioDuration(), false));
        }
    }
}