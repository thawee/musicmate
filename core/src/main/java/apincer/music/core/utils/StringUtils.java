package apincer.music.core.utils;

import static apincer.music.core.Constants.ARTIST_SEP_SPACE;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import org.apache.commons.text.WordUtils;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.Constants;
import apincer.android.storage.StorageVolume;
import apincer.music.core.playback.spi.MediaTrack;

public class StringUtils {
    // ·  \u00b7
    // ♪   \u266A
    // ♬  \u266C
    // ◈  \u25C8
    // ╞  \u255E
    // ╡  \u2561
    // ♯  \u266F
    // ◈  \u25C8
    // ☾
    // ☽
    // \u9404    \\
    // ⊢ \u22A2
    // ⊣ \u22a3
    // ♯♯
    // ⟣ \u27E3
    // ⟢ \
    // Ⓖ \u24BC
    // u1f552

    //public static final String ARTIST_SEP = " \u00bb "; //"" -/- "; ·
    public static final String SEP_SUBTITLE = " \u25C8 "; //"" \u266A\u266A "; //"" \u2022\u266A\u2022 ";
    public static final String SEP_TITLE = "\u1931 "; //""\u1690 "; //""\u2E1F "; //""\u00b7 "; //""\u266A "; //""\u00b7 ";
   // public static final String SEP_LEFT = "\u27E3 "; //""\u22A2 "; //""\u263E "; //""\u00b7\u255E "; //"" \u00ab ";
   // public static final String SEP_RIGHT = " \u27E2"; //"" \u22A3"; //"" \u263D"; //"" \u2561\u00b7"; //"" \u00bb ";
   // public static final String SYMBOL_ATTENTION = " \u2249"; //"" \u266F\u266F"; // path diff
    //public static final String SYMBOL_ENC_SEP = " \u25C8 ";
    //public static final String SYMBOL_ENC_SEP = " | ";
    public static final String SYMBOL_ENC_SEP = " | "; //"" \u25C8 ";
    public static final String SYMBOL_SEP = " \u25C8 "; //"" \u2051 "; //"" \u17C7 ";
  //  public static final String SYMBOL_HEADER_SEP = " \u25C8 ";
  //  public static final String SYMBOL_GENRE = " \u24BC ";
    public static final String SYMBOL_MUSIC_NOTE = "\u266A";
    public static final String UNKNOWN = "<unknown>";
    public static final String UNKNOWN_CAP = "<Unknown>";
    public static final String UNKNOWN_ARTIST = "Unknown Artist";
    public static final String UNKNOWN_ALL_CAP = "<UNKNOWN>";
    public static final String UNTITLED_CAP = "<Untitled>";
    public static final String MULTI_VALUES = "<*>";
    public static final String EMPTY = " - "; // must left as empty for dropdown list

    private static final Pattern ESCAPE_XML_CHARS = Pattern.compile("[\"&'<>]");

    public static String getSimplifiedAlbum(MediaTrack track) {
        if (track == null) {
            return null;
        }

        String albumTitle = track.getAlbum(); // Assuming this method exists

        // Handle null or empty titles
        if (albumTitle == null || albumTitle.isEmpty()) {
            return albumTitle;
        }

        final String suffix = " - Single";

        // Check if the album title ends with " - Single"
        if (albumTitle.endsWith(suffix)) {
            return "Single";
        } else {
            // Otherwise, return the full, original album title
            return albumTitle;
        }
    }

    public enum TruncateType {
        PREFIX, // Adds "..." at the beginning
        SUFFIX  // Adds "..." at the end
    }

    public static boolean equals(String album, String album1) {
        return trimTitle(album).equals(trimTitle(album1));
    }

    public static String trimTitle(String text) {
        if(text == null) return "";
        if("-".equals(text)) return "";

        text = StringUtils.remove(text, UNKNOWN);
        text = StringUtils.remove(text, UNKNOWN_ALL_CAP);
        text = StringUtils.remove(text, UNKNOWN_CAP);
        text = StringUtils.remove(text, UNTITLED_CAP);
        text = StringUtils.remove(text, UNKNOWN_ARTIST);
        text = StringUtils.remove(text, MULTI_VALUES);
        if("-/-".equals(text)) return "";
        return StringUtils.trimToEmpty(text);
    }

