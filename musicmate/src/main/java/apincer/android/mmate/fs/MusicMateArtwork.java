package apincer.android.mmate.fs;

import android.graphics.Bitmap;

import org.jaudiotagger.tag.id3.valuepair.ImageFormats;
import org.jaudiotagger.tag.images.AndroidArtwork;
import org.jaudiotagger.tag.reference.PictureTypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import apincer.android.mmate.utils.BitmapHelper;

/**
 * Created by e1022387 on 1/9/2018.
 */

public class MusicMateArtwork extends AndroidArtwork {
   // public static int MIN_ALBUM_ART_SIZE = 800; // px
    public static int MAX_ALBUM_ART_SIZE = 800; //1024; // px
    final int IMAGE_MAX_SIZE = 1024; //640000; // 640 kb
    @Override
    public void setFromFile(File file)  throws IOException  {
        RandomAccessFile imageFile = new RandomAccessFile(file, "r");
        byte[] imagedata = new byte[(int) imageFile.length()];
        imageFile.read(imagedata);
        imageFile.close();

        Bitmap resultBitmap = BitmapHelper.decodeBitmap(imagedata, MAX_ALBUM_ART_SIZE, MAX_ALBUM_ART_SIZE);
        // resize to desired dimensions
        int height = resultBitmap.getHeight();
        int width = resultBitmap.getWidth();
        double y = Math.sqrt(IMAGE_MAX_SIZE
                / (((double) width) / height));
        double x = (y / height) * width;

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(resultBitmap, (int) x, (int) y, true);
        resultBitmap.recycle();
        Bitmap bitmap = scaledBitmap;

        System.gc();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        setBinaryData(stream.toByteArray());
        bitmap.recycle();
        stream.close();

        setMimeType(ImageFormats.getMimeTypeForBinarySignature(imagedata));
        setDescription("");
        setPictureType(PictureTypes.DEFAULT_ID);
    }

    /**
     * Create Artwork from File
     *
     * @param file
     * @return
     * @throws java.io.IOException
     */
    public static AndroidArtwork createArtworkFromFile(File file)  throws IOException {
        AndroidArtwork artwork = new MusicMateArtwork();
        artwork.setFromFile(file);
        return artwork;
    }

    @Override
    public boolean setImageFromData()
    {
        return true;
    }
}
