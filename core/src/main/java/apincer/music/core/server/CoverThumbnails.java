package apincer.music.core.server;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import apincer.music.core.utils.StringUtils;

/**
 * 160x160 JPEG thumbnails of cover art, for DLNA renderers that only show art tagged with the
 * JPEG_TN profile (at most 160x160). Made on first request, cached, and rebuilt when the cover
 * file changes. Written to a temporary file and renamed, so a cut-short write is never served.
 */
public final class CoverThumbnails {
    private static final String TAG = "CoverThumbnails";
    static final int SIZE = 160;

    private CoverThumbnails() {
    }

    /** The thumbnail for {@code cover}, or null if the cover cannot be decoded. */
    public static File thumbnailFor(File cover, File cacheDir) {
        if (cover == null || !cover.isFile()) return null;
        File dir = new File(cacheDir, "coverart-tn");
        File thumb = new File(dir, StringUtils.md5Hex(cover.getAbsolutePath()) + ".jpg");
        if (thumb.isFile() && thumb.lastModified() >= cover.lastModified()) return thumb;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cover.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, SIZE);
        Bitmap sampled = BitmapFactory.decodeFile(cover.getAbsolutePath(), decode);
        if (sampled == null) return null;
        int[] size = fit(sampled.getWidth(), sampled.getHeight(), SIZE);
        Bitmap scaled = Bitmap.createScaledBitmap(sampled, size[0], size[1], true);
        try {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return null;
            File temp = File.createTempFile("thumb", ".tmp", dir); // prefix needs 3+ chars
            try (OutputStream out = new FileOutputStream(temp)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            if (!temp.renameTo(thumb)) {
                temp.delete();
                return thumb.isFile() ? thumb : null; // another request made it first
            }
            return thumb;
        } catch (IOException | RuntimeException e) {
            // The caller then serves the full-size cover instead
            Log.w(TAG, "Cannot write thumbnail for " + cover, e);
            return null;
        } finally {
            if (scaled != sampled) scaled.recycle();
            sampled.recycle();
        }
    }

    /** Width and height scaled to fit within max x max, keeping the aspect ratio; never enlarged. */
    static int[] fit(int width, int height, int max) {
        double scale = Math.min(1.0, Math.min((double) max / width, (double) max / height));
        return new int[]{Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    /** Largest power-of-two decode sample size that keeps both sides at least {@code max}. */
    static int sampleSize(int width, int height, int max) {
        int sample = 1;
        while (width / (sample * 2) >= max && height / (sample * 2) >= max) sample *= 2;
        return sample;
    }
}
