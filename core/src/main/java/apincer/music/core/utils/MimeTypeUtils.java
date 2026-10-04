package apincer.music.core.utils;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling MIME types, particularly for DLNA streaming
 */
public class MimeTypeUtils {
    private static final String TAG = "MimeTypeUtils";

    // Map of file extensions to MIME types
    private static final Map<String, String> MIME_MAP = new HashMap<>();

    // Default MIME type for unknown formats
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    // Default audio MIME type

    static {
        // Initialize the MIME type map with common audio formats

        // Lossless formats
        MIME_MAP.put("flac", "audio/flac");
        MIME_MAP.put("wav", "audio/wav");
        MIME_MAP.put("wave", "audio/wav");
        MIME_MAP.put("aif", "audio/aiff");
        MIME_MAP.put("aiff", "audio/aiff");
        MIME_MAP.put("ape", "audio/x-ape");
        MIME_MAP.put("alac", "audio/alac");
        MIME_MAP.put("dsd", "audio/dsd");
        MIME_MAP.put("dsf", "audio/x-dsf");
        MIME_MAP.put("dff", "audio/x-dff");

        // Lossy formats
        MIME_MAP.put("mp3", "audio/mpeg");
        MIME_MAP.put("m4a", "audio/mp4");
        MIME_MAP.put("aac", "audio/mp4");
        MIME_MAP.put("ogg", "audio/ogg");
        MIME_MAP.put("oga", "audio/ogg");
        MIME_MAP.put("opus", "audio/opus");
        MIME_MAP.put("wma", "audio/x-ms-wma");

        // Playlist formats
        MIME_MAP.put("m3u", "audio/x-mpegurl");
        MIME_MAP.put("m3u8", "audio/x-mpegurl");
        MIME_MAP.put("pls", "audio/x-scpls");

        // Image formats (for cover art)
        MIME_MAP.put("jpg", "image/jpeg");
        MIME_MAP.put("jpeg", "image/jpeg");
        MIME_MAP.put("png", "image/png");
        MIME_MAP.put("gif", "image/gif");

        // web content
        MIME_MAP.put("html", "text/html");
        MIME_MAP.put("css", "text/css");
        MIME_MAP.put("js", "text/javascript");
        MIME_MAP.put("ico", "image/x-icon");
        MIME_MAP.put("woff2", "font/woff2");
    }

    /**
     * Gets the MIME type from a file path based on its extension
     *
     * @param path The file path to analyze
     * @return The MIME type string, or a default type if not recognized
     */
    public static String getMimeTypeFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return DEFAULT_MIME_TYPE;
        }

        try {
            // Extract the file extension
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0 && lastDot < path.length() - 1) {
                String extension = path.substring(lastDot + 1).toLowerCase();

                // Look up the MIME type
                String mimeType = MIME_MAP.get(extension);
                if (mimeType != null) {
                    return mimeType;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error determining MIME type: " + e.getMessage());
        }

        // Default fallback
        return DEFAULT_MIME_TYPE;
    }

    /**
     * Gets a DLNA-compatible content type string for the given file path
     *
     * @param path The file path to analyze
     * @return A DLNA protocolInfo content type string
     */
    public static String getDlnaContentType(String path) {
        String mimeType = getMimeTypeFromPath(path);

        // DLNA profiles based on MIME type
        String dlnaProfile = "DLNA.ORG_PN=";
        if (mimeType.equals("audio/mpeg")) {
            dlnaProfile += "MP3";
        } else if (mimeType.equals("audio/flac")) {
            dlnaProfile += "FLAC";
        } else if (mimeType.equals("audio/wav")) {
            dlnaProfile += "WAV";
        } else if (mimeType.equals("audio/mp4") || mimeType.equals("audio/aac")) {
            dlnaProfile += "AAC_ISO";
        } else if (mimeType.equals("audio/ogg")) {
            dlnaProfile += "OGG";
        } else {
            // Generic profile
            return mimeType + ";DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000";
        }

        return mimeType + ";" + dlnaProfile + ";DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000";
    }
}
