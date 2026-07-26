package apincer.music.core.repository;

import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.Constants.PATH_MISSING_TRACK;
import static apincer.music.core.model.PlaylistEntry.TYPE_TITLE;
import static apincer.music.core.model.PlaylistEntry.songKey;

import android.content.Context;
import android.util.Log;



import apincer.music.core.model.ExcludeRule;
import apincer.music.core.model.PlaylistCollection;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.PlaylistRule;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.PlaylistCollection;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.PlaylistRule;
import apincer.music.core.model.Track;
import apincer.music.core.utils.ApplicationUtils;

public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    private static List<PlaylistEntry> playlists = new ArrayList<>();

    public static synchronized void loadPlaylists(Context context) {
        if (!playlists.isEmpty()) return; // Early exit

        // Ensure it's loaded only once
        InputStream in = ApplicationUtils.getAssetsAsStream(context, "playlists.json");

        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                PlaylistCollection collection = new PlaylistCollection();
                org.json.JSONArray playlistsArray = root.optJSONArray("playlists");
                if (playlistsArray != null) {
                    List<PlaylistEntry> entryList = new ArrayList<>();
                    for (int i = 0; i < playlistsArray.length(); i++) {
                        org.json.JSONObject entryObj = playlistsArray.getJSONObject(i);
                        PlaylistEntry entry = new PlaylistEntry();
                        entry.setName(entryObj.optString("name", null));
                        entry.setUuid(entryObj.optString("uuid", null));
                        entry.setType(entryObj.optString("type", PlaylistEntry.TYPE_TITLE));
                        entry.setNote(entryObj.optString("note", null));
                        entry.setDescription(entryObj.optString("description", null));
                        
                        org.json.JSONArray rulesArray = entryObj.optJSONArray("rules");
                        if (rulesArray != null) {
                            List<PlaylistRule> ruleList = new ArrayList<>();
                            for (int j = 0; j < rulesArray.length(); j++) {
                                org.json.JSONObject ruleObj = rulesArray.getJSONObject(j);
                                PlaylistRule rule = new PlaylistRule();
                                rule.setTitle(ruleObj.optString("title", null));
                                rule.setArtist(ruleObj.optString("artist", null));
                                rule.setAlbum(ruleObj.optString("album", null));
                                rule.setNotes(ruleObj.optString("notes", null));
                                
                                rule.setGenre(parseStringOrList(ruleObj, "genre"));
                                rule.setStyle(parseStringOrList(ruleObj, "style"));
                                rule.setMood(parseStringOrList(ruleObj, "mood"));
                                
                                if (ruleObj.has("exclude")) {
                                    org.json.JSONObject excObj = ruleObj.optJSONObject("exclude");
                                    if (excObj != null) {
                                        ExcludeRule exc = new ExcludeRule();
                                        exc.setMood(parseStringOrList(excObj, "mood"));
                                        exc.setStyle(parseStringOrList(excObj, "style"));
                                        rule.setExclude(exc);
                                    }
                                }
                                ruleList.add(rule);
                            }
                            entry.setRules(ruleList);
                        }
                        entryList.add(entry);
                    }
                    collection.setPlaylists(entryList);
                }

                if (collection.getPlaylists() != null) {
                    collection.compileRules();
                    playlists = collection.getPlaylists();
                    Log.d(TAG, "Loaded " + playlists.size() + " playlist entries from JSON.");
                } else {
                    Log.e(TAG, "Failed to parse playlists.json or it's empty.");
                    playlists = Collections.emptyList(); // Ensure it's not null
                }
                //populatePlaylistMap(playlists);
            } catch (Exception e) { // Catch parsing errors too
                Log.e(TAG, "Error reading or parsing playlists.json", e);
                playlists = Collections.emptyList();
            }
        } else {
            Log.e(TAG, "Could not find playlists.json in assets");
            playlists = Collections.emptyList();
        }
    }

    private static List<String> parseStringOrList(org.json.JSONObject obj, String key) {
        if (!obj.has(key)) return null;
        org.json.JSONArray arr = obj.optJSONArray(key);
        if (arr != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i));
            }
            return list;
        }
        String str = obj.optString(key, null);
        if (str != null) {
            return Collections.singletonList(str);
        }
        return null;
    }

    private static Optional<PlaylistEntry> findPlaylistByName(String playlistName) {
        if (playlistName == null || playlists == null) {
            return Optional.empty();
        }
        return playlists.stream()
                .filter(p -> p != null && playlistName.equalsIgnoreCase(p.getName()))
                .findFirst();
    }

    private static Optional<PlaylistEntry> findPlaylistByUuid(String uuid) {
        if (uuid == null || playlists == null) {
            return Optional.empty();
        }
        return playlists.stream()
                .filter(p -> p != null && uuid.equalsIgnoreCase(p.getUuid()))
                .findFirst();
    }

    public static List<PlaylistEntry> getPlaylists() {
        return playlists;
    }

    public static void exportPlaylists(File playlistDir, apincer.music.core.repository.TagRepository tagRepos) {
        if (!playlistDir.exists()) {
            playlistDir.mkdirs();
        }

        // 1. Group songs per playlist
        Map<PlaylistEntry, List<Track>> playlistMap = new HashMap<>();

        for (PlaylistEntry entry : playlists) {
            playlistMap.put(entry, new ArrayList<>());
        }

        tagRepos.processAllMusics(song -> {
            if (song != null) {
                for (PlaylistEntry entry : playlists) {
                    if (entry.isInPlaylist(song)) {
                        playlistMap.get(entry).add(song);
                    }
                }
            }
        });

        // 2. Write each playlist to M3U
        for (Map.Entry<PlaylistEntry, List<Track>> e : playlistMap.entrySet()) {
            PlaylistEntry entry = e.getKey();
            List<Track> tracks = e.getValue();

            if (tracks.isEmpty()) continue;

            File m3uFile = new File(playlistDir, sanitizeFileName(entry.getName()) + ".m3u");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(m3uFile))) {

                writer.write("#EXTM3U\n");

                for (Track track : tracks) {
                    if (track == null) continue;

                    String title = safe(track.getTitle());
                    String artist = safe(track.getArtist());
                    String path = safe(track.getPath());

                    // Duration unknown → use -1
                    writer.write("#EXTINF:-1," + artist + " - " + title + "\n");
                    writer.write(path + "\n");
                }

            } catch (IOException ex) {
                Log.e(TAG, "Error writing playlist: " + entry.getName(), ex);
            }
        }
    }
    private static String sanitizeFileName(String name) {
        if (name == null) return "playlist";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static boolean isSongInPlaylistName(Track track, String name) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(name);
        if (playlistOpt.isEmpty()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getRules() == null) {
            return false;
        }
        return entry.isInPlaylist(track);
    }

    public static boolean isSongInPlaylistUuid(Track track, String playlistUuid) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByUuid(playlistUuid);
        if (playlistOpt.isEmpty()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getRules() == null) {
            return false;
        }
        return entry.isInPlaylist(track);
    }

    public static PlaylistEntry getPlaylistByName(String playlistName) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        return playlistOpt.orElse(null);
    }

    public static boolean isInPlaylist(@NotNull Track tag) {
        for(PlaylistEntry entry: playlists) {
            return entry.isInTitlePlaylist(tag);
        }
        return false;
    }

    /**
     * Finds songs defined in a "song" type playlist that are missing from the user's library.
     //* @param playlistName UUID of the "song" type playlist.
     //* @param existingMusicTagsInLibrary List of MusicTags representing the user's current music library.
     * @return List of MusicTags for songs defined in the playlist but not found in the library.
     */
    public static List<Track> getMissingSongs(String playlistName, List<Track> existingMusicTagsInLibrary) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        if (playlistOpt.isEmpty() || !TYPE_TITLE.equalsIgnoreCase(playlistOpt.get().getType()) || playlistOpt.get().getRules() == null) {
            return Collections.emptyList();
        }

        List<PlaylistRule> playlistRules = new ArrayList<>(playlistOpt.get().getRules());
        List<Track> missingTags = new ArrayList<>();
        AtomicInteger pseudoIdCounter = new AtomicInteger(2999000);

        Set<Long> libraryIndex = new HashSet<>();
        for (Track song : existingMusicTagsInLibrary) {
            libraryIndex.add(songKey(song.getTitle(), song.getArtist()));
        }

        for (PlaylistRule rule : playlistRules) {
            long key = songKey(rule.getTitle(), rule.getArtist());

            if (!libraryIndex.contains(key)) {
                // missing song
                AudioTag missingTag = new AudioTag(pseudoIdCounter.getAndIncrement()); //songFromPlaylist;
                missingTag.setTitle(rule.getTitle());
                missingTag.setArtist(rule.getArtist());
                missingTag.setAlbum(rule.getAlbum());
                missingTag.setIsManaged(true); // prevent new label
                missingTag.setFileType("None");
                missingTag.setAudioEncoding("None");
                missingTag.setPath(PATH_MISSING_TRACK);
                missingTag.setUniqueKey(String.valueOf(missingTag.getId()));
                missingTag.setQualityInd("--");
                missingTag.setAlbumArtFilename(DEFAULT_COVERART);
                missingTags.add(missingTag);
            }
        }

        // missingTags.sort((o1, o2) -> o1.getTitle().compareTo(o2.getTitle()));

        missingTags.sort(Comparator
                .comparing(Track::getArtist, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Track::getTitle, String.CASE_INSENSITIVE_ORDER));
        return missingTags;
    }

}
