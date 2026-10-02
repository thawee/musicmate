package apincer.music.core.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers which DLNA clients (TVs, control apps) talk to this server, by address, with a
 * readable name from their User-Agent or Sony's X-AV-Client-Info header. Used for the "clients"
 * line on the Music Center Server tab and to log each new client's request headers once, so
 * device-specific problems can be fixed from evidence. Safe from any thread.
 */
public final class ClientRegistry {
    /** Shared by the web and UPnP ports. */
    public static final ClientRegistry SHARED = new ClientRegistry();

    // Sony puts the model in X-AV-Client-Info: mn="BRAVIA KD-55X85J" (UMS matches BRAVIA and (KD|FW)-NNX..)
    private static final Pattern SONY_MODEL = Pattern.compile("mn=\"([^\"]+)\"");

    private static final class Client {
        final String address;
        final String name;
        long lastSeen;

        Client(String address, String name, long lastSeen) {
            this.address = address;
            this.name = name;
            this.lastSeen = lastSeen;
        }
    }

    private final Map<String, Client> clients = new LinkedHashMap<>();

    /** Records a request; true the first time this address is seen under this name. */
    public synchronized boolean record(String address, String userAgent, String avClientInfo, long nowMs) {
        String name = describe(userAgent, avClientInfo);
        String key = address + "|" + name;
        Client client = clients.get(key);
        if (client != null) {
            client.lastSeen = nowMs;
            return false;
        }
        if (clients.size() >= 64) clients.remove(clients.keySet().iterator().next()); // oldest first
        clients.put(key, new Client(address, name, nowMs));
        return true;
    }

    /** "LG webOS TV (192.168.1.20), BubbleUPnP (192.168.1.21)" for clients seen within the window. */
    public synchronized String recentSummary(long nowMs, long windowMs) {
        List<String> parts = new ArrayList<>();
        for (Client c : clients.values()) {
            if (nowMs - c.lastSeen <= windowMs) parts.add(c.name + " (" + c.address + ")");
        }
        return String.join(", ", parts);
    }

    /**
     * Records a request from either port; the first time a client is seen, logs its request
     * headers once (logcat tag "DlnaClients") as evidence for device-specific fixes.
     */
    public static void observe(String address, java.util.Map<String, String> headers, String path) {
        String userAgent = headers.get("user-agent");
        String avClientInfo = headers.get("x-av-client-info");
        if (SHARED.record(address, userAgent, avClientInfo, System.currentTimeMillis())) {
            java.util.logging.Logger.getLogger("DlnaClients").info("New client " + describe(userAgent, avClientInfo)
                    + " at " + address + ", first request " + path + ", headers " + headers);
        }
    }

    /** A readable client name from its User-Agent and Sony's X-AV-Client-Info header. */
    static String describe(String userAgent, String avClientInfo) {
        if (avClientInfo != null && !avClientInfo.isEmpty()) {
            Matcher m = SONY_MODEL.matcher(avClientInfo);
            if (m.find()) {
                String model = m.group(1).trim();
                return model.toUpperCase(Locale.ROOT).startsWith("SONY") ? model : "Sony " + model;
            }
            if (avClientInfo.toLowerCase(Locale.ROOT).contains("sony")) return "Sony";
        }
        if (userAgent == null || userAgent.trim().isEmpty()) return "Unknown client";
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.matches(".*\\blg.? ?webos ?tv.*") || ua.contains("webostv")) return "LG webOS TV";
        if (ua.contains("toshiba")) return "Toshiba TV";
        if (ua.contains("sec_hhp") || ua.contains("samsung")) return "Samsung TV";
        if (ua.contains("bravia")) return "Sony BRAVIA";
        if (ua.contains("windows-media-player") || ua.contains("wmfsdk")) return "Windows Media Player";
        if (ua.contains("xbox")) return "Xbox";
        if (ua.contains("bubbleupnp")) return "BubbleUPnP";
        if (ua.contains("kodi")) return "Kodi";
        String first = userAgent.trim().split("[\\s/]", 2)[0];
        return first.isEmpty() ? "Unknown client" : first;
    }
}
