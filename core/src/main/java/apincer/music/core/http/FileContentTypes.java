package apincer.music.core.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/** Content type for files served by SonicNIO: by extension, and by magic bytes for images. */
final class FileContentTypes {
    private static final Map<String, String> MIME_MAP = new HashMap<>();

    static {
        // Lossless Audio Formats (Hi-Res)
        MIME_MAP.put("flac", "audio/flac");
        MIME_MAP.put("alac", "audio/mp4");
        MIME_MAP.put("ape", "audio/x-ape");
        MIME_MAP.put("wv", "audio/wavpack");
        MIME_MAP.put("tta", "audio/x-tta");

        // DSD Formats (Super Hi-Res)
        MIME_MAP.put("dff", "audio/x-dff");
        MIME_MAP.put("dsf", "audio/x-dsf");
        MIME_MAP.put("dsd", "audio/x-dsd");

        // Lossy Audio Formats
        MIME_MAP.put("mp3", "audio/mpeg");
        MIME_MAP.put("aac", "audio/aac");
        MIME_MAP.put("m4a", "audio/mp4");
        MIME_MAP.put("ogg", "audio/ogg");
        MIME_MAP.put("oga", "audio/ogg");
        MIME_MAP.put("opus", "audio/opus");

        // Uncompressed Audio
        MIME_MAP.put("wav", "audio/wav");
        MIME_MAP.put("aiff", "audio/aiff");
        MIME_MAP.put("aif", "audio/aiff");

        // Video with audio
        MIME_MAP.put("mp4", "video/mp4");
        MIME_MAP.put("mkv", "video/x-matroska");
        MIME_MAP.put("webm", "video/webm");

        // Images
        MIME_MAP.put("jpg", "image/jpeg");
        MIME_MAP.put("jpeg", "image/jpeg");
        MIME_MAP.put("png", "image/png");
        MIME_MAP.put("gif", "image/gif");
        MIME_MAP.put("webp", "image/webp");

        // Text
        MIME_MAP.put("txt", "text/plain");
        MIME_MAP.put("html", "text/html");
        MIME_MAP.put("css", "text/css");
        MIME_MAP.put("js", "application/javascript");
        MIME_MAP.put("json", "application/json");
        MIME_MAP.put("xml", "application/xml");
    }

    static String getMimeType(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1 && lastDot < fileName.length() - 1) {
            return MIME_MAP.getOrDefault(fileName.substring(lastDot + 1).toLowerCase(), "application/octet-stream");
        }
        return "application/octet-stream";
    }

    static String readContentForMime(File file) {
        // 1. First, get the MIME type based on the file extension
        String extensionMimeType = FileContentTypes.getMimeType(file.getName());

        // 2. Check if the extension is for an image
        if (!extensionMimeType.startsWith("image/")) {
            // It's not an image (e.g., "audio/flac"), just return the extension type.
            // We do NOT read the content.
            return extensionMimeType;
        }

        // 3. It *is* supposed to be an image (e.g., "front.jpg").
        //    NOW we read the content to find the *true* MIME type
        //    (in case it's really a PNG).
        String contentMimeType;
        // guessContentTypeFromStream needs mark/reset, which FileInputStream lacks
        try (InputStream is = new java.io.BufferedInputStream(new FileInputStream(file))) {
            // This reads the file's "magic bytes"
            contentMimeType = URLConnection.guessContentTypeFromStream(is);
        } catch (IOException e) {
            contentMimeType = null;
        }

        // 4. Return the most accurate type
        if (contentMimeType != null && !contentMimeType.equals("application/octet-stream")) {
            // The content check was successful (e.g., it found "image/png").
            // This is the most reliable answer.
            return contentMimeType;
        } else {
            // The content check failed. Fall back to the extension type we found in step 1.
            return extensionMimeType;
        }
    }
}
