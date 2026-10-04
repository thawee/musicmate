package apincer.android.mmate.utils;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * A lightweight, native drop-in replacement for com.vanniktech.textbuilder.TextBuilder
 * and cn.iwgang.simplifyspan.SimplifySpanBuild.
 * Utilizes native Android SpannableStringBuilder.
 */
public class TextBuilder {
    private final Context context;
    private final SpannableStringBuilder builder;

    public TextBuilder(Context context) {
        this.context = context;
        this.builder = new SpannableStringBuilder();
    }
    
    public TextBuilder() {
        this(null);
    }

    public TextBuilder addColoredText(String text, int color) {
        if (text == null || text.isEmpty()) return this;
        int start = builder.length();
        builder.append(text);
        builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return this;
    }

    public TextBuilder addColoredTextRes(int textResId, int colorResId) {
        if (context == null) return this;
        String text = context.getString(textResId);
        int color = ContextCompat.getColor(context, colorResId);
        return addColoredText(text, color);
    }

    public TextBuilder addNewLine() {
        builder.append("\n");
        return this;
    }

    public TextBuilder addWhiteSpace() {
        builder.append(" ");
        return this;
    }

    public TextBuilder append(String text) {
        if (text != null) builder.append(text);
        return this;
    }

    public TextBuilder addText(String text) {
        return append(text);
    }

    public TextBuilder append(String text, int color, int textSizeDip, boolean isBold) {
        if (text == null || text.isEmpty()) return this;
        int start = builder.length();
        builder.append(text);
        if (color != 0) {
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (textSizeDip > 0) {
            builder.setSpan(new AbsoluteSizeSpan(textSizeDip, true), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (isBold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return this;
    }

    public TextBuilder append(String text, int color, int textSizeDip) {
        return append(text, color, textSizeDip, false);
    }
    
    public TextBuilder append(String text, int color) {
        return append(text, color, 0, false);
    }

    public void into(TextView textView) {
        if (textView != null) {
            textView.setText(builder);
        }
    }

    public SpannableStringBuilder build() {
        return builder;
    }
}