    public static String getWord(String text, String sep, int index) {
        String [] words =  trimToEmpty(text).split(sep);
        if(words.length>index) {
            return trimToEmpty(words[index]);
        }
        return "";
    }

    public static boolean isEmpty(String input) {
        if(input == null) {
            return true;
        }else return input.trim().isEmpty();
    }


    /**
     * Truncates a string to a max length, adding an ellipsis to the start (PREFIX) or end (SUFFIX).
     *
     * @param input     The string to truncate.
     * @param maxLength The total maximum length of the resulting string, including the ellipsis.
     * @param type      The type of truncation (PREFIX or SUFFIX).
     * @return The truncated string.
     */
    public static String truncate(String input, int maxLength, TruncateType type) {
        if (input == null) {
            return "";
        }

        // If the string is already short enough, return it as is.
        if (input.length() <= maxLength) {
            return input;
        }

        String ellipsis = "...";
        int ellipsisLength = ellipsis.length(); // 3

        // --- Safety Check ---
        // If maxLength is too small to even fit the ellipsis (e.g., 3 or less),
        // we can't add "...". Just return a hard-cut substring.
        if (maxLength <= ellipsisLength) {
            return input.substring(0, maxLength);
        }
        // --- End Safety Check ---

        // Use a switch to handle the truncation type
        return switch (type) {
            case PREFIX ->
                // We want "...end"
                // Get the last (maxLength - 3) characters
                    ellipsis + input.substring(input.length() - (maxLength - ellipsisLength)).trim();
            default ->
                // We want "start..."
                // Get the first (maxLength - 3) characters
                    input.substring(0, maxLength - ellipsisLength).trim() + ellipsis;
        };
    }
	
	@SuppressLint("SuspiciousIndentation")
    public static boolean startsWith(String s1, String s2) {
	    if(isEmpty(s1) && !isEmpty(s2)) return false; // first is null
        if(isEmpty(s2)) return true; // do not compare
        s1 = trimToEmpty(s1);
        s2 = trimToEmpty(s2);
		
		if(s2.length() > s1.length()) {
			s2 = s2.substring(0, s1.length());
		}else {
			s1 = s1.substring(0, s2.length());
		}
		
        return s1.equalsIgnoreCase(s2);	
	}

    public static boolean compare(String s1, String s2) {
        if(isEmpty(s1) && !isEmpty(s2)) return false; // first is null
        if(isEmpty(s2)) return true; // do not compare
        s1 = trimToEmpty(s1);
        s2 = trimToEmpty(s2);

        return s1.equalsIgnoreCase(s2);
    }

