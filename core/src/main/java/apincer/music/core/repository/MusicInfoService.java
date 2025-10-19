package apincer.music.core.repository;

import android.util.Log; // Import Log

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import apincer.music.core.model.TrackInfo;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service to fetch additional music metadata (like genres, art URLs)
 * primarily using the Spotify Web API.
 *
 * <p>Note: Currently, artist and album biographies are not fetched by this service
 * as the Spotify API doesn't provide them. Integration with another service (e.g., Last.fm)
 * would be required for biographies.</p>
 */
public class MusicInfoService {

    private static final String TAG = "MusicInfoService"; // Added TAG for logging
    private static final String SPOTIFY_CLIENT_ID = "da1af9bdc62e4f1cbbb61b34d84f7b29";
    private static final String SPOTIFY_CLIENT_SECRET = "07ec68e2f9a849868e85bc33bfb28e18";

    // Create a single, shared OkHttpClient instance. Efficient for multiple requests.
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    // Cache for the Spotify API access token to avoid re-authentication on every call.
    private String spotifyAccessToken;
    private long tokenExpiryTime;

    /**
     * Fetches aggregated track information (genres, high-res art URL) from Spotify
     * based on the provided artist and album name.
     *
     * @param artist The name of the artist. Cannot be null or blank.
     * @param album  The name of the album.
     * @return A {@link TrackInfo} object containing the fetched metadata,
     * or {@code null} if the information could not be retrieved or if the artist is invalid.
     * Biographies are currently hard-coded placeholders.
     */
    public TrackInfo getFullTrackInfo(String artist, String album) {
        if (artist == null || artist.isBlank()) return null;
        try {
            Map<String, Object> albumSearchResult = searchSpotify(artist, album);
            if (albumSearchResult == null) return null;

            String artistId = getArtistIdFromSearch(albumSearchResult);
            if (artistId == null) return null;

            Map<String, Object> artistDetails = getSpotifyArtistDetails(artistId);

            // TODO: Fetch real artist and album bios from a service like Last.fm
            String artistBio = "Find more about the artist on Spotify.";
            String albumBio = ""; // Placeholder

            String highResArtUrl = getImageUrlFromSearch(albumSearchResult);

            @SuppressWarnings("unchecked") // Safe cast based on Spotify API structure
            List<String> genres = (List<String>) artistDetails.getOrDefault("genres", Collections.emptyList());

            return new TrackInfo(artistBio, albumBio, highResArtUrl, genres);
        } catch (Exception e) {
            // Use Android logging
            Log.e(TAG, "Failed to get full track info for artist: " + artist + ", album: " + album, e);
            return null;
        }
    }

