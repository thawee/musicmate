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
    
    private static final Object RATE_LIMIT_LOCK = new Object();
    private static long lastRequestTime = 0;
    private static final long RATE_LIMIT_MS = 1050; // 1 second limit + buffer

    private static final java.util.Set<String> GENERIC_GENRES = new java.util.HashSet<>(java.util.Arrays.asList(
        "various", "various artists", "unknown", "compilation", "other",
        "mix", "remix", "soundtrack", "misc", "miscellaneous",
        "va", "several", "noise", "metadata", "unknown genre"
    ));

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public MusicBrainzClient() {
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    private void enforceRateLimit() {
        synchronized (RATE_LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            long diff = now - lastRequestTime;
            if (diff < RATE_LIMIT_MS) {
                try {
                    Thread.sleep(RATE_LIMIT_MS - diff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
        }
    }

    private Response executeRequestWithRetry(Request request) throws IOException {
        enforceRateLimit();
        Response response = httpClient.newCall(request).execute();
        
        // MusicBrainz returns 503 or 429 when rate limited
        if (response.code() == 503 || response.code() == 429) {
            response.close();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            enforceRateLimit();
            response = httpClient.newCall(request).execute();
        }
        return response;
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

            try (Response response = executeRequestWithRetry(request)) {
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

            try (Response response = executeRequestWithRetry(request)) {
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

                // Parse genres, skipping generic folksonomy/non-genre tags if possible
                JsonNode genres = root.path("genres");
                if (genres.isArray() && genres.size() > 0) {
                    String selectedGenre = null;
                    for (JsonNode genreNode : genres) {
                        String genreName = genreNode.path("name").asText(null);
                        if (genreName != null && !genreName.isBlank()) {
                            String lowerGenre = genreName.trim().toLowerCase(java.util.Locale.ROOT);
                            if (!GENERIC_GENRES.contains(lowerGenre)) {
                                selectedGenre = genreName.trim();
                                break;
                            }
                        }
                    }
                    
                    // Fallback to the first available genre tag if all tags are generic/blocked
                    if (selectedGenre == null) {
                        for (JsonNode genreNode : genres) {
                            String genreName = genreNode.path("name").asText(null);
                            if (genreName != null && !genreName.isBlank()) {
                                selectedGenre = genreName.trim();
                                break;
                            }
                        }
                    }

                    if (selectedGenre != null && !selectedGenre.isEmpty()) {
                        // capitalize first letter
                        metadata.genre = selectedGenre.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + selectedGenre.substring(1);
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
        
        // Strategy 1: Attempt direct download of the 500px front thumbnail.
        // This is highly optimized for size/speed and uses a single request.
        try {
            HttpUrl url = HttpUrl.parse("https://coverartarchive.org/release/" + releaseId + "/front-500").newBuilder().build();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    try (java.io.InputStream is = response.body().byteStream();
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
        } catch (IOException e) {
            Log.w(TAG, "Failed to download front-500 thumbnail, trying fallback", e);
        }

        // Strategy 2: Fallback to querying release JSON metadata to find any available image
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
                    String imageUrl = null;
                    // Try to find any image marked as front, otherwise fallback to the first available image
                    for (JsonNode image : images) {
                        if (image.path("front").asBoolean()) {
                            imageUrl = image.path("thumbnails").path("500").asText(null);
                            if (imageUrl == null) {
                                imageUrl = image.path("image").asText(null);
                            }
                            break;
                        }
                    }
                    if (imageUrl == null) {
                        imageUrl = images.get(0).path("thumbnails").path("500").asText(null);
                        if (imageUrl == null) {
                            imageUrl = images.get(0).path("image").asText(null);
                        }
                    }
                    
                    if (imageUrl != null) {
                        Request imageReq = new Request.Builder()
                                .url(imageUrl)
                                .header("User-Agent", USER_AGENT)
                                .build();
                        try (Response imgRes = httpClient.newCall(imageReq).execute()) {
                            if (imgRes.isSuccessful() && imgRes.body() != null) {
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
            Log.e(TAG, "Error downloading cover art in fallback mode", e);
        }
        return false;
    }

    public java.util.List<MusicBrainzSearchResult> searchRecordingsList(String title, String artist, int limit) {
        java.util.List<MusicBrainzSearchResult> results = new java.util.ArrayList<>();
        if (title == null || title.isBlank()) return results;
        
        try {
            String query = "recording:\"" + title.replace("\"", "\\\"") + "\"";
            if (artist != null && !artist.isBlank()) {
                query += " AND artist:\"" + artist.replace("\"", "\\\"") + "\"";
            }
            
            HttpUrl url = HttpUrl.parse(BASE_URL + "recording").newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("fmt", "json")
                    .addQueryParameter("limit", String.valueOf(limit))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = executeRequestWithRetry(request)) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "MusicBrainz API error: " + response.code());
                    return results;
                }
                
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode recordings = root.path("recordings");
                if (recordings.isArray()) {
                    for (JsonNode rec : recordings) {
                        MusicBrainzSearchResult res = new MusicBrainzSearchResult();
                        res.recordingId = rec.path("id").asText(null);
                        res.title = rec.path("title").asText(null);
                        
                        // Parse artist
                        JsonNode artistCredit = rec.path("artist-credit");
                        if (artistCredit.isArray() && artistCredit.size() > 0) {
                            res.artist = artistCredit.get(0).path("name").asText(null);
                        }
                        
                        // Parse releases
                        JsonNode releases = rec.path("releases");
                        if (releases.isArray() && releases.size() > 0) {
                            // Find the best release if possible, or fallback to the first
                            JsonNode bestRelease = releases.get(0);
                            int maxScore = Integer.MIN_VALUE;
                            for (JsonNode rel : releases) {
                                int score = 0;
                                String status = rel.path("status").asText("");
                                if ("Official".equalsIgnoreCase(status)) score += 10;
                                
                                String primType = rel.path("release-group").path("primary-type").asText("");
                                if ("Album".equalsIgnoreCase(primType)) score += 5;
                                
                                if (score > maxScore) {
                                    maxScore = score;
                                    bestRelease = rel;
                                }
                            }
                            
                            res.album = bestRelease.path("title").asText(null);
                            res.releaseId = bestRelease.path("id").asText(null);
                            res.year = bestRelease.path("date").asText(null);
                            if (res.year != null && res.year.length() > 4) {
                                res.year = res.year.substring(0, 4);
                            }
                        }
                        results.add(res);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error performing search list from MusicBrainz", e);
        }
        return results;
    }

    public static class MusicBrainzSearchResult {
        public String recordingId;
        public String title;
        public String artist;
        public String album;
        public String releaseId;
        public String year;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            if (artist != null && !artist.isBlank()) {
                sb.append(" - ").append(artist);
            }
            if (album != null && !album.isBlank()) {
                sb.append(" (").append(album);
                if (year != null && !year.isBlank()) {
                    sb.append(", ").append(year);
                }
                sb.append(")");
            }
            return sb.toString();
        }
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
