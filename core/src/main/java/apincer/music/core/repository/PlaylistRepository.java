package apincer.music.core.repository;

import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.utils.StringUtils.trim;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.music.core.database.MusicTag;
import apincer.music.core.model.PlaylistCollection;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.utils.ApplicationUtils;

public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    private static List<PlaylistEntry> playlists = new ArrayList<>();

    public static final String TYPE_SONG = "song";
    //public static final String TYPE_TITLE_ARTIST = "title_artist";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_GENRE = "genre";
    //public static final String TYPE_GROUPING = "grouping";
    public static final String TYPE_RATING = "rating";

    public static void loadPlaylists(Context context) {
        if (playlists.isEmpty()) { // Ensure it's loaded only once
            ObjectMapper mapper = new ObjectMapper();
            // Assuming playlists.json is directly under assets
            InputStream in = ApplicationUtils.getAssetsAsStream(context, "playlists.json");

            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    PlaylistCollection collection = mapper.readValue(reader, PlaylistCollection.class);
                    if (collection != null && collection.getPlaylists() != null) {
                        playlists = collection.getPlaylists();
                        Log.d(TAG, "Loaded " + playlists.size() + " playlist entries from JSON.");
                    } else {
                        Log.e(TAG, "Failed to parse playlists.json or it's empty.");
                        playlists = Collections.emptyList(); // Ensure it's not null
                    }
                    populatePlaylistMap(playlists);
                } catch (IOException e) { // Catch parsing errors too
                    Log.e(TAG, "Error reading or parsing playlists.json", e);
                    playlists = Collections.emptyList();
                }
            } else {
                Log.e(TAG, "Could not find playlists.json in assets");
                playlists = Collections.emptyList();
            }
        }
    }

    private static void populatePlaylistMap(List<PlaylistEntry> allPlaylists) {
        //todo for each entry, add to it's mappedSong with key based on type
        // song - key is title|artist|album
        // album - key is album|artist
        // genre - key is genre
        if (allPlaylists == null) {
            return;
        }
        for (PlaylistEntry entry : allPlaylists) {
            if (entry == null) {
                continue;
            }

            String type = entry.getType() == null ? "" : entry.getType().toLowerCase();

            switch (type) {
                case TYPE_SONG:
                    if (entry.getSongs() != null) {
                        for (MusicTag song : entry.getSongs()) {
                            if (song != null && song.getTitle() != null && song.getArtist() != null) {
                                String key = getKeyForSong(song.getTitle(), song.getArtist(), song.getAlbum());
                                entry.getMappedSongs().put(key, song);
                            }
                        }
                    }
                    break;

                case TYPE_ALBUM:
                    if (entry.getSongs() != null) {
                        for (MusicTag song : entry.getSongs()) {
                            if (song != null && song.getAlbum() != null && song.getArtist() != null) {
                                String key = getKeyForAlbum(song);
                                entry.getMappedSongs().put(key, song);
                            }
                        }
                    }
                    break;

                case TYPE_GENRE:
                    if (entry.getSongs() != null) {
                        for (MusicTag song : entry.getSongs()) {
                            if (song != null && song.getGenre() != null ) {
                                String genre = getKeyForGenre(song);
                                entry.getMappedSongs().put(genre, song);
                            }
                        }
                    }
                    break;

                case TYPE_RATING:
                    if (entry.getSongs() != null) {
                        for (MusicTag song : entry.getSongs()) {
                            if (song != null && song.getQualityRating() != null) {
                                String grouping = getKeyForRating(song);
                                entry.getMappedSongs().put(grouping, song);
                            }
                        }
                    }
                    break;

                default:
                   // Log.w(TAG, "populatePlaylistMaps: Unhandled playlist type: " + entry.getType() + " for playlist UUID: " + playlistUuid);
                    break;
            }
        }
    }

    @NonNull
    private static String getKeyForGenre(MediaTrack song) {
        return trimToEmpty(song.getGenre()).toLowerCase();
    }

    @NonNull
    private static String getKeyForAlbum(MediaTrack song) {
        String albumTitle = trimString(song.getAlbum()).toLowerCase();
        String artist = trimString(song.getArtist()).toLowerCase();
        return albumTitle + "|" + artist;
    }

    @NonNull
    private static String getKeyForSong(String title, String artist, String album) {
        title = trimString(title).toLowerCase();
        artist = trimString(artist).toLowerCase();
        album = trim(album, "*").toLowerCase();
        return title + "|" + artist + "|" + album;
    }

    private static String trimString(String title) {
        title = trimToEmpty(title).toLowerCase();
        title = title.replaceAll(" {2}", " ");

        return title;
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

    public static boolean isSongInPlaylistName(MusicTag tagToCheck, String name) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(name);
        if (playlistOpt.isEmpty()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getSongs() == null) {
            return false;
        }
        return isSongInPlaylist(tagToCheck, entry);
    }

    public static boolean isSongInPlaylist(MusicTag tagToCheck, String playlistUuid) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByUuid(playlistUuid);
        if (playlistOpt.isEmpty()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getSongs() == null) {
            return false;
        }
        return isSongInPlaylist(tagToCheck, entry);
    }

    private static boolean isSongInPlaylist(MusicTag tagToCheck, PlaylistEntry entry) {
        List<String> keys = getKeyForPlaylist(entry, tagToCheck);
        for(String key: keys) {
             if(entry.getMappedSongs().containsKey(key)) {
                 return true;
             }
        }
        return false;
    }

    private static List<String> getKeyForPlaylist(PlaylistEntry entry, MusicTag tagToCheck) {
        List<String> keys = new ArrayList<>();
        switch (entry.getType()) {
            case TYPE_SONG -> {
                keys.add(getKeyForSong(tagToCheck.getTitle(), tagToCheck.getArtist(), tagToCheck.getAlbum()));
                keys.add(getKeyForSong(tagToCheck.getTitle(), tagToCheck.getArtist(), "*"));
            }
           // case TYPE_TITLE_ARTIST -> getKeyForTitleArtist(tagToCheck);
            case TYPE_ALBUM -> keys.add(getKeyForAlbum(tagToCheck));
            case TYPE_GENRE -> keys.add(getKeyForGenre(tagToCheck));
           // case TYPE_GROUPING -> getKeyForGrouping(tagToCheck);
            case TYPE_RATING -> keys.add(getKeyForRating(tagToCheck));
           // default -> "";
        }
        return keys;
    }

    private static String getKeyForRating(@UnknownNullability MediaTrack song) {
        return trimToEmpty(song.getQualityRating()).toLowerCase();
    }

    public static PlaylistEntry getPlaylistByName(String playlistName) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        return playlistOpt.orElse(null);
    }

    public static boolean isInPlaylist(@NotNull MusicTag tag) {
        List<String> focusTypes = Arrays.asList("song", "album", "title_artist");

        for(PlaylistEntry entry: playlists) {
            if(focusTypes.contains(entry.getType())) {
                if (isSongInPlaylistName(tag, entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds songs defined in a "song" type playlist that are missing from the user's library.
     //* @param playlistName UUID of the "song" type playlist.
     //* @param existingMusicTagsInLibrary List of MusicTags representing the user's current music library.
     * @return List of MusicTags for songs defined in the playlist but not found in the library.
     */
    public static List<MusicTag> getMissingSongs(String playlistName, List<MusicTag> existingMusicTagsInLibrary) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        if (playlistOpt.isEmpty() || !TYPE_SONG.equalsIgnoreCase(playlistOpt.get().getType()) || playlistOpt.get().getSongs() == null) {
            return Collections.emptyList();
        }

        List<MusicTag> songsInJsonPlaylist = new ArrayList<>(playlistOpt.get().getSongs());
        List<MusicTag> missingTags = new ArrayList<>();
        AtomicInteger pseudoIdCounter = new AtomicInteger(2999000);

        Map<String, String> foundMapped = new HashMap<>();
        for(MusicTag song: existingMusicTagsInLibrary) {
            String key = getKeyForSong(song.getTitle(), song.getArtist(), song.getAlbum());
            foundMapped.put(key, key);
            key = getKeyForSong(song.getTitle(), song.getArtist(), "*");
            foundMapped.put(key, key);
        }

        for (MediaTrack songFromPlaylist : songsInJsonPlaylist) {
            if (songFromPlaylist == null) continue;

            String key = getKeyForSong(songFromPlaylist.getTitle(), songFromPlaylist.getArtist(), songFromPlaylist.getAlbum());
            if(!foundMapped.containsKey(key)) {
                MusicTag missingTag = new MusicTag(); //songFromPlaylist;
                missingTag.setTitle(songFromPlaylist.getTitle());
                missingTag.setArtist(songFromPlaylist.getArtist());
                missingTag.setAlbum(songFromPlaylist.getAlbum());
                missingTag.setMusicManaged(true); // prevent new label
                missingTag.setFileType("None");
                missingTag.setAudioEncoding("None");
                missingTag.setPath("Missing");
                missingTag.setId(pseudoIdCounter.getAndIncrement());
                missingTag.setUniqueKey(String.valueOf(missingTag.getId()));
                missingTag.setMusicManaged(true);
                missingTag.setQualityInd("--");
                missingTag.setAlbumArtFilename(DEFAULT_COVERART);
                missingTags.add(missingTag);
            }
        }
       // missingTags.sort((o1, o2) -> o1.getTitle().compareTo(o2.getTitle()));

        missingTags.sort(Comparator
                .comparing(MediaTrack::getArtist, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MediaTrack::getTitle, String.CASE_INSENSITIVE_ORDER));
        return missingTags;
    }
}
