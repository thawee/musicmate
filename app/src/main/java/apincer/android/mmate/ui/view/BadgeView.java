package apincer.android.mmate.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vanniktech.textbuilder.TextBuilder;

import apincer.android.mmate.R;

public class BadgeView extends LinearLayout {
    private TextView textView;

    public BadgeView(Context context) {
        super(context);
        init(context, null);
    }

    public BadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public BadgeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Inflate the layout for this custom view
        // The 'true' for attachToRoot is because this LinearLayout is the root
        LayoutInflater.from(context).inflate(R.layout.view_badge, this, true);

        // Get reference to the TextView
        textView = findViewById(R.id.label_text_view);
        // Set orientation if it's not set in the <merge> tag (though LinearLayout defaults to horizontal)
        setOrientation(LinearLayout.HORIZONTAL);

        // Obtain the custom attributes from the XML layout
      //  TypedArray typedArray = context.getTheme().obtainStyledAttributes(
       //         attrs,
       //         R.styleable.BadgeView,
        //        0, 0
      //  );

       // try {
            // Read the string value from the 'label' attribute
           // String labelText = typedArray.getString(R.styleable.BadgeView);

            // Set the text on the internal TextView
           // if (labelText != null) {
            //    textView.setText(labelText);
           // }
      //  } finally {
            // IMPORTANT: Always recycle the TypedArray to free up resources
        //    typedArray.recycle();
       // }
    }

    public void setBadge(String label, int textColor, int bgColor) {
        if(label != null) {
            TextBuilder builder = new TextBuilder(getContext());
            builder.addColoredText(label, textColor);
            builder.into(textView);
            if(bgColor > -1) {
                textView.getBackground().setTint(bgColor);
               // textView.getBackground().setTintMode(PorterDuff.Mode.ADD);
                textView.getBackground().setTintMode(PorterDuff.Mode.SCREEN);
                //textView.setBackground(background);
            }
        }
    }

    public void setBadge(String label) {
        if(label != null) {
            textView.setText(label);
        }
    }

}