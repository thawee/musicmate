package apincer.music.core.repository;

import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AcoustIdClient {
    private static final String TAG = "AcoustIdClient";
    private static final String BASE_URL = "https://api.acoustid.org/v2/lookup";
    // Official AcoustID demo API key, usually fine for light usage, but should be replaced
    private static final String CLIENT_KEY = "8XaBELgH"; 

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public AcoustIdClient() {
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Looks up an audio file by generating an acoustic fingerprint using FFmpegKit.
     * @param filePath The local path to the audio file.
     * @param durationSeconds The duration of the audio in seconds.
     * @return The MBID (MusicBrainz ID) if found, otherwise null.
     */
    public String lookupByFile(String filePath, long durationSeconds) {
        String fingerprint = generateFingerprint(filePath);
        if (fingerprint == null || fingerprint.isEmpty()) {
            return null;
        }

        try {
            HttpUrl url = HttpUrl.parse(BASE_URL).newBuilder()
                    .addQueryParameter("client", CLIENT_KEY)
                    .addQueryParameter("meta", "recordings")
                    .addQueryParameter("duration", String.valueOf(durationSeconds))
                    .addQueryParameter("fingerprint", fingerprint)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "AcoustID API error: " + response.code());
                    return null;
                }

                JsonNode root = mapper.readTree(response.body().string());
                if (!"ok".equals(root.path("status").asText())) {
                    return null;
                }

                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode recordings = results.get(0).path("recordings");
                    if (recordings.isArray() && recordings.size() > 0) {
                        return recordings.get(0).path("id").asText(null);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error looking up AcoustID", e);
        }

        return null;
    }

    /**
     * Uses FFmpegKit to generate a Chromaprint (base64) of the audio file.
     */
    private String generateFingerprint(String filePath) {
        // Run FFmpeg to output chromaprint to stdout
        String command = "-i \"" + filePath.replace("\"", "\\\"") + "\" -vn -ar 44100 -ac 2 -f chromaprint -fp_format base64 -";
        FFmpegSession session = FFmpegKit.execute(command);
        
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            String output = session.getOutput();
            if (output != null) {
                // Parse FINGERPRINT=... from output
                for (String line : output.split("\\r?\\n")) {
                    if (line.startsWith("FINGERPRINT=")) {
                        return line.substring("FINGERPRINT=".length());
                    }
                }
            }
        } else {
            Log.e(TAG, "FFmpeg fingerprinting failed: " + session.getFailStackTrace());
        }
        return null;
    }
}
