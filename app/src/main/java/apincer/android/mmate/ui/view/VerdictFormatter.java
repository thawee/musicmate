package apincer.android.mmate.ui.view;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import androidx.annotation.StyleRes;

import apincer.android.mmate.R;

public class VerdictFormatter {

    public static CharSequence format(Context context, String verdict) {
        if (verdict == null) return "No Analysis Data";

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append("Verdict: ");
        int start = builder.length();
        builder.append(verdict);
        int end = builder.length();

        // Map keywords to specific XML styles
        @StyleRes int styleId = getStyleForVerdict(verdict);

        // Apply the primary verdict style
        builder.setSpan(
                new TextAppearanceSpan(context, styleId),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

       // applyResolutionStyles(context, builder, verdict, start);
       // applyNotesStyles(context, builder, verdict, start);

        return builder;
    }

    private static @StyleRes int getStyleForVerdict(String verdict) {
        if (verdict == null) return R.style.Text_MM_Verdict_CD;

        // 1. Gold / Amber -> Studio Master
        if (verdict.contains("Studio") || verdict.contains("Hi-Res")) {
            return R.style.Text_MM_Verdict_Studio;
        }

        // 2. Green / Vintage -> Analog Master
        if (verdict.contains("Analog")) {
            return R.style.Text_MM_Verdict_Vintage;
        }

        // 3. Orange / Warning -> Upscaled Content
        if (verdict.contains("Upscaled") || verdict.contains("Upsampled")) {
            return R.style.Text_MM_Verdict_Warning;
        }

        // 4. Red -> MPEG/Lossy Source
        if (verdict.contains("Lossy")) {
            return R.style.Text_MM_Verdict_Lossy;
        }

        // 5. Cyan / Blue -> Perfect CD Quality (Default)
        return R.style.Text_MM_Verdict_CD;
    }

    private static void applyNotesStyles(Context context, SpannableStringBuilder builder, String verdict, int offset) {
        // Regex or simple search to find (...) and make them smaller/gray
        int openBracket = verdict.indexOf("(");
        int closeBracket = verdict.indexOf(")");

        if (openBracket != -1 && closeBracket != -1) {
            builder.setSpan(
                    new TextAppearanceSpan(context, R.style.Text_MM_Verdict_TechnicalNote),
                    offset + openBracket,
                    offset + closeBracket + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private static void applyResolutionStyles(Context context, SpannableStringBuilder builder, String verdict, int offset) {
        // Regex or simple search to find (...) and make them smaller/gray
        int openBracket = verdict.indexOf("[");
        int closeBracket = verdict.indexOf("]");

        if (openBracket != -1 && closeBracket != -1) {
            builder.setSpan(
                    new TextAppearanceSpan(context, R.style.Text_MM_Verdict_TechnicalNote),
                    offset + openBracket,
                    offset + closeBracket + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}