package apincer.android.mmate.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import apincer.android.mmate.R;
import apincer.music.core.model.Track;
import apincer.music.core.utils.TagUtils;

public class NewIndicatorView extends LinearLayout {
    private TextView textView;
    private ImageView iconView;
    private View chipContainer;

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
        LayoutInflater.from(context).inflate(R.layout.view_new_indicator, this, true);

        chipContainer = findViewById(R.id.new_indicator_chip);
        iconView = findViewById(R.id.new_indicator_icon);
        textView = findViewById(R.id.new_indicator_text_view);
        setOrientation(LinearLayout.HORIZONTAL);
    }

    @SuppressLint("CheckResult")
    public void setMusicItem(Track tag) {
        if (tag == null || tag.isManaged()) {
            setVisibility(GONE);
        } else {
            boolean isDownload = TagUtils.isOnDownloadDir(tag);
            int textColorRes = isDownload ? R.color.new_download_indicator_text : R.color.new_indicator_text;
            int bgColorRes = isDownload ? R.color.new_download_indicator_background : R.color.new_indicator_background;

            int color = ResourcesCompat.getColor(getResources(), textColorRes, getContext().getTheme());
            int bgColor = ResourcesCompat.getColor(getResources(), bgColorRes, getContext().getTheme());

            if (textView != null) {
                textView.setTextColor(color);
            }
            if (iconView != null) {
                iconView.setImageTintList(ColorStateList.valueOf(color));
            }
            if (chipContainer != null) {
                chipContainer.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            }
            setVisibility(VISIBLE);
        }
    }
}