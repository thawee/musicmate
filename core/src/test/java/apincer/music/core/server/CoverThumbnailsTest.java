package apincer.music.core.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CoverThumbnailsTest {

    @Test
    public void fit_keepsAspectRatio_withinTheBox() {
        assertArrayEquals(new int[]{160, 160}, CoverThumbnails.fit(1400, 1400, 160));
        assertArrayEquals(new int[]{160, 90}, CoverThumbnails.fit(1920, 1080, 160));
        assertArrayEquals(new int[]{90, 160}, CoverThumbnails.fit(1080, 1920, 160));
    }

    @Test
    public void fit_neverEnlarges() {
        assertArrayEquals(new int[]{100, 80}, CoverThumbnails.fit(100, 80, 160));
    }

    @Test
    public void sampleSize_isTheLargestPowerOfTwoThatStaysAboveTheTarget() {
        assertEquals(8, CoverThumbnails.sampleSize(1400, 1400, 160)); // 175 >= 160; 16 would give 87
        assertEquals(1, CoverThumbnails.sampleSize(300, 300, 160));
        assertEquals(1, CoverThumbnails.sampleSize(100, 100, 160));
        assertEquals(4, CoverThumbnails.sampleSize(3000, 700, 160)); // the short side decides: 700/4 = 175
    }
}
