package apincer.music.core.utils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for detecting and fixing Thai text encoding issues.
 * 
 * Common problem: Thai text encoded in TIS-620 (Windows-874) stored in ID3v1 tags
 * which don't specify encoding, causing garbled display when read as ISO-8859-1.
 */
public class ThaiEncodingUtils {
    
    // TIS-620/Windows-874 Thai character ranges
    private static final int THAI_CHAR_START = 0x0E00; // Thai character block start
    private static final int THAI_CHAR_END = 0x0E5B;   // Thai character block end
    
    /**
     * Detect if a string contains garbled Thai text (TIS-620/Windows-874 bytes misread as Latin-1).
     * 
     * @param text The text to check
     * @return true if the text appears to be garbled Thai
     */
    public static boolean isGarbledThai(String text) {
        if (text == null || text.isEmpty()) return false;
        if (!hasHighAscii(text)) return false;
        
        try {
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, "windows-874");
            return isValidThaiText(fixed);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if text contains valid Thai characters (not garbled).
     */
    private static boolean isValidThaiText(String text) {
        if (text == null || text.isEmpty()) return false;
        int validThaiCount = 0;
        int len = text.length();
        
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            // Thai vowels, consonants, tone marks, and digits
            if ((c >= 0x0E01 && c <= 0x0E3A) ||  // Consonants + vowels + tone marks
                (c >= 0x0E40 && c <= 0x0E49) ||  // Vowels above/below
                (c >= 0x0E50 && c <= 0x0E59)) {  // Thai digits
                validThaiCount++;
            }
        }
        
        // If most characters are valid Thai, it's probably correct
        return validThaiCount > len * 0.3;
    }
    
    /**
     * Convert a string from TIS-620/Windows-874 encoding to Unicode.
     * 
     * @param garbledText The garbled text (TIS-620 bytes interpreted as Latin-1)
     * @return The corrected Unicode text, or the original if conversion fails
     */
    public static String fixThaiEncoding(String garbledText) {
        if (garbledText == null || garbledText.isEmpty()) return garbledText;
        if (!hasHighAscii(garbledText)) return garbledText;
        
        try {
            byte[] bytes = garbledText.getBytes(StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, "windows-874");
            if (isValidThaiText(fixed)) {
                return fixed;
            }
        } catch (Exception e) {
            // Conversion failed, return original
        }
        
        return garbledText;
    }
    
    /**
     * Fast-path check: Check if text contains high ASCII characters in the range A1-FB.
     * These correspond to Thai characters when encoded in TIS-620/Windows-874.
     */
    public static boolean hasHighAscii(String text) {
        if (text == null) return false;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c >= 0xA1 && c <= 0xFB) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if text contains Thai characters.
     */
    private static boolean hasThaiCharacters(String text) {
        if (text == null) return false;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c >= THAI_CHAR_START && c <= THAI_CHAR_END) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Fix encoding for a map of tag fields.
     * 
     * @param tags Map of tag field names to values
     * @return Map with corrected encoding
     */
    public static Map<String, String> fixEncodingBatch(Map<String, String> tags) {
        Map<String, String> fixed = new HashMap<>();
        
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String value = entry.getValue();
            fixed.put(entry.getKey(), fixThaiEncoding(value));
        }
        
        return fixed;
    }
}
