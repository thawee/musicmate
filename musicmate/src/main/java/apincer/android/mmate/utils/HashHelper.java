package apincer.android.mmate.utils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashHelper {
    public static String sha1(String s) {
        MessageDigest dig = getSha1Digest();
        byte[] bytes = dig.digest(getBytes(s));
        return new BigInteger(1, bytes).toString(16); // NOSONAR Hex is not a magic number.
    }

    private static byte[] getBytes (String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static MessageDigest getSha1Digest () {
        try {
            return MessageDigest.getInstance("SHA1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM should always know about SHA1.", e);
        }
    }
}