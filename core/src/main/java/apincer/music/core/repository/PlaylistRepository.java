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

                        // Smart playlist telemetry criteria
                        entry.setMinDrScore(entryObj.optDouble("minDrScore", 0.0));
                        entry.setHiresOnly(entryObj.optBoolean("hiresOnly", false));
                        entry.setDsdOnly(entryObj.optBoolean("dsdOnly", false));
                        entry.setLosslessOnly(entryObj.optBoolean("losslessOnly", false));
                        entry.setMinBitDepth(entryObj.optInt("minBitDepth", 0));
                        entry.setMinSampleRate(entryObj.optLong("minSampleRate", 0L));
                        
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
                    registerBuiltInSmartPlaylists(entryList);
                    collection.setPlaylists(entryList);
                } else {
                    List<PlaylistEntry> entryList = new ArrayList<>();
                    registerBuiltInSmartPlaylists(entryList);
                    collection.setPlaylists(entryList);
                }

                if (collection.getPlaylists() != null) {
                    collection.compileRules();
                    playlists = collection.getPlaylists();
                    Log.d(TAG, "Loaded " + playlists.size() + " playlist entries from JSON.");
                } else {
                    List<PlaylistEntry> fallback = new ArrayList<>();
                    registerBuiltInSmartPlaylists(fallback);
                    collection.setPlaylists(fallback);
                    collection.compileRules();
                    playlists = fallback;
                }
                //populatePlaylistMap(playlists);
            } catch (Exception e) { // Catch parsing errors too
                Log.e(TAG, "Error reading or parsing playlists.json", e);
                List<PlaylistEntry> fallback = new ArrayList<>();
                registerBuiltInSmartPlaylists(fallback);
                playlists = fallback;
            }
        } else {
            Log.e(TAG, "Could not find playlists.json in assets");
            List<PlaylistEntry> fallback = new ArrayList<>();
            registerBuiltInSmartPlaylists(fallback);
            playlists = fallback;
        }
        loadCustomPlaylistsFromDisk(context, playlists);
    }

    public static synchronized void saveCustomPlaylist(Context context, PlaylistEntry entry) {
        if (entry == null || context == null) return;
        if (entry.getUuid() == null || entry.getUuid().isEmpty()) {
            entry.setUuid("custom-" + java.util.UUID.randomUUID().toString());
        }
        entry.setType(PlaylistEntry.TYPE_SMART);
        boolean found = false;
        for (int i = 0; i < playlists.size(); i++) {
            if (entry.getUuid().equalsIgnoreCase(playlists.get(i).getUuid())) {
                playlists.set(i, entry);
                found = true;
                break;
            }
        }
        if (!found) {
            playlists.add(entry);
        }
        writeCustomPlaylistsToDisk(context);
    }

    public static synchronized void deleteCustomPlaylist(Context context, String uuid) {
        if (uuid == null || context == null) return;
        playlists.removeIf(p -> uuid.equalsIgnoreCase(p.getUuid()));
        writeCustomPlaylistsToDisk(context);
    }

    private static void writeCustomPlaylistsToDisk(Context context) {
        try {
            File file = new File(context.getFilesDir(), "custom_playlists.json");
            org.json.JSONArray arr = new org.json.JSONArray();
            for (PlaylistEntry p : playlists) {
                if (PlaylistEntry.TYPE_SMART.equalsIgnoreCase(p.getType()) &&
                    !UUID_SMART_DR12.equalsIgnoreCase(p.getUuid()) &&
                    !UUID_SMART_HIRES.equalsIgnoreCase(p.getUuid()) &&
                    !UUID_SMART_DSD.equalsIgnoreCase(p.getUuid()) &&
                    !UUID_SMART_LOSSLESS.equalsIgnoreCase(p.getUuid())) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("name", p.getName());
                    obj.put("uuid", p.getUuid());
                    obj.put("type", p.getType());
                    obj.put("description", p.getDescription());
                    obj.put("note", p.getNote());
                    obj.put("minDrScore", p.getMinDrScore());
                    obj.put("hiresOnly", p.isHiresOnly());
                    obj.put("dsdOnly", p.isDsdOnly());
                    obj.put("losslessOnly", p.isLosslessOnly());
                    obj.put("minBitDepth", p.getMinBitDepth());
                    obj.put("minSampleRate", p.getMinSampleRate());
                    arr.put(obj);
                }
            }
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(arr.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing custom_playlists.json", e);
        }
    }

    private static void loadCustomPlaylistsFromDisk(Context context, List<PlaylistEntry> entryList) {
        if (context == null || entryList == null) return;
        try {
            File file = new File(context.getFilesDir(), "custom_playlists.json");
            if (!file.exists()) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject entryObj = arr.getJSONObject(i);
                PlaylistEntry entry = new PlaylistEntry();
                entry.setName(entryObj.optString("name", null));
                entry.setUuid(entryObj.optString("uuid", null));
                entry.setType(entryObj.optString("type", PlaylistEntry.TYPE_SMART));
                entry.setDescription(entryObj.optString("description", null));
                entry.setNote(entryObj.optString("note", null));
                entry.setMinDrScore(entryObj.optDouble("minDrScore", 0.0));
                entry.setHiresOnly(entryObj.optBoolean("hiresOnly", false));
                entry.setDsdOnly(entryObj.optBoolean("dsdOnly", false));
                entry.setLosslessOnly(entryObj.optBoolean("losslessOnly", false));
                entry.setMinBitDepth(entryObj.optInt("minBitDepth", 0));
                entry.setMinSampleRate(entryObj.optLong("minSampleRate", 0L));
                if (entryList.stream().noneMatch(p -> entry.getUuid() != null && entry.getUuid().equalsIgnoreCase(p.getUuid()))) {
                    entryList.add(entry);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading custom_playlists.json", e);
        }
    }

    public static final String UUID_SMART_DR12 = "smart-audiophile-sanctuary-dr12";
    public static final String UUID_SMART_HIRES = "smart-studio-masters-hires";
    public static final String UUID_SMART_DSD = "smart-pure-dsd-archive";
    public static final String UUID_SMART_LOSSLESS = "smart-lossless-master-vault";

    public static void registerBuiltInSmartPlaylists(List<PlaylistEntry> entryList) {
        if (entryList == null) return;

        // 1. Audiophile Sanctuary (DR12+)
        if (entryList.stream().noneMatch(p -> UUID_SMART_DR12.equalsIgnoreCase(p.getUuid()) || "Audiophile Sanctuary (DR12+)".equalsIgnoreCase(p.getName()))) {
            PlaylistEntry dr12 = new PlaylistEntry();
            dr12.setName("Audiophile Sanctuary (DR12+)");
            dr12.setUuid(UUID_SMART_DR12);
            dr12.setType(PlaylistEntry.TYPE_SMART);
            dr12.setMinDrScore(12.0);
            dr12.setDescription("High dynamic range uncompressed masterings (DR12 and above).");
            dr12.setNote("DR12+");
            entryList.add(dr12);
        }

        // 2. Studio Masters (Hi-Res)
        if (entryList.stream().noneMatch(p -> UUID_SMART_HIRES.equalsIgnoreCase(p.getUuid()) || "Studio Masters (Hi-Res)".equalsIgnoreCase(p.getName()))) {
            PlaylistEntry hires = new PlaylistEntry();
            hires.setName("Studio Masters (Hi-Res)");
            hires.setUuid(UUID_SMART_HIRES);
            hires.setType(PlaylistEntry.TYPE_SMART);
            hires.setHiresOnly(true);
            hires.setDescription("24-bit studio quality and high sample rate masters (>= 24-bit / 48kHz).");
            hires.setNote("Hi-Res Studio");
            entryList.add(hires);
        }

        // 3. Pure DSD Archive
        if (entryList.stream().noneMatch(p -> UUID_SMART_DSD.equalsIgnoreCase(p.getUuid()) || "Pure DSD Archive".equalsIgnoreCase(p.getName()))) {
            PlaylistEntry dsd = new PlaylistEntry();
            dsd.setName("Pure DSD Archive");
            dsd.setUuid(UUID_SMART_DSD);
            dsd.setType(PlaylistEntry.TYPE_SMART);
            dsd.setDsdOnly(true);
            dsd.setDescription("1-bit Direct Stream Digital recordings (DSD64, DSD128, DSD256).");
            dsd.setNote("DSD Audio");
            entryList.add(dsd);
        }

        // 4. Lossless Master Vault
        if (entryList.stream().noneMatch(p -> UUID_SMART_LOSSLESS.equalsIgnoreCase(p.getUuid()) || "Lossless Master Vault".equalsIgnoreCase(p.getName()))) {
            PlaylistEntry lossless = new PlaylistEntry();
            lossless.setName("Lossless Master Vault");
            lossless.setUuid(UUID_SMART_LOSSLESS);
            lossless.setType(PlaylistEntry.TYPE_SMART);
            lossless.setLosslessOnly(true);
            lossless.setDescription("Bit-perfect lossless CD audio and studio recordings (FLAC, ALAC, WAV, AIFF, DSD).");
            lossless.setNote("Lossless Vault");
            entryList.add(lossless);
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
        if (!PlaylistEntry.TYPE_SMART.equalsIgnoreCase(entry.getType()) && entry.getRules() == null) {
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
        if (!PlaylistEntry.TYPE_SMART.equalsIgnoreCase(entry.getType()) && entry.getRules() == null) {
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
