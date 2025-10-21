package apincer.music.core.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class FileTypeUtil {

    // A map to find the most common extension for a given MIME type
    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    static {
        // --- Common Image Types ---
        EXTENSION_MAP.put("image/jpeg", "jpg");
        EXTENSION_MAP.put("image/png", "png");
        EXTENSION_MAP.put("image/gif", "gif");
        EXTENSION_MAP.put("image/webp", "webp");
        EXTENSION_MAP.put("image/bmp", "bmp");
        EXTENSION_MAP.put("image/tiff", "tif");

        // --- Common Audio Types ---
        EXTENSION_MAP.put("audio/mpeg", "mp3");
        EXTENSION_MAP.put("audio/flac", "flac");
        EXTENSION_MAP.put("audio/wav", "wav");
        EXTENSION_MAP.put("audio/mp4", "m4a"); // Note: audio/mp4 is often m4a
        EXTENSION_MAP.put("audio/ogg", "ogg");
        EXTENSION_MAP.put("audio/aac", "aac");
        EXTENSION_MAP.put("audio/x-dsf", "dsf"); // DSD

        // (Add any other types you need to detect)
    }

    /**
     * Reads the file's content (magic bytes) to determine its MIME type,
     * then returns the most common file extension for that type.
     *
     * @param file The file to check.
     * @return The file extension (e.g., "png", "jpg") or null if unknown.
     */
    public static String getExtensionFromContent(File file) {
        String mimeType;
        try (InputStream is = new FileInputStream(file)) {
            // 1. Read "magic bytes" to get the true MIME type
            mimeType = URLConnection.guessContentTypeFromStream(is);
        } catch (IOException e) {
            // Could not read the file
            mimeType = null;
        }

        if (mimeType == null || mimeType.equals("application/octet-stream")) {
            // Content check failed, we can't determine the extension
            return null;
        }

        // 2. Look up the common extension for that MIME type
        //    Returns "jpg" for "image/jpeg", "png" for "image/png", etc.
        return EXTENSION_MAP.get(mimeType);
    }
}