    public static boolean contains(String s1, String s2) {
	if(StringUtils.isEmpty(s1) || StringUtils.isEmpty(s2)) {
		return false;
	}
	s1 = s1.trim().toLowerCase();
	s2 = s2.trim().toLowerCase();

        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return true; /* both strings are zero length */
        }
        return longer.contains(shorter);
    }

    /**
     * Calculates the similarity (a number within 0 and 1) between two strings.
     */
    public static double similarity(String s1, String s2) {
        String longer = StringUtils.trimToEmpty(s1), shorter = StringUtils.trimToEmpty(s2);
        if (longer.length() < shorter.length()) { // longer should always have greater length
            longer = shorter;
            shorter = StringUtils.trimToEmpty(s1);
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0; /* both strings are zero length */
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;

    }

    // Example implementation of the Levenshtein Edit Distance
    // See http://rosettacode.org/wiki/Levenshtein_distance#Java
    public static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    public static String trimToEmpty(String substring) {
        if(substring==null) return "";
        return substring.trim();
    }

    public static String trim(String substring, String defaultString) {
        if(isEmpty(substring)) return defaultString;
        return substring.trim();
    }

    public static String remove(String txt, String toRemove) {
        if(isEmpty(txt)) return "";
        if(isEmpty(toRemove)) return txt;
        while(txt.contains(toRemove)) {
            txt = txt.replace(toRemove, "");
        }
        return txt;
    }

    public static boolean isDigitOnly(String text) {
        if(StringUtils.isEmpty(text)) return false;
        for (char c : text.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }
    public static boolean isDigitOrDecimal(String text) {
        if(StringUtils.isEmpty(text)) return false;
        for (char c : text.toCharArray()) {
            if (!(Character.isDigit(c) || c=='.' || c=='-')) return false;
        }
        return true;
    }

    public static String formatStorageSize(long bytes) {
        double s = bytes*1.00;
        String unit;
        if(bytes<= StorageVolume.MB_IN_BYTES) {
            s = s/StorageVolume.KB_IN_BYTES;
            unit = "KB";
        }else if(bytes<= StorageVolume.GB_IN_BYTES){
            s = s/StorageVolume.MB_IN_BYTES;
            unit = "MB";
        }else { //if(bytes> StorageVolume.GB_IN_BYTES) {
           s = s/StorageVolume.GB_IN_BYTES;
           unit = "GB";
        }
        return String.format(Locale.getDefault(),"%.2f "+unit, s);
    }

    public static String formatNumber(long size) {
        if(size ==0) {
            return " - ";
        }
        return java.text.NumberFormat.getInstance().format(size);
    }

    public static String formatSongSize(long size) {
        if(size ==0) {
            return " - ";
        }
        return java.text.NumberFormat.getInstance().format(size);
    }

    public static String formatTitle(CharSequence text) {
        // trim space
        // format as word, first letter of word is capital
        if(isEmpty((String) text)) {
            return "";
        }

        String str = text.toString().trim();
        if(str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length()-1);
        }
        if(str.startsWith("\\\"") && str.endsWith("\\\"")) {
            str = str.substring(2, str.length()-2);
        }
        return WordUtils.capitalize(str);
    }

    public static String formatFilePath(CharSequence text) {
        // trim space
        // format as word, first letter of word is capital
        if(isEmpty((String) text)) {
            return "";
        }

        String str = text.toString().trim();
         if(str.contains("/")) {
             str = str.replace("/","_");
         }
        if(str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length()-1);
        }
        if(str.startsWith("\\\"") && str.endsWith("\\\"")) {
            str = str.substring(2, str.length()-2);
        }
        return WordUtils.capitalize(str);
    }

    /**
     * Formats a duration from a total number of seconds into a user-friendly string.
     * <p>
     * This method provides two distinct formats:
     * <ol>
     * <li><b>Standard (withUnit = false):</b> Formats as {@code HH:MM:SS} or {@code MM:SS}.
     * It strips leading "00:" components (e.g., "05:30" instead of "00:05:30").</li>
     * <li><b>Friendly (withUnit = true):</b> Formats with units, adapting to the duration:
     * <ul>
     * <li><b>&lt; 1 Minute:</b> "54 Sec"</li>
     * <li><b>&lt; 48 Hours:</b> "12 Hrs, 30 Min"</li>
     * <li><b>&ge; 48 Hours:</b> "12.5 Days"</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @param totalseconds The total duration in seconds.
     * @param withUnit     If {@code true}, formats with friendly units (e.g., "1.5 Days").
     * If {@code false}, formats as {@code HH:MM:SS}.
     * @return A formatted string representing the duration.
     */
    public static String formatDuration(double totalseconds, boolean withUnit) {

        // --- Format: 12:34:56 (with leading 00: stripped) ---
        if (!withUnit) {
            long s = (long) (totalseconds % 60);
            long m = (long) (totalseconds / 60 % 60);
            long h = (long) (totalseconds / 3600); // Total hours

            // Format as HH:MM:SS
            String formatText = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);

            // Strip leading "00:" (e.g., "00:05:30" -> "05:30")
            while (formatText.startsWith("00:") && formatText.length() > 5) {
                formatText = formatText.substring(3); // "00:".length() is 3
            }
            return formatText;
        }

        // --- Format: Friendly Units (e.g., "1.5 Days" or "12 Hrs, 30 Min") ---

        double totalHours = totalseconds / 3600.0;

        // NEW: If duration is 2 days (48 hours) or more, use the "Days" format
        if (totalHours >= 48.0) {
            double totalDays = totalHours / 24.0;
            // Format to one decimal place
            return String.format(Locale.getDefault(), "%.1f Days", totalDays);
        }

        // --- Original logic for durations < 48 hours ---
        // (This shows "Hrs, Min" and ignores seconds)

        long h = (long) (totalseconds / 3600);
        long m = (long) (totalseconds / 60 % 60);

        // Using %d to avoid padding (e.g., " 5 Hrs" -> "5 Hrs")
        String formatHrsUnit = "%d Hrs";
        String formatMinuteUnit = "%d Min";
        String formatText = "";

        if (h > 0) {
            formatText = String.format(Locale.getDefault(), formatHrsUnit, h);
        }

        if (m > 0) {
            if (formatText != null && !formatText.isEmpty()) {
                formatText = formatText + ", ";
            }
            formatText = formatText + String.format(Locale.getDefault(), formatMinuteUnit, m);
        }

        // Handle cases where duration is less than 1 minute
        if (formatText == null || formatText.isEmpty()) {
            long s = (long) (totalseconds % 60);
            return String.format(Locale.getDefault(), "%d Sec", s);
        }

        return formatText;
    }


    public static String formatDurationOld(double totalseconds, boolean withUnit) {
        int unitms = 1; //1000;
        long s = (long) (totalseconds / unitms % 60);
        long m = (long) (totalseconds / unitms / 60 % 60);
        long h = (long) (totalseconds / unitms / 60 / 60);
        String format = "%02d:%02d:%02d";
        String formatHrsUnit = "%2d Hrs";
        String formatMinuteUnit = "%2d Min";
        String formatText = "";

        if(withUnit) {
            if(h >0) {
                formatText = String.format(Locale.getDefault(), formatHrsUnit, h);
            }
            if(m >0) {
                if(!StringUtils.isEmpty(formatText)) {
                    formatText = formatText+", ";
                }
                formatText = formatText + String.format(Locale.getDefault(), formatMinuteUnit, m);
            }
            formatText = StringUtils.trimToEmpty(formatText);
        }else {
            formatText = String.format(Locale.getDefault(), format, h,m,s);
            while(formatText.startsWith("00:")) {
                formatText = formatText.substring(formatText.indexOf("00:")+("00:".length()));
            }
        }
        return formatText;
    }

    public static String formatAudioBitRate(long audioBitRate) {
        // show 1 MB as 1024 KB
        if(audioBitRate>10000000) {
            double dBitrate = audioBitRate/1000000.00;
            return String.format(Locale.getDefault(), "%.1f Mbps", dBitrate);
        }else {
            double dBitrate = audioBitRate/1000.00;
            return String.format(Locale.getDefault(), "%.0f Kbps", dBitrate);
        }
    }
    public static String formatAudioBitRateShortUnit(long audioBitRate) {
        if(audioBitRate>10000000) { // > 1000 K
            double dBitrate = audioBitRate/1000000.00;
            return String.format(Locale.getDefault(), "%.3f M", dBitrate);
        }else {
            double dBitrate = audioBitRate/1000.00;
            return String.format(Locale.getDefault(), "%.0f K", dBitrate);
        }
    }

    public static String formatAudioBitsDepth(int bit) {
        if(bit > 1) {
            return String.format(Locale.getDefault(), "%d-Bits", bit);
        } else {
            return String.format(Locale.getDefault(), "%d-Bit", bit);
        }
    }

    public static String formatAudioSampleRate(long rate,boolean includeUnit) {
        String unit = "kHz";
        String str;
        double factor = 1000.00;
        if(rate > 1000000) {
            unit = "MHz";
            factor = 1000000.00;
            double s = rate / factor;
            str = String.format(Locale.getDefault(),"%.1f", s);
        }else {
            double s = rate / factor;
            str = String.format(Locale.getDefault(),"%.1f", s);
            str = str.replace(".0", "");
        }
        if(includeUnit) {
            return str + unit;
        }
        return str;
    }

    public static String formatAudioSampleRateAbvUnit(long rate) {
        String unit = " k";
        String str;
        double factor = 1000.00;
        if(rate > 1000000) {
            unit = " M";
            factor = 1000000.00;
            double s = rate / factor;
            str = String.format(Locale.getDefault(),"%.1f", s);
        }else {
            double s = rate / factor;
            str = String.format(Locale.getDefault(),"%.1f", s);
            str = str.replace(".0", "");
        }
        return str + unit;
    }

    public static String formatStorageSizeGB(long size) {
        double s = size*1.00;
        s = s/StorageVolume.GB_IN_BYTES;

        //double s = bytes / StorageVolume.GB_IN_BYTES;
        return String.format(Locale.getDefault(),"%.2f", s);
    }

    public static long toLong(String text) {
        try {
            return Long.parseLong(trimToEmpty(text));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static double toDouble(String text) {
        try {
            return Double.parseDouble(trimToEmpty(text));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static double gainToDouble(String text) {
        if (text == null) return 0.0;
        text = text.replace("dB", "");
        try {
            return Double.parseDouble(trimToEmpty(text));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static int toInt(String text) {
        try {
            return Integer.parseInt(trimToEmpty(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double toDurationSeconds(String durationString) {
        // 00:04:13.33 - HH:MM:SS.sss
        // P1DT8H15M10.345000S

        double seconds = 0.0;
        String[] text = durationString.split(":");
        if(text.length>=1) {
            seconds += toDouble(text[0])*60*60;
        }
        if(text.length>=2) {
            seconds += toDouble(text[1])*60;
        }
        if(text.length>=3) {
            seconds += toDouble(text[2]);
        }
        return seconds;
    }

    public static boolean toBoolean(String text) {
        String trimmed = trimToEmpty(text);
        return "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed);
    }

    public static String formatAudioBitRateNoUnit(long audioBitRate) {
        if(audioBitRate>1000000) { //DSD
            // convert to Mbps
            double dBitrate = audioBitRate/1000000.00;
            return String.format(Locale.getDefault(), "%.1f", dBitrate);
        }else { // others, convert to kbps
            double dBitrate = audioBitRate/1000.00;
            return String.format(Locale.getDefault(), "%.0f", dBitrate);
        }
    }

    public static long formatDSDRate(long audioBitRate) {
           return audioBitRate/Constants.QUALITY_SAMPLING_RATE_44;
         //   return String.valueOf(dBitrate); // String.format(Locale.getDefault(), "%.0f", dBitrate);
    }

    public static String formatAudioBitRateInKbps(long audioBitRate) {
            double dBitrate = audioBitRate/1000.00;
            return String.format(Locale.getDefault(), "%.0f", dBitrate);
    }

    @Deprecated
    public static String normalize(String album) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < album.length(); i++) {
            switch (album.charAt(i)) {
                case '"':
                case '/':
                case '\\':
                case '?':
                case '*':
                    continue;
                case ':':
                    result.append('-');
                    break;
                default:
                    result.append(album.charAt(i));
            }
        }
        return result.toString().trim();
    }

    /**
     * A highly robust function to normalize strings for duplicate checking.
     * It handles case, accents, punctuation, and special characters from editors like MS Word.
     */
    public static String normalizeName(String name) {
       /* if (name == null)
            return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            switch (name.charAt(i)) {
                case '"':
                    result.append('\'');
                    continue;
                case '\r':
                case '\n':
                    continue;
                default:
                    result.append(name.charAt(i));
            }
        }
        return result.toString().trim(); */
        // 1. Handle null or empty input
        if (name == null || name.isEmpty()) {
            return "";
        }

        // 2. Pre-process common Microsoft Word and typographic characters
        // This step converts them to their simple ASCII equivalents before further processing.
        String processed = name
                // Replace smart single quotes (left and right) with a standard apostrophe
                .replace('‘', '\'')
                .replace('’', '\'')

                // Replace smart double quotes (left and right) with a standard double quote
                .replace('“', '"')
                .replace('”', '"')

                // Replace en-dash and em-dash with a standard hyphen
                .replace('–', '-')
                .replace('—', '-')

                // Replace the single-character ellipsis with a space
                .replace('…', ' ');

        // 3. Normalize to separate accents from base letters (e.g., "é" -> "e" + "´")
        processed = Normalizer.normalize(processed, Normalizer.Form.NFD);

        // 4. Remove the separated accent characters (diacritics)
        processed = processed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 5. Final cleanup: lowercase, remove all remaining punctuation, and fix whitespace
        /*return processed
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // Remove anything not a letter, number, or space
                .replaceAll("\\s+", " ")       // Collapse multiple spaces to one
                .trim(); */
        // --- Step 2: Perform the final cleanup with Unicode-aware patterns ---
        return processed
                // Use Locale.ROOT for consistent, non-regional lowercasing.
                .toLowerCase(Locale.ROOT)

                // Keep only Unicode letters (\p{L}), numbers (\p{N}), and whitespace (\s).
                // This correctly preserves letters from all languages.
                .replaceAll("[^\\p{L}\\p{N}\\s]", "")

                // Collapse multiple whitespace characters into a single space.
                .replaceAll("\\s+", " ")

                // Trim any final leading/trailing whitespace.
                .trim();
    }

    public static String formatTrack(String track) {
        String newTrack = "";
        if(!StringUtils.isEmpty(track)) {
            track = track.trim();
            for (char ch: track.toCharArray()) {
                if(Character.isDigit(ch)) {
                    newTrack = newTrack+ch;
                }else {
                    break;
                }
            }
        }
        return newTrack;
    }

    public static String formatChannels(String audioChannels) {
        if(isDigitOnly(audioChannels)) {
            return String.format(Locale.getDefault(), "%s Ch.", audioChannels);
        }
        return audioChannels;
    }

    public static String getAbvByUpperCase(String letter) {
        final StringBuilder abv = new StringBuilder();
        letter.chars().filter(c -> (Character.isUpperCase(c)||Character.isDigit(c)))
                .forEach(c -> abv.append((char) c));

        return abv.toString();
    }

    public static String format(Object val, int maxLen, String separator) {
       /* String text = trimToEmpty(String.valueOf(val));
        String ret = "";
        int cnt=0;
        for(int i=0;i<text.length();i++) {
            char ch = text.charAt(i);
            if(ch ==' ') {
                if(cnt >= maxLen) {
                    ret = ret+separator;
                    cnt =0;
                }
            }
            ret = ret+ch;
            cnt++;
        }
        return ret; */

        return trimToEmpty(String.valueOf(val));
    }

    public static String toUpperCase(String val) {
        return trimToEmpty(val).toUpperCase(Locale.US);
    }

    public static String toLowerCase(String val) {
        return trimToEmpty(val).toLowerCase(Locale.US);
    }

    public static String removeTrackNo(@Nullable String title) {
        if(title==null) return "";
        if(title.contains(".")) {
            String no = title.substring(0,title.indexOf("."));
            if(isDigitOnly(no)) {
                title = title.substring(title.indexOf(".")+1);
            }
        }
        return title;
    }

    public static String escapeXml(String title) {
        Matcher m = ESCAPE_XML_CHARS.matcher(trimToEmpty(title));
        StringBuffer buf = new StringBuffer();
        while (m.find()) {
            switch (m.group().codePointAt(0)) {
                case '"':
                    m.appendReplacement(buf, "&quot;");
                    break;
                case '&':
                    m.appendReplacement(buf, "&amp;");
                    break;
                case '\'':
                    m.appendReplacement(buf, "&apos;");
                    break;
                case '<':
                    m.appendReplacement(buf, "&lt;");
                    break;
                case '>':
                    m.appendReplacement(buf, "&gt;");
                    break;
            }
        }
        m.appendTail(buf);
        return buf.toString();
    }

    public static String formatArtists(String artist) {
        String []oldSeps = {";","&", ","}; //, "-", "/"};

        artist = trimToEmpty(artist);
        for(String sep: oldSeps) {
            String [] artistList = artist.split(sep, -1);
            for(int i=0; i<artistList.length;i++) {
                artistList[i] = formatTitle(artistList[i]);
            }
            artist = String.join(ARTIST_SEP_SPACE, artistList);
        }
        return artist;
    }

    @SuppressLint("DefaultLocale")
    public static String formatDurationAsMinute(double totalseconds) {
        Duration duration = Duration.ofSeconds((long) totalseconds);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();

        return String.format("%02d:%02d Min", minutes, seconds);
    }

    /**
     * Extracts the track number as an integer from a string that might be formatted as "1" or "1/2"
     * @param trackNumberStr The track number string to parse
     * @return The integer track number, or 0 if parsing fails
     */
    public static int extractTrackNumber(String trackNumberStr) {
        if (isEmpty(trackNumberStr)) {
            return 0;
        }

        // If the track number contains a slash, extract just the part before it
        int slashIndex = trackNumberStr.indexOf('/');
        if (slashIndex > 0) {
            trackNumberStr = trackNumberStr.substring(0, slashIndex);
        }

        // Try to parse the track number as an integer
        try {
            return Integer.parseInt(trackNumberStr.trim());
        } catch (NumberFormatException e) {
            return 0; // Return 0 for any parsing failures
        }
    }
}
