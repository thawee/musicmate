package org.jaudiotagger.tag.images;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import org.jaudiotagger.tag.id3.valuepair.ImageFormats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Image Handling used when running on standard JVM
 */
public class StandardImageHandler implements ImageHandler {
    private static StandardImageHandler instance;

    public static StandardImageHandler getInstanceOf() {
        if (instance == null) {
            instance = new StandardImageHandler();
        }
        return instance;
    }

    private StandardImageHandler() {

    }

    /**
     * Resize the image until the total size require to store the image is less than maxsize
     *
     * @param artwork
     * @param maxSize
     * @throws IOException
     */
    public void reduceQuality(Artwork artwork, int maxSize) throws IOException {
        while (artwork.getBinaryData().length > maxSize) {
            Bitmap srcImage = (Bitmap) artwork.getImage();
            int w = srcImage.getWidth();
            int newSize = w / 2;
            makeSmaller(artwork, newSize);
        }
    }

    /**
     * Resize image using Java 2D
     *
     * @param artwork
     * @param size
     * @throws java.io.IOException
     */
    public void makeSmaller(Artwork artwork, int size) throws IOException {
        Bitmap srcImage = (Bitmap) artwork.getImage();

        int w = srcImage.getWidth();
        int h = srcImage.getHeight();

        // Determine the scaling required to get desired result.
        float scaleW = (float) size / (float) w;
        float scaleH = (float) size / (float) h;

        // Create a matrix for the scaling
        Matrix matrix = new Matrix();
        matrix.postScale(scaleW, scaleH);

        // Create a new bitmap scaled to size
        Bitmap resizedBitmap = Bitmap.createBitmap(srcImage, 0, 0, w, h, matrix, false);

        if (artwork.getMimeType() != null && isMimeTypeWritable(artwork.getMimeType())) {
            artwork.setBinaryData(writeImage(resizedBitmap, artwork.getMimeType()));
        } else {
            artwork.setBinaryData(writeImageAsPng(resizedBitmap));
        }
    }

    public boolean isMimeTypeWritable(String mimeType) {
        return mimeType.equals(ImageFormats.MIME_TYPE_JPEG) ||
                mimeType.equals(ImageFormats.MIME_TYPE_PNG) ||
                mimeType.equals(ImageFormats.MIME_TYPE_GIF);
    }

    @Override
    public byte[] writeImage(Bitmap bitmap, String mimeType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (mimeType.equals(ImageFormats.MIME_TYPE_JPEG)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            return baos.toByteArray();
        } else if (mimeType.equals(ImageFormats.MIME_TYPE_PNG)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        } else if (mimeType.equals(ImageFormats.MIME_TYPE_GIF)) {
            // Note: Android doesn't support GIF compression directly
            // For GIF support, consider a third-party library or convert to PNG
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        }

        throw new IOException("Cannot write to this mimetype: " + mimeType);
    }

    @Override
    public byte[] writeImageAsPng(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }


    /**
     * Show read formats
     * <p>
     * On Windows supports png/jpeg/bmp/gif
     */
    public void showReadFormats() {
        System.out.println("Android supports reading: JPEG, PNG, GIF, BMP, WebP");
    }

    /**
     * Show write formats
     * <p>
     * On Windows supports png/jpeg/bmp
     */
    public void showWriteFormats() {
        System.out.println("Android supports writing: JPEG, PNG, WebP");
    }
}
