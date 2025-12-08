package apincer.music.core.server;

import android.app.ActivityManager;
import android.content.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import apincer.music.core.server.model.ClientProfile;

public class ProfileManager {
    private static final Map<String, ClientProfile> PROFILES = new ConcurrentHashMap<>();
    private final int globalBufferSize;

    public ProfileManager(int globalBufferSize) {
        this.globalBufferSize = globalBufferSize;
        initProfiles();
    }

    public static int calculateBufferSize(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();

        if (memoryClass > 256) {
            return 131072; // 128KB for high-end devices
        } else if (memoryClass > 128) {
            return 65536;  // 64KB standard
        } else {
            return 32768;  // 32KB low mem
        }
    }

    private void initProfiles() {
        // 1. Default
        PROFILES.put("default", ClientProfile.standard(globalBufferSize));

        // 2. MPD (Audiophile)
        PROFILES.put("mpd", new ClientProfile("mpd",
                49152, true, 3,
                true, true, false, true, true));

        // 3. mConnect (Gapless + HighRes)
        PROFILES.put("mconnect", new ClientProfile("mconnect",
                globalBufferSize * 2, true, 3,
                true, true, true, false, false));

        // 4. JPlay (Strict Timing)
        PROFILES.put("jplay", new ClientProfile("jplay",
                49152, true, 2,
                true, true, true, false, true));

        // 5. Apple (ALAC friendly)
        PROFILES.put("apple", new ClientProfile("apple",
                globalBufferSize * 2, true, 4,
                false, false, false, false, false));
    }

    /**
     * The Single Source of Truth for detection
     */
    public ClientProfile detect(String userAgentString) {
        if (userAgentString == null) return PROFILES.get("default");

        String ua = userAgentString.toLowerCase();

        if (ua.contains("music player daemon") || ua.contains("mpd")) {
            return PROFILES.get("mpd");
        } else if (ua.contains("mconnect")) {
            return PROFILES.get("mconnect");
        } else if (ua.contains("jplay")) {
            return PROFILES.get("jplay");
        } else if (ua.contains("apple") || ua.contains("iphone") || ua.contains("ipad")) {
            return PROFILES.get("apple");
        }

        return PROFILES.get("default");
    }
}