package apincer.music.core.server;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;



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
            return 262144; // 256KB for high-end devices (Hi-Res Audio Sweet Spot)
        } else if (memoryClass > 128) {
            return 131072;  // 128KB standard
        } else {
            return 65536;  // 64KB low mem
        }
    }

    private void initProfiles(Context context) {
        // 1. Default Profile (Hardcoded fallback)
        PROFILES.put("default", ClientProfile.standard(globalBufferSize));

        // 2. Load from JSON
        try (InputStream is = context.getResources().openRawResource(R.raw.client_profiles)) {
            String jsonText = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONArray profilesArray = new org.json.JSONArray(jsonText);
            
            for (int i = 0; i < profilesArray.length(); i++) {
                org.json.JSONObject obj = profilesArray.getJSONObject(i);
                String name = obj.optString("name", "unknown");
                int chunkSize = obj.optInt("chunkSize", -1);
                boolean keepAlive = obj.optBoolean("keepAlive", true);
                int maxConnections = obj.optInt("maxConnections", 100);
                boolean supportsGapless = obj.optBoolean("supportsGapless", false);
                boolean supportsHighRes = obj.optBoolean("supportsHighRes", false);
                boolean supportsDirectStreaming = obj.optBoolean("supportsDirectStreaming", false);
                boolean supportsLosslessStreaming = obj.optBoolean("supportsLosslessStreaming", false);
                boolean supportsBitPerfectStreaming = obj.optBoolean("supportsBitPerfectStreaming", false);
                
                List<String> keywords = new java.util.ArrayList<>();
                org.json.JSONArray kwArray = obj.optJSONArray("userAgentKeywords");
                if (kwArray != null) {
                    for (int j = 0; j < kwArray.length(); j++) {
                        keywords.add(kwArray.getString(j));
                    }
                }
                
                int finalChunkSize = (chunkSize == -1) ? globalBufferSize : chunkSize;
                ClientProfile profile = new ClientProfile(
                        name,
                        finalChunkSize,
                        keepAlive,
                        maxConnections,
                        supportsGapless,
                        supportsHighRes,
                        supportsDirectStreaming,
                        supportsLosslessStreaming,
                        supportsBitPerfectStreaming,
                        keywords
                );
                PROFILES.put(name.toLowerCase(), profile);
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
