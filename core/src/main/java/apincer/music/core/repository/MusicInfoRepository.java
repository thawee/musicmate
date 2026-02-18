package apincer.music.core.repository;

import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import apincer.music.core.model.TrackInfo;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody; // Import ResponseBody

/**
 * Service to fetch additional music metadata (like album description)
 * primarily using the Wikipedia (MediaWiki) API.
 *
 * <p>Note: Artist biographies and genres are not currently fetched by this service.</p>
 */
public class MusicInfoRepository {

    private static final String TAG = "MusicInfoRepository"; // Renamed TAG
    private static final String WIKIPEDIA_API_ENDPOINT = "en.wikipedia.org"; // Base host for Wikipedia API
    // Set of common names indicating a compilation album artist
    private static final Set<String> VARIOUS_ARTIST_NAMES = new HashSet<>(Arrays.asList(
            "various artists", "va", "v/a", "soundtrack", "original soundtrack"
            // Add more variations as needed, all lowercase
    ));
    // Create a single, shared OkHttpClient instance. Efficient for multiple requests.
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Define the type for Jackson parsing of Wikipedia response
    private static final TypeReference<Map<String, Object>> WIKIPEDIA_RESPONSE_TYPE = new TypeReference<Map<String, Object>>() {};


    /**
     * Fetches aggregated track information from Wikipedia.
     *
     * @param trackArtist The specific artist performing the track.
     * @param albumArtist The artist credited for the album.
     * @param album       The name of the album.
     * @param year        The release year of the album (can be null or empty).
     * @return A {@link TrackInfo} object or {@code null}.
     */
    public TrackInfo getFullTrackInfo(String trackArtist, String albumArtist, String album, String year) {
        if (trackArtist == null || trackArtist.isBlank() ||
                albumArtist == null || albumArtist.isBlank() ||
                album == null || album.isBlank()) {
            Log.w(TAG, "Track artist, album artist, or album name is blank.");
            return null;
        }

        try {

            String albumDescription = getWikipediaAlbumInfo(albumArtist, album, year);
            if (albumDescription == null || albumDescription.isEmpty()) {
                Log.d(TAG, "No album description found on Wikipedia for: " + album + " by " + trackArtist);
                albumDescription = "No album information found."; // Set a default message
            }

            // Placeholders - Wikipedia doesn't easily provide these in the same query
            String artistBio = ""; // Placeholder - Could make another Wikipedia call for the artist page
            String highResArtUrl = null; // Placeholder - Wikipedia doesn't host album art directly in API
            List<String> genres = Collections.emptyList(); // Placeholder

            return new TrackInfo(artistBio, albumDescription, highResArtUrl, genres);

        } catch (Exception e) {
            Log.e(TAG, "Network error fetching Wikipedia info for artist: " + trackArtist + ", album: " + album, e);
            return null;
        }
    }

