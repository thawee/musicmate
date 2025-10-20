package apincer.music.core.utils;
import java.util.regex.Pattern;

/**
 * A utility class to create user-friendly names for media players
 * discovered on the network.
 */
public final class PlayerNameUtils {

    // A pre-compiled pattern to find and remove UUIDs.
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "uuid:[a-fA-F0-9\\-]{36}", Pattern.CASE_INSENSITIVE);

    // A pre-compiled pattern to find and remove IP addresses.
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");

    // An array of common technical terms to remove (case-insensitive).
    private static final String[] JARGON_TO_REMOVE = {
            "UPnP", "DLNA", "MediaRenderer", "AVTransport", "MediaServer"
    };

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private PlayerNameUtils() {}

    /**
     * Cleans a technical player name to make it more readable and user-friendly.
     *
     * @param technicalName The raw name from the discovered device.
     * @return A cleaned, more user-friendly name.
     */
    public static String createFriendlyPlayerName(String technicalName) {
        // 1. Handle null or empty input gracefully.
        if (technicalName == null || technicalName.trim().isEmpty()) {
            return "Unknown Player";
        }

        String friendlyName = technicalName;

        // 2. Remove UUIDs and IP Addresses using regex.
        friendlyName = UUID_PATTERN.matcher(friendlyName).replaceAll("");
        friendlyName = IP_ADDRESS_PATTERN.matcher(friendlyName).replaceAll("");

        // 3. Remove common technical jargon.
        for (String jargon : JARGON_TO_REMOVE) {
            // (?i) makes the replacement case-insensitive.
            friendlyName = friendlyName.replaceAll("(?i)" + Pattern.quote(jargon), "");
        }

        // 4. Replace common separators with spaces.
        friendlyName = friendlyName.replaceAll("[-_:]", " ");

        // 5. Collapse multiple spaces into a single space and trim whitespace.
        friendlyName = friendlyName.replaceAll("\\s+", " ").trim();

        // 6. Convert the result to a cleaner Title Case format.
        friendlyName = toTitleCase(friendlyName);

        // 7. If cleaning resulted in an empty string, fall back to the original name.
        if (friendlyName.isEmpty()) {
            return technicalName;
        }

        return friendlyName;
    }

    /**
     * Converts a string to Title Case (e.g., "hello world" -> "Hello World").
     *
     * @param input The string to convert.
     * @return The Title Cased string.
     */
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder titleCase = new StringBuilder();
        boolean nextTitleCase = true;

        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            } else {
                c = Character.toLowerCase(c);
            }
            titleCase.append(c);
        }

        return titleCase.toString();
    }

    /**
     * Creates a user-friendly name from a raw HTTP User-Agent string.
     * It specifically looks for common media player clients.
     *
     * @param userAgent The User-Agent string from the HTTP request.
     * @return A cleaned, user-friendly player name.
     */
    public static String getFriendlyNameFromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return "Streaming Player";
        }

        String lowerCaseAgent = userAgent.toLowerCase();

        // Check for specific, known players first
        if (lowerCaseAgent.contains("mpd") ||
                lowerCaseAgent.contains("music player daemon 0.23.17")) {
            //music player daemon 0.23.17
            return "MPD Player";
        }
        if (lowerCaseAgent.contains("jplay")) {
            return "JPLAY";
        }
        if (lowerCaseAgent.contains("lavf")) {
            //lavf/58.45.100
            return "mconnect Player";
        }

        // Fallback: If no specific agent is found, try to extract a clean name
        // by taking the part before the first slash or parenthesis.
        String friendlyName = userAgent.split("[/(]")[0].trim();
        if (!friendlyName.isEmpty()) {
            return friendlyName;
        }

        // If all else fails, return a generic name.
        return "Streaming Player";
    }
}