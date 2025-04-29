package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.utils.ApplicationUtils;

public class PlaylistRepository {
    private static final Map<String, Map<String,String>> albumPlaylists = new HashMap<>();
    private static final Map<String, Map<String,String>> titlePlaylists = new HashMap<>();

    public static void initPlaylist(Context context) {
        if(albumPlaylists.isEmpty()) {
            // load from resources
            InputStream in = ApplicationUtils.getAssetsAsStream(context, "playlist_by_albums.txt");

            // while each line, not start with # and not empty
            // put to top50Audiophile with "playlist|seq|album|artist|year" as key
            if (in != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;

                    // while each line, not start with # and not empty
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            // Parse the line
                            String[] parts = line.split("\\|");
                            if (parts.length >= 5) {
                                String playlist = parts[0].trim();
                                String seq = parts[1].trim();
                                String album = parts[2].trim();
                                String artist = parts[3].trim();
                                String year = trimToEmpty(parts[4]);
                                // String description = trimToEmpty(parts[4]);

                                // Create the key in format "album|artist|year"
                                String key = album + "|" + artist + "|" + year;
                                if(albumPlaylists.containsKey(playlist)) {
                                    albumPlaylists.get(playlist).put(key,seq);
                                }else {
                                    Map<String,String> list = new HashMap<>();
                                    list.put(key, seq);
                                    albumPlaylists.put(playlist, list);
                                }

                                // Put to top50Audiophile with "album|artist|year" as key
                                // playlist.put(key, description);
                            }
                        }
                    }
                    reader.close();
                    Log.d("PlaylistManager", "Loaded " + albumPlaylists.size() + " albums playlist");
                } catch (IOException e) {
                    Log.e("PlaylistManager", "Error reading playlist file", e);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Log.e("PlaylistManager", "Error closing input stream", e);
                    }
                }
            } else {
                Log.e("PlaylistManager", "Could not find playlist_by_albums.txt in assets");
            }
        }

        if(titlePlaylists.isEmpty()) {
            // load from resources
            InputStream in = ApplicationUtils.getAssetsAsStream(context, "playlist_by_titles.txt");

            // while each line, not start with # and not empty
            // put to top50Audiophile with "playlist|seq|album|artist|year" as key
            if (in != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;

                    // while each line, not start with # and not empty
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            // Parse the line
                            String[] parts = line.split("\\|");
                            if (parts.length >= 5) {
                                String playlist = trimToEmpty(parts[0]);
                                String title = trimToEmpty(parts[1]);
                                String artist = trimToEmpty(parts[2]);
                                String album = trimToEmpty(parts[3]);
                                String notes = trimToEmpty(parts[4]);

                                // Create the key in format "album|artist|year"
                                String key = title+"|"+album + "|" + artist;
                                if(titlePlaylists.containsKey(playlist)) {
                                    titlePlaylists.get(playlist).put(key,notes);
                                }else {
                                    Map<String,String> list = new HashMap<>();
                                    list.put(key, notes);
                                    titlePlaylists.put(playlist, list);
                                }
                            }
                        }
                    }
                    reader.close();
                    Log.d("PlaylistManager", "Loaded " + titlePlaylists.size() + " albums playlist");
                } catch (IOException e) {
                    Log.e("PlaylistManager", "Error reading playlist file", e);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Log.e("PlaylistManager", "Error closing input stream", e);
                    }
                }
            } else {
                Log.e("PlaylistManager", "Could not find playlist_by_albums.txt in assets");
            }
        }
    }

    public static boolean isInAlbumPlaylist(MusicTag tag, String playlistName) {
        if (tag == null || !albumPlaylists.containsKey(playlistName)) {
            return false;
        }

        // Extract relevant information from the tag
        String album = tag.getAlbum();
        String artist = tag.getArtist();
        String year = tag.getYear();

        // Check if any of the required fields are missing
        if (isEmpty(album) || isEmpty(artist)) {
            return false;
        }

        // Normalize the data (optional but recommended)
        album = album.trim();
        artist = artist.trim();

        // Create the key in the format "album|artist|year"
        String key = album + "|" + artist + "|" + year;

        // Check if the key exists in the top50Audiophile map
        boolean isInTop50 = albumPlaylists.get(playlistName).containsKey(key);

        // If not found, try a less strict match (without year)
        if (!isInTop50 && !isEmpty(year)) {
            // Try matching without the year
            String keyWithoutYear = album + "|" + artist + "|";

            // Check for any key that starts with this prefix
            for (String existingKey : albumPlaylists.get(playlistName).keySet()) {
                if (existingKey.startsWith(keyWithoutYear)) {
                    isInTop50 = true;
                    break;
                }
            }
        }

        // Return the result
        return isInTop50;
    }

    public static List<String> getPlaylistNames() {
        List<String> list = new ArrayList<>(albumPlaylists.keySet());
        list.addAll(titlePlaylists.keySet());
        return list;
    }

    public static boolean isInTitlePlaylist(MusicTag tag, String playlistName) {
        if (tag == null || !titlePlaylists.containsKey(playlistName)) {
            return false;
        }

        // Extract relevant information from the tag
        String album = tag.getAlbum();
        String artist = tag.getArtist();
        String title = tag.getTitle();

        // Check if any of the required fields are missing
        if (isEmpty(album) || isEmpty(artist) || isEmpty(title)) {
            return false;
        }

        // Normalize the data (optional but recommended)
        album = album.trim();
        artist = artist.trim();

        // Create the key in the format "album|artist|year"
        String key = title+"|"+album + "|" + artist;

        // Check if the key exists in the top50Audiophile map
        return titlePlaylists.get(playlistName).containsKey(key);
    }
}