    /**
     * Fetches the introductory section (extract) of a Wikipedia page for a given album and artist.
     *
     * @param artist The artist name.
     * @param album  The album name.
     * @return The plain text extract of the Wikipedia page, or {@code null} if not found or an error occurs.
     * @throws IOException If a network error occurs.
     */
    private String getWikipediaAlbumInfo(String artist, String album) throws IOException {
        // Attempt to construct a likely Wikipedia page title format.
        // This might need refinement for edge cases (disambiguation, special characters).
        String pageTitle = album + " (" + artist + " album)";
        Log.d(TAG, "Attempting to fetch Wikipedia page: " + pageTitle);

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(WIKIPEDIA_API_ENDPOINT)
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("prop", "extracts") // Request page extracts (summaries)
                .addQueryParameter("exintro", "true")   // Get only the intro section
                .addQueryParameter("explaintext", "true") // Get plain text, not HTML
                .addQueryParameter("redirects", "1")    // Automatically follow redirects
                .addQueryParameter("titles", pageTitle)  // The page title we are looking for
                .addQueryParameter("origin", "*")       // Required for CORS if called from browser JS
                .build();

        // See: https://meta.wikimedia.org/wiki/User-Agent_policy
        // Format: <App Name>/<Version> (<Contact Info, e.g., email or website>) <Library>/<Version>
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MusicMate/1.0 (thaweemail@gmail.com) OkHttp/" + OkHttpClient.class.getPackage().getImplementationVersion()) // Replace with your app info
                .build(); // No Authorization needed for Wikipedia

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Wikipedia API Error: " + response.code() + " " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Wikipedia API returned empty body.");
            }

            String jsonString = body.string();
            // Log the raw JSON response for debugging
            // Log.v(TAG, "Wikipedia JSON Response: " + jsonString);

            Map<String, Object> responseMap = mapper.readValue(jsonString, WIKIPEDIA_RESPONSE_TYPE);

            // Navigate the Wikipedia JSON structure
            Map<String, Object> query = (Map<String, Object>) responseMap.get("query");
            if (query == null) return null;

            Map<String, Object> pages = (Map<String, Object>) query.get("pages");
            if (pages == null || pages.isEmpty()) return null;

            // The page ID is variable, so we get the first (and likely only) page object
            Map.Entry<String, Object> firstPageEntry = pages.entrySet().iterator().next();
            if (firstPageEntry == null) return null;

            // Check if the page actually exists (pageid -1 means not found)
            String pageIdStr = firstPageEntry.getKey();
            if ("-1".equals(pageIdStr)) {
                Log.d(TAG, "Wikipedia page not found for title: " + pageTitle);
                return null; // Page does not exist
            }

            @SuppressWarnings("unchecked") // Safe cast based on MediaWiki API structure
            Map<String, Object> pageData = (Map<String, Object>) firstPageEntry.getValue();
            if (pageData == null) return null;

            // Return the extract if present, otherwise null
            return (String) pageData.get("extract");

        }
    }

    /**
     * Fetches the introductory section (extract) of a Wikipedia page for a given album and artist.
     * Uses opensearch first to find a better page title, especially for compilations.
     *
     * @param artist The album artist name.
     * @param album  The album name.
     * @param year   The release year of the album (can be null or empty). Helps disambiguate.
     * @return The plain text extract of the Wikipedia page, or {@code null} if not found or an error occurs.
     * @throws IOException If a network error occurs.
     */
    private String getWikipediaAlbumInfo(String artist, String album, String year) throws IOException {
        // --- Step 1: Use OpenSearch to find the best page title ---
        String bestPageTitle = findWikipediaPageTitle(artist, album, year);

        if (bestPageTitle == null || bestPageTitle.isEmpty()) {
            Log.d(TAG, "OpenSearch did not return a suitable page title for: " + album);
            return null; // Stop if we couldn't find a likely page title
        }

        // --- Step 2: Fetch the extract using the found title ---
        Log.d(TAG, "Fetching extract for Wikipedia page: " + bestPageTitle);

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(WIKIPEDIA_API_ENDPOINT)
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("prop", "extracts")
                .addQueryParameter("exintro", "true")
                .addQueryParameter("explaintext", "true")
                .addQueryParameter("redirects", "1")
                .addQueryParameter("titles", bestPageTitle) // Use the title from OpenSearch
                .addQueryParameter("origin", "*")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MusicMate/1.0 (thaweemail@gmail.com) OkHttp/" + OkHttpClient.class.getPackage().getImplementationVersion()) // Replace with your app info
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                Log.e(TAG, "Wikipedia Extract API Error Response: " + errorBody);
                throw new IOException("Wikipedia Extract API Error: " + response.code() + " " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) throw new IOException("Wikipedia Extract API returned empty body.");

            String jsonString = body.string();
            Map<String, Object> responseMap = mapper.readValue(jsonString, WIKIPEDIA_RESPONSE_TYPE);

            Map<String, Object> query = (Map<String, Object>) responseMap.get("query");
            if (query == null) return null;

            Map<String, Object> pages = (Map<String, Object>) query.get("pages");
            if (pages == null || pages.isEmpty()) return null;

            Map.Entry<String, Object> firstPageEntry = pages.entrySet().iterator().next();
            if (firstPageEntry == null) return null;

            String pageIdStr = firstPageEntry.getKey();
            if ("-1".equals(pageIdStr)) {
                Log.d(TAG, "Wikipedia page ID -1 for title (might be redirect issue or page deleted): " + bestPageTitle);
                return null; // Page does not exist or wasn't resolved correctly
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> pageData = (Map<String, Object>) firstPageEntry.getValue();
            if (pageData == null) return null;

            return (String) pageData.get("extract");
        }
    }

    /**
     * Uses Wikipedia's OpenSearch API to find the most likely page title for an album.
     *
     * @param artist Album artist.
     * @param album Album title.
     * @param year Release year (optional).
     * @return The best guess for the page title, or null if none found.
     * @throws IOException Network errors.
     */
    private String findWikipediaPageTitle(String artist, String album, String year) throws IOException {
        // Removes parentheses/brackets from the raw album/artist text
        // which *are* known to break the search.
        String cleanAlbum = album.replaceAll("[\\[\\](){}]", "").replaceAll("\\s+", " ").trim();
        String cleanArtist = artist.replaceAll("[\\[\\](){}]", "").replaceAll("\\s+", " ").trim();

        // Construct a search query, adding year if available
        String searchQuery;
        boolean hasYear = (year != null && !year.isBlank());

        if (isVariousArtists(cleanArtist)) {
            searchQuery = cleanAlbum + (hasYear ? " (" + year + " album)" : " (compilation album)");
        } else {
            if (hasYear) {
                searchQuery = cleanAlbum + " (" + year + ") " + cleanArtist + " album";
            } else {
                searchQuery = cleanAlbum + " " + cleanArtist + " album";
            }
        }

        Log.d(TAG, "Performing OpenSearch for: " + searchQuery);

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(WIKIPEDIA_API_ENDPOINT)
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "opensearch")
                .addQueryParameter("format", "json")
                .addQueryParameter("limit", "1") // We only need the top suggestion
                .addQueryParameter("search", searchQuery)
                .addQueryParameter("namespace", "*")
                .addQueryParameter("origin", "*")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MusicMate/1.0 (thaweemail@gmail.com) OkHttp/" + OkHttpClient.class.getPackage().getImplementationVersion()) // Replace with your app info
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                Log.e(TAG, "Wikipedia OpenSearch API Error Response: " + errorBody);
                throw new IOException("Wikipedia OpenSearch API Error: " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Wikipedia OpenSearch API returned empty body.");

            String jsonString = body.string();
            JsonNode rootNode = mapper.readTree(jsonString);

            if (rootNode.isArray() && rootNode.size() > 1) {
                JsonNode titles = rootNode.get(1);
                if (titles.isArray() && titles.size() > 0) {
                    String title = titles.get(0).asText();
                    Log.i(TAG, "OpenSearch SUCCESS for '" + searchQuery + "'. Found: " + title);
                    return title; // Return the first suggested title
                }
            }

            // THIS IS THE NEW LOGGING
            Log.w(TAG, "OpenSearch NO RESULTS for query: '" + searchQuery + "'. Full response: " + jsonString);
            return null; // No titles suggested
        }
    }

    /**
     * Checks if an artist name likely refers to a compilation.
     * Case-insensitive check.
     * @param artistName The artist name to check.
     * @return true if the name is considered generic, false otherwise.
     */
    private boolean isVariousArtists(String artistName) {
        return artistName != null && VARIOUS_ARTIST_NAMES.contains(artistName.toLowerCase());
    }
}