    /**
     * Searches the Spotify API for an album matching the given artist and album name.
     * Uses the cached access token.
     *
     * @param artist The artist name.
     * @param album  The album name.
     * @return A Map representing the first album item found, or {@code null} if none found or error.
     * @throws IOException If a network error occurs or the Spotify API returns an error.
     */
    private Map<String, Object> searchSpotify(String artist, String album) throws IOException {
        String query = String.format("artist:\"%s\" album:\"%s\"", artist, album);
        // Corrected URL
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.spotify.com")
                .addPathSegment("v1")
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("type", "album")
                .addQueryParameter("limit", "1")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + getSpotifyAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Spotify Search API Error: " + response.code() + " " + response.message());

            // Using TypeToken for safer deserialization of generic types
            Map<String, Object> responseMap = gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
            Map<String, Object> albums = (Map<String, Object>) responseMap.get("albums");
            if (albums == null) return null; // Handle cases where 'albums' key might be missing

            @SuppressWarnings("unchecked") // Safe cast based on Spotify API structure
            List<Map<String, Object>> items = (List<Map<String, Object>>) albums.get("items");
            if (items == null) return null; // Handle cases where 'items' key might be missing

            return items.isEmpty() ? null : items.get(0);
        }
    }

    /**
     * Fetches detailed artist information (including genres) from the Spotify API using the artist ID.
     * Uses the cached access token.
     *
     * @param artistId The Spotify artist ID.
     * @return A Map representing the artist details, or {@code null} on error.
     * @throws IOException If a network error occurs or the Spotify API returns an error.
     */
    private Map<String, Object> getSpotifyArtistDetails(String artistId) throws IOException {
        // Corrected URL
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.spotify.com")
                .addPathSegment("v1")
                .addPathSegment("artists")
                .addPathSegment(artistId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + getSpotifyAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Spotify Artist API Error: " + response.code() + " " + response.message());
            return gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
        }
    }

    /**
     * Retrieves a valid Spotify API access token, either from the cache or
     * by requesting a new one using Client Credentials flow.
     *
     * @return A valid Spotify access token.
     * @throws IOException If authentication fails or a network error occurs.
     */
    private String getSpotifyAccessToken() throws IOException {
        // Return cached token if still valid
        if (spotifyAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return spotifyAccessToken;
        }

        Log.d(TAG, "Spotify token expired or not present, requesting new token..."); // Added log

        String authHeader = "Basic " + Base64.getEncoder().encodeToString((SPOTIFY_CLIENT_ID + ":" + SPOTIFY_CLIENT_SECRET).getBytes());
        RequestBody body = RequestBody.create("grant_type=client_credentials", MediaType.get("application/x-www-form-urlencoded"));

        // Corrected URL
        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", authHeader)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Spotify Token API Error: " + response.code() + " " + response.message());

            Map<String, Object> responseMap = gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
            this.spotifyAccessToken = (String) responseMap.get("access_token");
            // Handle potential ClassCastException if 'expires_in' is not a Double
            Object expiresInObj = responseMap.get("expires_in");
            if (!(expiresInObj instanceof Double)) {
                throw new IOException("Invalid 'expires_in' type received from Spotify token endpoint.");
            }
            double expiresIn = (Double) expiresInObj;

            // Calculate expiry time (subtract 5 seconds buffer)
            this.tokenExpiryTime = System.currentTimeMillis() + ((long) (expiresIn * 1000)) - 5000;
            Log.d(TAG, "Successfully obtained new Spotify token."); // Added log
            return this.spotifyAccessToken;
        }
    }

    // --- Helper methods to safely extract data from the nested JSON ---

    /**
     * Safely extracts the artist ID from the Spotify album search result item.
     *
     * @param albumItem The Map representing an album item from Spotify search.
     * @return The artist ID string, or {@code null} if not found.
     */
    private String getArtistIdFromSearch(Map<String, Object> albumItem) {
        if (albumItem == null) return null;
        @SuppressWarnings("unchecked") // Safe cast based on Spotify API structure
        List<Map<String, Object>> artists = (List<Map<String, Object>>) albumItem.get("artists");
        if (artists != null && !artists.isEmpty()) {
            Map<String, Object> firstArtist = artists.get(0);
            if (firstArtist != null) {
                return (String) firstArtist.get("id");
            }
        }
        return null;
    }

    /**
     * Safely extracts the highest resolution image URL from the Spotify album search result item.
     * Spotify typically returns images sorted from highest to lowest resolution.
     *
     * @param albumItem The Map representing an album item from Spotify search.
     * @return The image URL string, or {@code null} if not found.
     */
    private String getImageUrlFromSearch(Map<String, Object> albumItem) {
        if (albumItem == null) return null;
        @SuppressWarnings("unchecked") // Safe cast based on Spotify API structure
        List<Map<String, Object>> images = (List<Map<String, Object>>) albumItem.get("images");
        if (images != null && !images.isEmpty()) {
            Map<String, Object> firstImage = images.get(0);
            if (firstImage != null) {
                return (String) firstImage.get("url");
            }
        }
        return null;
    }
}