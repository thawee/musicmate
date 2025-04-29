package apincer.android.mmate.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Environment;
import android.util.Log;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;

public class MusicApiHandler extends Handler.Abstract {
    private static final String TAG = "MusicApiHandler";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        // Set common headers for API responses
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, JSON_CONTENT_TYPE);
        response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        String path = request.getHttpURI().getPath();
        String method = request.getMethod();
        String query = request.getHttpURI().getQuery();

        Log.d(TAG, "Received API request: " + method + " " + path);

        try {
            // Parse request body if present
            String requestBody = null;
            if (HttpMethod.POST.is(method) || HttpMethod.PUT.is(method)) {
                requestBody = Content.Source.asString(request, UTF_8);
            }

            // Handle different API endpoints
            if (path.equals("/api/music") || path.equals("/api/music/")) {
                if (HttpMethod.GET.is(method)) {
                    // Check if there are search parameters
                    if (query != null && !query.isEmpty()) {
                        return handleSearchMusic(query, response, callback);
                    } else {
                        return handleGetAllMusic(response, callback);
                    }
                } else if (HttpMethod.POST.is(method)) {
                    return handleAddMusic(requestBody, response, callback);
                }
            } else if (path.startsWith("/api/music/")) {
                // Check if it's a move request
                if (path.endsWith("/move") && HttpMethod.POST.is(method)) {
                    String musicId = path.substring("/api/music/".length(), path.length() - "/move".length());
                    return handleMoveMusic(musicId, requestBody, response, callback);
                }

                // Existing code for other endpoints...
                String musicId = path.substring("/api/music/".length());
                if (HttpMethod.GET.is(method)) {
                    return handleGetMusic(musicId, response, callback);
                } else if (HttpMethod.PUT.is(method)) {
                    return handleUpdateMusic(musicId, requestBody, response, callback);
                } else if (HttpMethod.DELETE.is(method)) {
                    return handleDeleteMusic(musicId, response, callback);
                }
            }

            // If endpoint not found, return 404
            response.setStatus(HttpStatus.NOT_FOUND_404);
            Content.Sink.write(response, true, "{\"error\":\"API endpoint not found\"}", callback);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "API error: " + e.getMessage(), e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            Content.Sink.write(response, true, "{\"error\":\"" + e.getMessage() + "\"}", callback);
            return true;
        }
    }

    private boolean handleSearchMusic(String query, Response response, Callback callback) throws Exception {
        // Parse query parameters
        String searchTerm = null;
        String searchType = "all";

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = URLDecoder.decode(keyValue[1], UTF_8);

                if (key.equals("q")) {
                    searchTerm = value.toLowerCase();
                } else if (key.equals("type")) {
                    searchType = value.toLowerCase();
                }
            }
        }

        if (searchTerm == null || searchTerm.isEmpty()) {
            return handleGetAllMusic(response, callback);
        }

        // Get all music from repository
        List<MusicTag> allMusic = TagRepository.getAllMusics();
        List<MusicTag> filteredMusic = new ArrayList<>();

        // Filter based on search term and type
        for (MusicTag music : allMusic) {
            boolean matches = false;

            if (searchType.equals("all")) {
                matches = (music.getTitle() != null && music.getTitle().toLowerCase().contains(searchTerm)) ||
                        (music.getArtist() != null && music.getArtist().toLowerCase().contains(searchTerm)) ||
                        (music.getAlbum() != null && music.getAlbum().toLowerCase().contains(searchTerm));
            } else if (searchType.equals("title")) {
                matches = music.getTitle() != null && music.getTitle().toLowerCase().contains(searchTerm);
            } else if (searchType.equals("artist")) {
                matches = music.getArtist() != null && music.getArtist().toLowerCase().contains(searchTerm);
            } else if (searchType.equals("album")) {
                matches = music.getAlbum() != null && music.getAlbum().toLowerCase().contains(searchTerm);
            }

            if (matches) {
                filteredMusic.add(music);
            }
        }

        // Convert to JSON
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < filteredMusic.size(); i++) {
            MusicTag tag = filteredMusic.get(i);
            appendMusicTagJson(json, tag);

            if (i < filteredMusic.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        response.setStatus(HttpStatus.OK_200);
        Content.Sink.write(response, true, json.toString(), callback);
        return true;
    }

    private boolean handleGetAllMusic(Response response, Callback callback) throws Exception {
        // Get all music from repository
        List<MusicTag> musicList = TagRepository.getAllMusics();

        // Convert to JSON
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < musicList.size(); i++) {
            MusicTag tag = musicList.get(i);
            appendMusicTagJson(json, tag);

            if (i < musicList.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        response.setStatus(HttpStatus.OK_200);
        Content.Sink.write(response, true, json.toString(), callback);
        return true;
    }

    private void appendMusicTagJson(StringBuilder json, MusicTag tag) {
        json.append("{");
        json.append("\"id\":\"").append(tag.getId()).append("\",");
        json.append("\"title\":\"").append(escapeJson(tag.getTitle())).append("\",");
        json.append("\"artist\":\"").append(escapeJson(tag.getArtist())).append("\",");
        json.append("\"album\":\"").append(escapeJson(tag.getAlbum())).append("\",");
        json.append("\"path\":\"").append(escapeJson(tag.getPath())).append("\",");

        // Add album art URL
        json.append("\"albumArtUrl\":\"/coverart/").append(tag.getAlbumUniqueKey()).append(".png\",");

        // Add additional music details
        json.append("\"sampleRate\":\"").append(tag.getAudioSampleRate()).append("\",");
        json.append("\"bitDepth\":\"").append(tag.getAudioBitsDepth()).append("\",");
        json.append("\"grouping\":\"").append(escapeJson(tag.getGrouping())).append("\",");
        json.append("\"mqaInfo\":\"").append(escapeJson(tag.getMqaInd())).append("\",");
        json.append("\"dr\":\"").append(tag.getDynamicRange()).append("\",");
        json.append("\"drScore\":\"").append(tag.getDynamicRangeScore()).append("\",");
        json.append("\"resampleScore\":\"").append(tag.getResampledScore()).append("\",");
        json.append("\"upscaleScore\":\"").append(tag.getUpscaledScore()).append("\"");

        json.append("}");
    }

    private boolean handleGetMusic(String musicId, Response response, Callback callback) throws Exception {
        // Get specific music by ID
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(musicId));

        if (tag == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            Content.Sink.write(response, true, "{\"error\":\"Music not found\"}", callback);
            return true;
        }

        // Convert to JSON
        StringBuilder json = new StringBuilder();
        appendMusicTagJson(json, tag);

        response.setStatus(HttpStatus.OK_200);
        Content.Sink.write(response, true, json.toString(), callback);
        return true;
    }

    private boolean handleAddMusic(String requestBody, Response response, Callback callback) throws Exception {
        // Implementation depends on your music repository
        // This is a placeholder
        response.setStatus(HttpStatus.NOT_IMPLEMENTED_501);
        Content.Sink.write(response, true, "{\"error\":\"Not implemented yet\"}", callback);
        return true;
    }

    private boolean handleDeleteMusic(String musicId, Response response, Callback callback) throws Exception {
        // Implementation depends on your music repository
        // This is a placeholder
        response.setStatus(HttpStatus.NOT_IMPLEMENTED_501);
        Content.Sink.write(response, true, "{\"error\":\"Not implemented yet\"}", callback);
        return true;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Update the handleUpdateMusic method to implement tag editing
    private boolean handleUpdateMusic(String musicId, String requestBody, Response response, Callback callback) throws Exception {
        try {
            // Parse the music ID
            long id = Long.parseLong(musicId);

            // Get the music tag from the repository
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(id);

            if (tag == null) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                Content.Sink.write(response, true, "{\"error\":\"Music not found\"}", callback);
                return true;
            }

            // Parse the JSON request body
            // This is a simple implementation - in production, use a proper JSON parser
            String[] pairs = requestBody.substring(1, requestBody.length() - 1).split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");

                    // Update the tag based on the field
                    switch (key) {
                        case "title":
                            tag.setTitle(value);
                            break;
                        case "trackNumber":
                            try {
                                tag.setTrack(value);
                            } catch (NumberFormatException e) {
                                // Handle invalid number format
                            }
                            break;
                        case "artist":
                            tag.setArtist(value);
                            break;
                        case "album":
                            tag.setAlbum(value);
                            break;
                        case "albumArtist":
                            tag.setAlbumArtist(value);
                            break;
                        case "genre":
                            tag.setGenre(value);
                            break;
                        case "grouping":
                            tag.setGrouping(value);
                            break;
                        case "mediaQuality":
                            tag.setMediaQuality(value);
                            break;
                    }
                }
            }

            // Save the updated tag
            MusixMateApp.getInstance().getOrmLite().save(tag);

            // Return success response
            response.setStatus(HttpStatus.OK_200);
            Content.Sink.write(response, true, "{\"success\":true,\"message\":\"Music updated successfully\"}", callback);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error updating music: " + e.getMessage(), e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            Content.Sink.write(response, true, "{\"error\":\"" + e.getMessage() + "\"}", callback);
            return true;
        }
    }

    // Add this method to your MusicApiHandler class

    private boolean handleMoveMusic(String musicId, String requestBody, Response response, Callback callback) throws Exception {
        try {
            // Parse the music ID
            long id = Long.parseLong(musicId);

            // Get the music tag from the repository
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(id);

            if (tag == null) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                Content.Sink.write(response, true, "{\"error\":\"Music not found\"}", callback);
                return true;
            }

            // Parse the JSON request body to get the target directory
            String targetDirectory = null;
            String[] pairs = requestBody.substring(1, requestBody.length() - 1).split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");

                    if (key.equals("targetDirectory")) {
                        targetDirectory = value;
                        break;
                    }
                }
            }

            if (targetDirectory == null || targetDirectory.isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                Content.Sink.write(response, true, "{\"error\":\"Target directory not specified\"}", callback);
                return true;
            }

            // Get the current file
            File currentFile = new File(tag.getPath());
            if (!currentFile.exists()) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                Content.Sink.write(response, true, "{\"error\":\"Source file not found\"}", callback);
                return true;
            }

            // Determine the target directory path based on the selected option
            String targetPath;
            switch (targetDirectory) {
                case "music":
                    targetPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
                    break;
                case "downloads":
                    targetPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                    break;
                default:
                    // You can add more directory options or handle custom paths
                    response.setStatus(HttpStatus.BAD_REQUEST_400);
                    Content.Sink.write(response, true, "{\"error\":\"Invalid target directory\"}", callback);
                    return true;
            }

            // Create the target directory if it doesn't exist
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            // Create the target file
            File targetFile = new File(targetDir, currentFile.getName());

            // Move the file
            boolean success = currentFile.renameTo(targetFile);

            if (success) {
                // Update the path in the database
                tag.setPath(targetFile.getAbsolutePath());
                MusixMateApp.getInstance().getOrmLite().save(tag);

                // Return success response with the new path
                response.setStatus(HttpStatus.OK_200);
                Content.Sink.write(response, true,
                        "{\"success\":true,\"message\":\"File moved successfully\",\"newPath\":\"" +
                                escapeJson(targetFile.getAbsolutePath()) + "\"}", callback);
            } else {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                Content.Sink.write(response, true, "{\"error\":\"Failed to move file\"}", callback);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error moving music file: " + e.getMessage(), e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            Content.Sink.write(response, true, "{\"error\":\"" + e.getMessage() + "\"}", callback);
            return true;
        }
    }
}
