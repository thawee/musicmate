package apincer.android.mmate.utils;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.palette.graphics.Palette;

public class ColorExtractionUtils {

    /**
     * Extracts colors from an album art Bitmap and applies them to the UI elements.
     * 
     * @param albumArt    The album art bitmap to extract colors from.
     * @param background  The root background view to tint with the dominant color.
     * @param titleText   The title text view to tint with a high-contrast color.
     * @param seekBar     The progress bar or seek bar to tint with the vibrant color.
     */
    public static void applyDynamicColorsFromAlbumArt(Bitmap albumArt, View background, TextView titleText, ProgressBar seekBar) {
        if (albumArt == null || albumArt.isRecycled()) return;

        Palette.from(albumArt).generate(palette -> {
            if (palette != null) {
                // Get the dominant and vibrant colors
                Palette.Swatch dominantSwatch = palette.getDominantSwatch();
                Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();

                if (dominantSwatch != null) {
                    if (background != null) {
                        background.setBackgroundColor(dominantSwatch.getRgb());
                    }
                    if (titleText != null) {
                        titleText.setTextColor(dominantSwatch.getTitleTextColor());
                    }
                }

                if (vibrantSwatch != null && seekBar != null) {
                    seekBar.setProgressTintList(ColorStateList.valueOf(vibrantSwatch.getRgb()));
                    
                    if (seekBar instanceof SeekBar) {
                        ((SeekBar) seekBar).setThumbTintList(ColorStateList.valueOf(vibrantSwatch.getRgb()));
                    }
                }
            }
        });
    }
}
