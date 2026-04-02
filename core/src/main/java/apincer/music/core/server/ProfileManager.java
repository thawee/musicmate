package apincer.music.core.server;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import musicmate.core.R;

public class ProfileManager {
    private static final String TAG = "ProfileManager";
    private static final Map<String, ClientProfile> PROFILES = new ConcurrentHashMap<>();
    private final int globalBufferSize;

    public ProfileManager(Context context, int globalBufferSize) {
        this.globalBufferSize = globalBufferSize;
        initProfiles(context);
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

    private void initProfiles(Context context) {
        // 1. Default Profile (Hardcoded fallback)
        PROFILES.put("default", ClientProfile.standard(globalBufferSize));

        // 2. Load from JSON
        try (InputStream is = context.getResources().openRawResource(R.raw.client_profiles)) {
            ObjectMapper mapper = new ObjectMapper();
            List<ClientProfile> list = mapper.readValue(is, new TypeReference<List<ClientProfile>>() {});
            
            for (ClientProfile profile : list) {
                ClientProfile finalProfile = profile;
                // Replace placeholder with global buffer size if needed
                if (profile.chunkSize == -1) {
                    finalProfile = new ClientProfile(
                            profile.name,
                            globalBufferSize,
                            profile.keepAlive,
                            profile.maxConnections,
                            profile.supportsGapless,
                            profile.supportsHighRes,
                            profile.supportsDirectStreaming,
                            profile.supportsLosslessStreaming,
                            profile.supportsBitPerfectStreaming,
                            profile.userAgentKeywords
                    );
                }
                PROFILES.put(profile.name.toLowerCase(), finalProfile);
            }
            Log.i(TAG, "Loaded " + PROFILES.size() + " client profiles from JSON");
        } catch (Exception e) {
            Log.e(TAG, "Error loading client profiles", e);
        }
    }

    /**
     * Detects the client profile based on the User-Agent string.
     */
    public ClientProfile detect(String userAgentString) {
        if (userAgentString == null) return PROFILES.get("default");

        String ua = userAgentString.toLowerCase();

        // Iterate through loaded profiles and match keywords
        for (ClientProfile profile : PROFILES.values()) {
            if ("default".equalsIgnoreCase(profile.name)) continue;
            
            for (String keyword : profile.userAgentKeywords) {
                if (ua.contains(keyword.toLowerCase())) {
                    return profile;
                }
            }
        }

        return PROFILES.get("default");
    }
}
