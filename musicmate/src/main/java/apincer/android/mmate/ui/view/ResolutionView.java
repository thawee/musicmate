package apincer.android.mmate.ui.view;
import static apincer.android.mmate.core.utils.TagUtils.getBPSAndSampleRate;
import static apincer.android.mmate.core.utils.TagUtils.isMQA;
import static apincer.android.mmate.utils.MusicTagUtils.getFileEncodingColor;
import static apincer.android.mmate.utils.MusicTagUtils.getResolutionBackground;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Locale;

import apincer.android.mmate.R;
import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.utils.StringUtils;

public class ResolutionView extends RelativeLayout {

    private TextView encodingTextView;
    private TextView resolutionTextView;

    public ResolutionView(Context context) {
        super(context);
        init(context, null);
    }

    public ResolutionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ResolutionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Inflate the layout and attach it to this view (the RelativeLayout root)
        LayoutInflater.from(context).inflate(R.layout.view_resolution, this, true);

        // Get references to the child views
        encodingTextView = findViewById(R.id.file_encoding);
        resolutionTextView = findViewById(R.id.file_resolution);

        // Set the background for the entire component
        setBackgroundResource(R.drawable.shape_icon_border_back);
        // Set padding
        int padding = (int) getResources().getDimension(R.dimen.dimen_2_dp);
        setPadding(padding, padding, padding, padding);
    }

    public void setMusicItem(MusicTag tag) {
        if(tag == null) {
            encodingTextView.setText("");
            resolutionTextView.setText("");
        }else {
            String label;
            String samplingRate = getBPSAndSampleRate(tag);
            if(tag.isDSD()) {
                // dsd use bitrate
                label = "DSD";
                long dsdRate = StringUtils.formatDSDRate(tag.getAudioSampleRate());
                samplingRate = String.valueOf(dsdRate);
            }else if(isMQA(tag)) {
                label = "MQA";
            }else {
                // any others format i.e. mpeg, aac
                label = StringUtils.trim(tag.getAudioEncoding(), tag.getFileType()).toUpperCase(Locale.US);
            }
            int labelColor = getFileEncodingColor(getContext(), tag);
            encodingTextView.setText(label);
            encodingTextView.setTextColor(labelColor);
            resolutionTextView.setText(samplingRate);
            Drawable background = getResolutionBackground(getContext(), tag);
            resolutionTextView.setBackground(background);
        }
    }
}