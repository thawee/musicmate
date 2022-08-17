package apincer.android.mmate.utils;

import java.io.UnsupportedEncodingException;
import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;

import apincer.android.storage.StorageVolume;
import apincer.android.mmate.Constants;

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
    //

    //public static final String ARTIST_SEP = " \u00bb "; //"" -/- "; ·
    public static final String SEP_SUBTITLE = " \u25C8 "; //"" \u266A\u266A "; //"" \u2022\u266A\u2022 ";
    public static final String SEP_TITLE = "\u1931 "; //""\u1690 "; //""\u2E1F "; //""\u00b7 "; //""\u266A "; //""\u00b7 ";
    public static final String SEP_LEFT = "\u27E3 "; //""\u22A2 "; //""\u263E "; //""\u00b7\u255E "; //"" \u00ab ";
    public static final String SEP_RIGHT = " \u27E2"; //"" \u22A3"; //"" \u263D"; //"" \u2561\u00b7"; //"" \u00bb ";
    public static final String SYMBOL_ATTENTION = " \u2249"; //"" \u266F\u266F"; // path diff
    public static final String SYMBOL_ENC_SEP = " \u25C8 ";
    public static final String SYMBOL_SEP = " \u25C8 "; //"" \u2051 "; //"" \u17C7 ";
    public static final String SYMBOL_HEADER_SEP = " \u25C8 ";
    public static final String SYMBOL_GENRE = " \u24BC ";
    public static final String SYMBOL_MUSIC_NOTE = " \u266A ";
    public static final String UNKNOWN = "<unknown>";
    public static final String UNKNOWN_CAP = "<Unknown>";
    public static final String UNKNOWN_ARTIST = "Unknown Artist";
    public static final String UNKNOWN_ALL_CAP = "<UNKNOWN>";
    public static final String UNTITLED_CAP = "<Untitled>";
    public static final String MULTI_VALUES = "<multiple>";
    public static final String CHARSET_ISO8859_1 = "ISO-8859-1";
    public static final String SYMBOL_RES_SEP = " \u25C8 ";

    public static String encodeText(String text, String encode) {
        if(StringUtils.isEmpty(encode)) {
            return text;
        }
        if(StringUtils.isEmpty(text)) {
            return "";
        }
        try {
            return newString(getBytesUnchecked(text,CHARSET_ISO8859_1),encode);
        } catch (Exception e) {
            return text;
        }
    }

    public static byte[]getBytesUnchecked(String string, String charsetName) {

        if (string == null) {
            return null;
        }
        try {
            return string.getBytes(charsetName);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static String newString(byte[] bytes, String charsetName) {
        if (bytes == null) {
            return "";
        }

        try {
            return new String(bytes, charsetName);
        } catch (UnsupportedEncodingException e) {
            return new String(bytes);
        }
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
        if("-/-".equals(text)) return "";
        return StringUtils.trimToEmpty(text);
    }

    public static boolean isEmpty(String input) {
        if(input == null) {
            return true;
        }else return input.trim().length() == 0;
    }

    public static String convertToStartCase(String value) {
        StringBuilder returnValue = new StringBuilder();
        value = value.toLowerCase();
        boolean makeNextUppercase = true;
        for (char c : value.toCharArray()) {
            if (Character.isSpaceChar(c) || Character.isWhitespace(c) || "()[]{}\\/".indexOf(c) != -1) {
                makeNextUppercase = true;
            } else if (makeNextUppercase) {
                c = Character.toTitleCase(c);
                makeNextUppercase = false;
            }

            returnValue.append(c);
        }
        return returnValue.toString();
    }

    @Deprecated
    public static String capitalize(final String str, final char... delimiters) {
        final int delimLen = delimiters == null ? -1 : delimiters.length;
        if (StringUtils.isEmpty(str) || delimLen == 0) {
            return str;
        }
        if(str.equals("VA")) return str;
        if(str.startsWith("VA ")) return str;

        final char[] buffer = str.toCharArray();
        boolean capitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            final char ch = buffer[i];
            if (isDelimiter(ch, delimiters)) {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer[i] = Character.toTitleCase(ch);
                capitalizeNext = false;
            }else {
                buffer[i] = Character.toLowerCase(ch);
            }
        }
        return new String(buffer);
    }

    @Deprecated
    public static String uncapitalize(final String str, final char... delimiters) {
        final int delimLen = delimiters == null ? -1 : delimiters.length;
        if (StringUtils.isEmpty(str) || delimLen == 0) {
            return str;
        }
        final char[] buffer = str.toCharArray();
        boolean uncapitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            final char ch = buffer[i];
            if (isDelimiter(ch, delimiters)) {
                uncapitalizeNext = true;
            } else if (uncapitalizeNext) {
                buffer[i] = Character.toLowerCase(ch);
                uncapitalizeNext = false;
            }
        }
        return new String(buffer);
    }

    /**
     * Is the character a delimiter.
     *
     * @param ch  the character to check
     * @param delimiters  the delimiters
     * @return true if it is a delimiter
     */
    private static boolean isDelimiter(final char ch, final char[] delimiters) {
        if (delimiters == null) {
            return Character.isWhitespace(ch);
        }
        for (final char delimiter : delimiters) {
            if (ch == delimiter) {
                return true;
            }
        }
        return false;
    }

    public static String truncate(String input, int maxLength) {
        if(input == null) {
            return "";
        }else if (input.length() <= maxLength) {
            return input;
        } else {
            return input.substring(0, maxLength - 3) + "...";
        }
    }
	
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

    public static String getFirstWord(String text) {
       // String firstWord = text.split("\\W")[0];
        Locale thaiLocale = new Locale("th");
        BreakIterator boundary = BreakIterator.getWordInstance(thaiLocale);
        boundary.setText(text);
        int start = boundary.first();
        int end = boundary.next();
        return text.substring(start, end);
    }

    public static String trimToEmpty(String substring) {
        if(substring==null) return "";
        return substring.trim();
    }

    public static String trim(String substring, String defaultString) {
        if(isEmpty(substring)) return defaultString;
        return substring.trim();
    }

    public static String[] splitArtists(String artist) {
        if(!StringUtils.isEmpty(artist)) {
            return artist.split(Constants.FIELD_SEP);
        }

        return null;
    }

    public static String merge(List<String> list, String s) {
        StringBuilder builder = new StringBuilder();
        for(String str:list) {
            if(builder.length()>0) {
                builder.append(s);
            }
            builder.append(str);
        }
        return builder.toString();
    }

    public static String remove(String txt, String toRemove) {
        if(isEmpty(txt)) return "";
        if(isEmpty(toRemove)) return txt;
        while(txt.contains(toRemove)) {
            txt = txt.replace(toRemove, "");
        }
        return txt;
    }

    public static String getChars(String text, int num) {
        if(StringUtils.isEmpty(text)) {
            return "*";
         }
         if(text.length()<=num) {
            return StringUtils.trimToEmpty(text);
         }
         return  StringUtils.trimToEmpty(text.substring(0, num));
    }

    public static boolean isDigitOnly(String text) {
        for (char c : text.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    public static String formatStorageSize(long bytes) {
        double s = bytes*1.00;
        String unit = "Bytes";
        if(bytes<= StorageVolume.MB_IN_BYTES) {
            s = s/StorageVolume.KB_IN_BYTES;
            unit = "KB";
        }else if(bytes<= StorageVolume.GB_IN_BYTES){
            s = s/StorageVolume.MB_IN_BYTES;
            unit = "MB";
        }else if(bytes> StorageVolume.GB_IN_BYTES) {
           s = s/StorageVolume.GB_IN_BYTES;
           unit = "GB";
        }
        //double s = bytes / StorageVolume.GB_IN_BYTES;
        String str = String.format(Locale.getDefault(),"%.2f "+unit, s);
        //str = str.replace(".0", "");
        return str;
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
        return java.text.NumberFormat.getInstance().format(size) +" songs";
    }

    public static String formatTitle(CharSequence text) {
        // trim space
        // format as word, first letter of word is capital
        if(text==null) {
            return "";
        }

        String str = text.toString().trim();
        if(str.contains("/")) {
            str = str.replace("/","_");
        }
        return convertToStartCase(trimToEmpty(str));
       // char [] delimiters = {' ','.','(','[',';'};
       // return StringUtils.capitalize(str, delimiters);
    }

    public static String formatDuration(long milliseconds, boolean withUnit) {
        int unitms = 1; //1000;
        long s = milliseconds / unitms % 60;
        long m = milliseconds / unitms / 60 % 60;
        long h = milliseconds / unitms / 60 / 60;
        //long h = milliseconds / unitms / 60 / 60 % 24;
       // long d = milliseconds / unitms / 60 / 60 / 24;
        //String format = "%02d:%02d";
       // String format = "%02d:%02d:%02d:%02d";
        String format = "%02d:%02d:%02d";
       // String formatDayUnit = "%2d Days";
        String formatHrsUnit = "%2d Hrs";
        String formatMinuteUnit = "%2d Min";
        //String formatUnits = "%2d days, %2d hrs, %2d Min.";
        String formatText = "";

        if(withUnit) {
           // if(d>0) {
           //     formatText = String.format(Locale.getDefault(), formatDayUnit, d);
           // }
            if(h >0) {
                //if(!StringUtils.isEmpty(formatText)) {
                //    formatText = formatText+", ";
                //}
                //formatText = formatText + String.format(Locale.getDefault(), formatHrsUnit, h);
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
            //formatText = String.format(Locale.getDefault(), format, d,h,m, s);
            formatText = String.format(Locale.getDefault(), format, h,m, s);
            while(formatText.startsWith("00:")) {
                formatText = formatText.substring(formatText.indexOf("00:")+("00:".length()));
            }
        }
        return formatText;
    }

    public static String getFormatedAudioBitRate(long audioBitRate) {
        if(audioBitRate>1000000) {
            double dBitrate = audioBitRate/1000000.00;
            return String.format(Locale.getDefault(), "%.1fMbps", dBitrate);
        }else {
            return String.format(Locale.getDefault(), "%dKbps", audioBitRate);
        }
    }

    public static String getFormatedBitsPerSample(int bit) {
            return String.format(Locale.getDefault(), "%dbits", bit);
    }

    public static String getFormatedAudioSampleRate(long rate,boolean includeUnit) {
        String unit = "kHz";
        String str="";
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

    public static String formatStorageSizeGB(long size) {
        double s = size*1.00;
        s = s/StorageVolume.GB_IN_BYTES;

        //double s = bytes / StorageVolume.GB_IN_BYTES;
        String str = String.format(Locale.getDefault(),"%.2f", s);
        return str;
    }

    public static long toLong(String sampleRate) {
        if(sampleRate == null) return 0;
        return Long.parseLong(sampleRate);
    }

    public static String getFormatedAudioBitRateNoUnit(long audioBitRate) {
        if(audioBitRate>1000000) {
            double dBitrate = audioBitRate/1000000.00;
            return String.format(Locale.getDefault(), "%.1f", dBitrate);
        }else {
            return String.format(Locale.getDefault(), "%d", audioBitRate);
        }
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

    public static String normalizeName(String name) {
        if (name == null)
            return name;
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
        return result.toString().trim();
    }
/*
    private static String quoteIt(String s) {
        if (s.indexOf(' ') < 0)
            return s;
        return String.format("\"%s\"", normalizeName(s));
    }*/

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

    public static String getFormatedChannels(String audioChannels) {
        if(isDigitOnly(audioChannels)) {
            return String.format(Locale.getDefault(), "%s ch", audioChannels);
        }
        return audioChannels;
    }
}
