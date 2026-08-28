package apincer.music.core.utils;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ThaiEncodingUtilsTest {

    @Test
    public void testHasHighAscii_standardAscii() {
        Assert.assertFalse(ThaiEncodingUtils.hasHighAscii("Hotel California"));
        Assert.assertFalse(ThaiEncodingUtils.hasHighAscii("Pink Floyd 1973"));
    }

    @Test
    public void testFixThaiEncoding_recoversGarbledText() {
        // Thai text "สวัสดี" encoded in Windows-874 and read as ISO-8859-1
        String originalThai = "สวัสดี";
        byte[] win874Bytes = originalThai.getBytes(Charset.forName("windows-874"));
        String garbled = new String(win874Bytes, StandardCharsets.ISO_8859_1);

        Assert.assertTrue(ThaiEncodingUtils.hasHighAscii(garbled));
        Assert.assertTrue(ThaiEncodingUtils.isGarbledThai(garbled));

        String fixed = ThaiEncodingUtils.fixThaiEncoding(garbled);
        Assert.assertEquals(originalThai, fixed);
    }

    @Test
    public void testFixThaiEncoding_plainEnglishUnchanged() {
        String plain = "Comfortably Numb";
        Assert.assertEquals(plain, ThaiEncodingUtils.fixThaiEncoding(plain));
    }
}
