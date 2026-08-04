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
        if (lowerCaseAgent.contains("hiby")) {
            return "HiBy Player";
        }
        if (lowerCaseAgent.contains("mpd") ||
                lowerCaseAgent.contains("music player daemon")) {
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
        if (!friendlyName.isEmpty() && !friendlyName.equalsIgnoreCase("Mozilla")) {
            return friendlyName;
        }

        // If all else fails, return a generic name.
        return "Streaming Player";
    }

    /**
     * Formats a 2-line player label for dialog cards and sheets.
     * Line 1: Player Name
     * Line 2: (IP / App Detail • Player Type)
     */
    public static String getTwoLinePlayerLabel(apincer.music.core.playback.spi.PlaybackTarget player) {
        if (player == null) return " - ";
        String name = player.getDisplayName();
        String ip = NetworkUtils.extractIpAddress(player.getDescription());

        if (player instanceof apincer.music.core.playback.DMRPlayer) {
            return !ip.isEmpty() ? name + "\n(" + ip + " • DLNA Renderer)" : name + "\n(DLNA Renderer)";
        } else if (player instanceof apincer.music.core.playback.ExternalAndroidPlayer) {
            String vStr = formatAppVersion(player.getDescription());
            return !vStr.isEmpty() ? name + "\n(" + vStr + " • Android App)" : name + "\n(Android App)";
        } else if (player.isStreaming()) {
            return !ip.isEmpty() ? name + "\n(" + ip + " • Web Streaming)" : name + "\n(Web Streaming)";
        }

        return !ip.isEmpty() ? name + "\n(" + ip + ")" : name;
    }

    /**
     * Formats a single-line player label for dropdown menus.
     * e.g. "HiBy R3 • 192.168.1.50" or "Poweramp • v935"
     */
    public static String getDropdownPlayerLabel(apincer.music.core.playback.spi.PlaybackTarget player) {
        if (player == null) return " - ";
        String name = player.getDisplayName();
        String ip = NetworkUtils.extractIpAddress(player.getDescription());

        if (!ip.isEmpty()) {
            return name + " • " + ip;
        } else if (player instanceof apincer.music.core.playback.ExternalAndroidPlayer) {
            String vStr = formatAppVersion(player.getDescription());
            return !vStr.isEmpty() ? name + " • " + vStr : name + " • Android App";
        }
        return name;
    }

    /**
     * Sanitizes and formats raw Android app version strings (e.g. "vbuild-2024-universal-release" or "build-935-arm64-play")
     * into clean, concise display labels like "v2024" or "v935".
     */
    public static String formatAppVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.trim().isEmpty() || "N/A".equalsIgnoreCase(rawVersion.trim())) {
            return "";
        }

        String version = rawVersion.trim();

        // 1. Check for "build-XXX" or "buildXXX" pattern (e.g. build-975, build975, vbuild-2024-universal)
        java.util.regex.Matcher buildMatcher = Pattern.compile("(?i)v?build[-_\\s]?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(version);
        if (buildMatcher.find()) {
            return "v" + buildMatcher.group(1);
        }

        // 2. Strip common build target/architecture suffixes like -arm64, -universal, -release, -play, -bundle
        version = version.replaceAll("(?i)[-_\\s]?(arm64|armv7|v8a|v7a|universal|release|play|bundle|beta|alpha|debug)+", "");

        // 3. Extract standard semver or numeric version (e.g. 3.0.975 or 975)
        java.util.regex.Matcher numMatcher = Pattern.compile("(?i)v?(\\d+(?:\\.\\d+)*)", Pattern.CASE_INSENSITIVE).matcher(version);
        if (numMatcher.find()) {
            return "v" + numMatcher.group(1);
        }

        // 4. Fallback: clean non-numeric string and limit length
        version = version.replaceAll("(?i)^v+", "");
        if (version.length() > 10) {
            version = version.substring(0, 10);
        }

        return "v" + version;
    }
}