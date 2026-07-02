package apincer.music.core.repository;

import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MusicBrainzClient {
    private static final String TAG = "MusicBrainzClient";
    private static final String BASE_URL = "https://musicbrainz.org/ws/2/";
    private static final String USER_AGENT = "MusicMate/1.0 (thaweemail@gmail.com)";
    
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public MusicBrainzClient() {
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Search MusicBrainz for a recording by title and artist.
     * @return The MBID (MusicBrainz ID) of the best matching recording, or null.
     */
    public String searchRecording(String title, String artist) {
        if (title == null || title.isBlank()) return null;
        
        try {
            String query = "recording:\"" + title.replace("\"", "\\\"") + "\"";
            if (artist != null && !artist.isBlank()) {
                query += " AND artist:\"" + artist.replace("\"", "\\\"") + "\"";
            }
            
            HttpUrl url = HttpUrl.parse(BASE_URL + "recording").newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("fmt", "json")
                    .addQueryParameter("limit", "1")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "MusicBrainz API error: " + response.code());
                    return null;
                }
                
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode recordings = root.path("recordings");
                if (recordings.isArray() && recordings.size() > 0) {
                    return recordings.get(0).path("id").asText(null);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching from MusicBrainz", e);
        }
        return null;
    }

    /**
     * Fetch detailed recording metadata using an MBID.
     */
    public MusicBrainzMetadata getRecordingMetadata(String mbid) {
        if (mbid == null || mbid.isBlank()) return null;

        try {
            HttpUrl url = HttpUrl.parse(BASE_URL + "recording/" + mbid).newBuilder()
                    .addQueryParameter("inc", "artists+releases+release-groups+genres")
                    .addQueryParameter("fmt", "json")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "MusicBrainz API error: " + response.code());
                    return null;
                }

                JsonNode root = mapper.readTree(response.body().string());
                MusicBrainzMetadata metadata = new MusicBrainzMetadata();
                metadata.title = root.path("title").asText(null);
                
                // Parse artist
                JsonNode artistCredit = root.path("artist-credit");
                if (artistCredit.isArray() && artistCredit.size() > 0) {
                    metadata.artist = artistCredit.get(0).path("name").asText(null);
                }

                // Parse release/album using a scoring system to select the best release (e.g. official studio album)
                JsonNode releases = root.path("releases");
                if (releases.isArray() && releases.size() > 0) {
                    JsonNode bestRelease = null;
                    int maxScore = Integer.MIN_VALUE;
                    String earliestDate = null;

                    for (JsonNode release : releases) {
                        int score = 0;

                        // 1. Status: Prefer "Official" releases
                        String status = release.path("status").asText("");
                        if ("Official".equalsIgnoreCase(status)) {
                            score += 100;
                        }

                        // 2. Primary Type: Prefer Album -> EP -> Single
                        JsonNode releaseGroup = release.path("release-group");
                        String primaryType = releaseGroup.path("primary-type").asText("");
                        if ("Album".equalsIgnoreCase(primaryType)) {
                            score += 50;
                        } else if ("EP".equalsIgnoreCase(primaryType)) {
                            score += 30;
                        } else if ("Single".equalsIgnoreCase(primaryType)) {
                            score += 10;
                        }

                        // 3. Secondary Types: Deprioritize Compilation, Live, Remix, Soundtrack
                        JsonNode secondaryTypes = releaseGroup.path("secondary-types");
                        if (secondaryTypes.isArray()) {
                            for (JsonNode typeNode : secondaryTypes) {
                                String secType = typeNode.asText("");
                                if ("Compilation".equalsIgnoreCase(secType)) {
                                    score -= 30;
                                } else if ("Live".equalsIgnoreCase(secType)) {
                                    score -= 20;
                                } else if ("Remix".equalsIgnoreCase(secType)) {
                                    score -= 20;
                                } else if ("Soundtrack".equalsIgnoreCase(secType)) {
                                    score -= 10;
                                }
                            }
                        }

                        // 4. Cover Art availability: Prefer releases with front/artwork cover art
                        JsonNode caa = release.path("cover-art-archive");
                        if (caa.path("front").asBoolean(false)) {
                            score += 80;
                        } else if (caa.path("artwork").asBoolean(false)) {
                            score += 40;
                        }

                        // 5. Has date/year
                        String date = release.path("date").asText("");
                        if (!date.isBlank()) {
                            score += 5;
                        }

                        // Select the release with the highest score, breaking ties with the earliest date
                        if (bestRelease == null) {
                            bestRelease = release;
                            maxScore = score;
                            earliestDate = date;
                        } else if (score > maxScore) {
                            bestRelease = release;
                            maxScore = score;
                            earliestDate = date;
                        } else if (score == maxScore) {
                            // Tie-breaker: prefer the earliest release date (usually original studio release)
                            if (!date.isBlank()) {
                                if (earliestDate == null || earliestDate.isBlank()) {
                                    bestRelease = release;
                                    earliestDate = date;
                                } else if (date.compareTo(earliestDate) < 0) {
                                    bestRelease = release;
                                    earliestDate = date;
                                }
                            }
                        }
                    }

                    if (bestRelease != null) {
                        metadata.album = bestRelease.path("title").asText(null);
                        metadata.releaseId = bestRelease.path("id").asText(null);
                        metadata.year = bestRelease.path("date").asText(null); // Often YYYY-MM-DD
                        if (metadata.year != null && metadata.year.length() > 4) {
                            metadata.year = metadata.year.substring(0, 4);
                        }
                    }
                }

                // Parse genres
                JsonNode genres = root.path("genres");
                if (genres.isArray() && genres.size() > 0) {
                    metadata.genre = genres.get(0).path("name").asText(null);
                    // capitalize first letter
                    if (metadata.genre != null && !metadata.genre.isEmpty()) {
                        metadata.genre = metadata.genre.substring(0, 1).toUpperCase() + metadata.genre.substring(1);
                    }
                }

                return metadata;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching recording details from MusicBrainz", e);
        }
        return null;
    }

    /**
     * Downloads cover art from the Cover Art Archive and saves it to a local file.
     * @param releaseId The MusicBrainz Release ID
     * @param targetFile The local file to save the image to (e.g. Cover.jpg)
     * @return true if successful, false otherwise
     */
    public boolean downloadCoverArt(String releaseId, java.io.File targetFile) {
        if (releaseId == null || releaseId.isBlank()) return false;
        
        try {
            HttpUrl url = HttpUrl.parse("https://coverartarchive.org/release/" + releaseId).newBuilder().build();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode images = root.path("images");
                if (images.isArray() && images.size() > 0) {
                    // Try to find the "front" image
                    String imageUrl = null;
                    for (JsonNode image : images) {
                        if (image.path("front").asBoolean()) {
                            imageUrl = image.path("image").asText(null);
                            break;
                        }
                    }
                    if (imageUrl == null) {
                        imageUrl = images.get(0).path("image").asText(null);
                    }
                    
                    if (imageUrl != null) {
                        // Download the image
                        Request imageReq = new Request.Builder()
                                .url(imageUrl)
                                .header("User-Agent", USER_AGENT)
                                .build();
                        try (Response imgRes = httpClient.newCall(imageReq).execute()) {
                            if (imgRes.isSuccessful() && imgRes.body() != null) {
                                // Save to file
                                try (java.io.InputStream is = imgRes.body().byteStream();
                                     java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                                    byte[] buffer = new byte[8192];
                                    int read;
                                    while ((read = is.read(buffer)) != -1) {
                                        fos.write(buffer, 0, read);
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error downloading cover art", e);
        }
        return false;
    }

    public static class MusicBrainzMetadata {
        public String title;
        public String artist;
        public String album;
        public String releaseId;
        public String year;
        public String genre;
    }
}
