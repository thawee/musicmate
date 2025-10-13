package apincer.android.mmate.core.repository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.core.model.TrackInfo;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MusicInfoService {

    private static final String SPOTIFY_CLIENT_ID = "da1af9bdc62e4f1cbbb61b34d84f7b29";
    private static final String SPOTIFY_CLIENT_SECRET = "07ec68e2f9a849868e85bc33bfb28e18";

    // 1. Create a single, shared OkHttpClient instance.
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    private String spotifyAccessToken;
    private long tokenExpiryTime;

    /**
     * Main method to get aggregated info for a track.
     */
    public TrackInfo getFullTrackInfo(String artist, String album) {
        // This method's logic doesn't need to change.
        if (artist == null || artist.isBlank()) return null;
        try {
            Map<String, Object> albumSearchResult = searchSpotify(artist, album);
            if (albumSearchResult == null) return null;
            String artistId = getArtistIdFromSearch(albumSearchResult);
            if (artistId == null) return null;
            Map<String, Object> artistDetails = getSpotifyArtistDetails(artistId);
            String artistBio = "Find more about the artist on Spotify.";
            String highResArtUrl = getImageUrlFromSearch(albumSearchResult);
            List<String> genres = (List<String>) artistDetails.getOrDefault("genres", Collections.emptyList());
            return new TrackInfo(artistBio, highResArtUrl, genres);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Searches Spotify for an album. (Refactored for OkHttp)
     */
    private Map<String, Object> searchSpotify(String artist, String album) throws IOException {
        String query = String.format("artist:\"%s\" album:\"%s\"", artist, album);
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

        // Use a try-with-resources block to ensure the response is always closed.
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            Map<String, Object> responseMap = gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
            Map<String, Object> albums = (Map<String, Object>) responseMap.get("albums");
            List<Map<String, Object>> items = (List<Map<String, Object>>) albums.get("items");

            return items.isEmpty() ? null : items.get(0);
        }
    }

    /**
     * Gets detailed artist information. (Refactored for OkHttp)
     */
    private Map<String, Object> getSpotifyArtistDetails(String artistId) throws IOException {
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
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
        }
    }

    /**
     * Authenticates with Spotify to get an access token. (Refactored for OkHttp)
     */
    private String getSpotifyAccessToken() throws IOException {
        if (spotifyAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return spotifyAccessToken;
        }

        String authHeader = "Basic " + Base64.getEncoder().encodeToString((SPOTIFY_CLIENT_ID + ":" + SPOTIFY_CLIENT_SECRET).getBytes());
        RequestBody body = RequestBody.create("grant_type=client_credentials", MediaType.get("application/x-www-form-urlencoded"));

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", authHeader)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            Map<String, Object> responseMap = gson.fromJson(response.body().string(), new TypeToken<Map<String, Object>>(){}.getType());
            this.spotifyAccessToken = (String) responseMap.get("access_token");
            double expiresIn = (Double) responseMap.get("expires_in");
            this.tokenExpiryTime = System.currentTimeMillis() + ((long) expiresIn * 1000) - 5000;
            return this.spotifyAccessToken;
        }
    }

    // Helper methods to safely extract data from the nested JSON
    private String getArtistIdFromSearch(Map<String, Object> albumItem) {
        List<Map<String, Object>> artists = (List<Map<String, Object>>) albumItem.get("artists");
        if (artists != null && !artists.isEmpty()) {
            return (String) artists.get(0).get("id");
        }
        return null;
    }

    private String getImageUrlFromSearch(Map<String, Object> albumItem) {
        List<Map<String, Object>> images = (List<Map<String, Object>>) albumItem.get("images");
        if (images != null && !images.isEmpty()) {
            // Typically the first image is the highest resolution
            return (String) images.get(0).get("url");
        }
        return null;
    }
}