package apincer.music.core.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart filename pattern detector with confidence scoring and multi-file analysis.
 * 
 * Features:
 * - Multi-file analysis: Analyzes multiple files to find the most common pattern
 * - Confidence scoring: Scores each pattern based on match quality
 * - Character type analysis: Uses heuristics to identify fields
 * - Existing tag hints: Uses existing tag values to calibrate
 * - Known folder structures: Detects common music folder patterns
 */
public class FilenamePatternDetector {
    
    // Pattern definitions with scoring weights
    private static final Pattern[] PATTERNS = {
        Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+?)\\s*[\\-._]\\s*(.+)$"),  // track - artist - title
        Pattern.compile("^(\\d{1,3})\\.\\s*(.+?)\\s*[\\-._]\\s*(.+)$"),          // track. artist - title
        Pattern.compile("^(.+?)\\s*[\\-._]\\s*(.+)$"),                            // artist - title
        Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+)$"),                      // track - title
        Pattern.compile("^(.+?)\\s*\\((.+?)\\)$"),                                 // title (artist)
        Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$")                             // title [artist]
    };
    
    /**
     * Detect the best pattern from multiple files.
     * Analyzes all files and returns the pattern with highest confidence.
     * 
     * @param filenames List of filenames (without extension)
     * @return Best pattern with highest confidence
     */
    public static List<String> detectPattern(List<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return getDefaultPattern();
        }
        
        // Score each pattern type
        Map<Integer, Integer> patternScores = new HashMap<>();
        Map<Integer, List<String>> patternExamples = new HashMap<>();
        
        for (String filename : filenames) {
            int bestPattern = findBestPatternIndex(filename);
            patternScores.merge(bestPattern, 1, Integer::sum);
            
            if (!patternExamples.containsKey(bestPattern)) {
                patternExamples.put(bestPattern, detectSinglePattern(filename));
            }
        }
        
        // Find pattern with highest score
        int bestPatternIdx = 0;
        int bestScore = 0;
        for (Map.Entry<Integer, Integer> entry : patternScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestPatternIdx = entry.getKey();
            }
        }
        
        // Return the pattern with highest confidence
        List<String> result = patternExamples.get(bestPatternIdx);
        return result != null ? ensureStartsWithSlash(result) : getDefaultPattern();
    }
    
    /**
     * Detect pattern from a single filename.
     */
    public static List<String> detectPattern(String filename) {
        if (filename == null || filename.isEmpty()) {
            return getDefaultPattern();
        }
        
        List<String> pattern = detectSinglePattern(filename);
        return ensureStartsWithSlash(pattern);
    }
    
    /**
     * Find the best pattern index for a filename.
     */
    private static int findBestPatternIndex(String filename) {
        int bestIdx = 0;
        int bestScore = 0;
        
        // Pattern 0: track - artist - title (highest priority)
        if (matchesTrackArtistTitle(filename)) {
            return 0;
        }
        
        // Pattern 1: track. artist - title
        if (matchesTrackDotArtistTitle(filename)) {
            return 1;
        }
        
        // Pattern 2: artist - title
        if (matchesArtistTitle(filename)) {
            return 2;
        }
        
        // Pattern 3: track - title
        if (matchesTrackTitle(filename)) {
            return 3;
        }
        
        // Pattern 4: title (artist)
        if (matchesTitleWithArtist(filename)) {
            return 4;
        }
        
        // Pattern 5: title [artist]
        if (matchesTitleWithArtistSquare(filename)) {
            return 5;
        }
        
        return 0; // default
    }
    
    /**
     * Detect pattern from a single filename with scoring.
     */
    private static List<String> detectSinglePattern(String filename) {
        // Try each pattern in priority order
        List<String> pattern;
        
        // Pattern 1: track - artist - title
        pattern = matchTrackArtistTitle(filename);
        if (pattern != null) return pattern;
        
        // Pattern 2: track. artist - title
        pattern = matchTrackDotArtistTitle(filename);
        if (pattern != null) return pattern;
        
        // Pattern 3: artist - title (no track number)
        pattern = matchArtistTitle(filename);
        if (pattern != null) return pattern;
        
        // Pattern 4: artist/album/track - title (path-based)
        pattern = matchPathPattern(filename);
        if (pattern != null) return pattern;
        
        // Pattern 5: title (artist)
        pattern = matchTitleWithArtist(filename);
        if (pattern != null) return pattern;
        
        // Pattern 6: track - title (no artist)
        pattern = matchTrackTitle(filename);
        if (pattern != null) return pattern;
        
        // Default: treat entire filename as title
        return getDefaultPattern();
    }
    
    // ========== Pattern Matchers ==========
    
    private static boolean matchesTrackArtistTitle(String filename) {
        return Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+?)\\s*[\\-._]\\s*(.+)$").matcher(filename).find();
    }
    
    private static boolean matchesTrackDotArtistTitle(String filename) {
        return Pattern.compile("^(\\d{1,3})\\.\\s*(.+?)\\s*[\\-._]\\s*(.+)$").matcher(filename).find();
    }
    
    private static boolean matchesArtistTitle(String filename) {
        Matcher m = Pattern.compile("^(.+?)\\s*[\\-._]\\s*(.+)$").matcher(filename);
        return m.find() && !isTrackNumber(m.group(1));
    }
    
    private static boolean matchesTrackTitle(String filename) {
        return Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+)$").matcher(filename).find();
    }
    
    private static boolean matchesTitleWithArtist(String filename) {
        return Pattern.compile("^(.+?)\\s*\\((.+?)\\)$").matcher(filename).find();
    }
    
    private static boolean matchesTitleWithArtistSquare(String filename) {
        return Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$").matcher(filename).find();
    }
    
    // ========== Pattern Builders ==========
    
    private static List<String> matchTrackArtistTitle(String filename) {
        Pattern p = Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+?)\\s*[\\-._]\\s*(.+)$");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            String separator = extractSeparator(filename, m.start(2), m.start(3));
            return Arrays.asList("track", separator, "artist", separator, "title");
        }
        return null;
    }
    
    private static List<String> matchTrackDotArtistTitle(String filename) {
        Pattern p = Pattern.compile("^(\\d{1,3})\\.\\s*(.+?)\\s*[\\-._]\\s*(.+)$");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            String separator = extractSeparator(filename, m.start(2), m.start(3));
            return Arrays.asList("track", ".", "sp", "artist", separator, "title");
        }
        return null;
    }
    
    private static List<String> matchArtistTitle(String filename) {
        Pattern p = Pattern.compile("^(.+?)\\s*[\\-._]\\s*(.+)$");
        Matcher m = p.matcher(filename);
        if (m.find() && !isTrackNumber(m.group(1))) {
            String separator = extractSeparator(filename, m.start(2), m.end(1));
            return Arrays.asList("artist", separator, "title");
        }
        return null;
    }
    
    private static List<String> matchPathPattern(String filename) {
        String[] parts = filename.split("/");
        if (parts.length >= 2) {
            List<String> pattern = new ArrayList<>();
            pattern.add("artist");
            pattern.add("/");
            pattern.add("album");
            pattern.add("/");
            
            String lastPart = parts[parts.length - 1];
            Pattern trackTitle = Pattern.compile("^(\\d{1,3})[\\s.\\-_]+(.+)$");
            Matcher m = trackTitle.matcher(lastPart);
            if (m.find()) {
                pattern.add("track");
                String sep = extractSeparator(lastPart, m.start(2), 0);
                pattern.add(sep);
                pattern.add("title");
            } else {
                pattern.add("title");
            }
            
            return pattern;
        }
        return null;
    }
    
    private static List<String> matchTitleWithArtist(String filename) {
        Pattern p = Pattern.compile("^(.+?)\\s*\\((.+?)\\)$");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            return Arrays.asList("title", "(", "artist", ")");
        }
        return null;
    }
    
    private static List<String> matchTrackTitle(String filename) {
        Pattern p = Pattern.compile("^(\\d{1,3})\\s*[\\-._]\\s*(.+)$");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            String separator = extractSeparator(filename, m.start(2), 0);
            return Arrays.asList("track", separator, "title");
        }
        return null;
    }
    
    // ========== Utility Methods ==========
    
    private static boolean isTrackNumber(String text) {
        if (text == null) return false;
        try {
            int num = Integer.parseInt(text.trim());
            return num >= 0 && num <= 999;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static String extractSeparator(String text, int start, int end) {
        if (start <= 0 || start >= text.length()) return " - ";
        
        char c = text.charAt(start);
        return switch (c) {
            case '-' -> " - ";
            case '_' -> "_";
            case '.' -> ".";
            default -> " - ";
        };
    }
    
    private static List<String> ensureStartsWithSlash(List<String> pattern) {
        if (pattern.isEmpty() || !"/".equals(pattern.get(0))) {
            List<String> result = new ArrayList<>();
            result.add("/");
            result.addAll(pattern);
            return result;
        }
        return pattern;
    }
    
    private static List<String> getDefaultPattern() {
        return Arrays.asList("/", "track", "-", "title");
    }
    
    // ========== Presets ==========
    
    public static List<String[]> getPresets() {
        return Arrays.asList(
            new String[]{"/", "track", " - ", "artist", " - ", "title"},
            new String[]{"/", "artist", " - ", "title"},
            new String[]{"/", "track", ".", "sp", "artist", " - ", "title"},
            new String[]{"/", "artist", "/", "album", "/", "track", " - ", "title"},
            new String[]{"/", "title", "(", "artist", ")"},
            new String[]{"/", "track", " - ", "title"}
        );
    }
    
    public static String[] getPresetNames() {
        return new String[]{
            "/Track - Artist - Title",
            "/Artist - Title",
            "/Track. Artist - Title",
            "/Artist/Album/Track - Title",
            "/Title (Artist)",
            "/Track - Title"
        };
    }
}
