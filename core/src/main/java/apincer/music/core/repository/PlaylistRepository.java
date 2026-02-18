package apincer.music.core.repository;

import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import apincer.music.core.database.MusicTag;
import apincer.music.core.model.PlaylistCollection;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.utils.ApplicationUtils;

public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    private static List<PlaylistEntry> allPlaylists = new ArrayList<>();

    public static final String TYPE_SONG = "song";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_GENRE = "genre";
    public static final String TYPE_GROUPING = "grouping";
    public static final String TYPE_RATING = "rating";

    public static void initPlaylist(Context context) {
        if (allPlaylists.isEmpty()) { // Ensure it's loaded only once
            ObjectMapper mapper = new ObjectMapper();
            // Assuming playlists.json is directly under assets
            InputStream in = ApplicationUtils.getAssetsAsStream(context, "playlists.json");

            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    PlaylistCollection collection = mapper.readValue(reader, PlaylistCollection.class);
                    if (collection != null && collection.getPlaylists() != null) {
                        allPlaylists = collection.getPlaylists();
                        Log.d(TAG, "Loaded " + allPlaylists.size() + " playlist entries from JSON.");
                    } else {
                        Log.e(TAG, "Failed to parse playlists.json or it's empty.");
                        allPlaylists = Collections.emptyList(); // Ensure it's not null
                    }
                    populatePlaylistMap(allPlaylists);
                } catch (IOException e) { // Catch parsing errors too
                    Log.e(TAG, "Error reading or parsing playlists.json", e);
                    allPlaylists = Collections.emptyList();
                }
            } else {
                Log.e(TAG, "Could not find playlists.json in assets");
                allPlaylists = Collections.emptyList();
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
                                String key = getKeyForSong(song);
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

    @Deprecated
    private static String getKeyForRating(MediaTrack song) {
        return trimToEmpty(song.getQualityRating()).toLowerCase();
    }

    @Deprecated
    @NonNull
    private static String getKeyForGenre(MediaTrack song) {
        return trimToEmpty(song.getGenre()).toLowerCase();
    }

    @Deprecated
    @NonNull
    private static String getKeyForAlbum(MediaTrack song) {
        String albumTitle = trimToEmpty(song.getAlbum()).toLowerCase();
        String artist = trimToEmpty(song.getArtist()).toLowerCase();
        return albumTitle + "|" + artist;
    }

    @Deprecated
    @NonNull
    private static String getKeyForSong(MediaTrack song) {
        String title = trimToEmpty(song.getTitle()).toLowerCase();
        String artist = trimToEmpty(song.getArtist()).toLowerCase();
        // Album can be optional or empty for songs in some contexts
        String album = trimToEmpty(song.getAlbum()).toLowerCase();
        return title + "|" + artist + "|" + album;
    }

    @NonNull
    private static String getKeyForGenre(MusicTag song) {
        return trimToEmpty(song.getGenre()).toLowerCase();
    }

    @NonNull
    private static String getKeyForAlbum(MusicTag song) {
        String albumTitle = trimToEmpty(song.getAlbum()).toLowerCase();
        String artist = trimToEmpty(song.getArtist()).toLowerCase();
        return albumTitle + "|" + artist;
    }

    @NonNull
    private static String getKeyForSong(MusicTag song) {
        String title = trimToEmpty(song.getTitle()).toLowerCase();
        String artist = trimToEmpty(song.getArtist()).toLowerCase();
        String album = trimToEmpty(song.getAlbum()).toLowerCase();
        return title + "|" + artist + "|" + album;
    }

    private static Optional<PlaylistEntry> findPlaylistByName(String playlistName) {
        if (playlistName == null || allPlaylists == null) {
            return Optional.empty();
        }
        return allPlaylists.stream()
                .filter(p -> p != null && playlistName.equalsIgnoreCase(p.getName()))
                .findFirst();
    }

    private static Optional<PlaylistEntry> findPlaylistByUuid(String uuid) {
        if (uuid == null || allPlaylists == null) {
            return Optional.empty();
        }
        return allPlaylists.stream()
                .filter(p -> p != null && uuid.equalsIgnoreCase(p.getUuid()))
                .findFirst();
    }

    public static List<String> getPlaylistNames() {
        if (allPlaylists == null) {
            return Collections.emptyList();
        }
        return allPlaylists.stream()
                .filter(Objects::nonNull)
                .map(PlaylistEntry::getName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct() // Ensure names are unique if multiple entries could somehow have same name
                .sorted()
                .collect(Collectors.toList());
    }

    public static List<PlaylistEntry> getPlaylists() {
        return allPlaylists;
    }

    /**
     * Finds songs defined in a "song" type playlist that are missing from the user's library.
     *
     //* @param playlistName UUID of the "song" type playlist.
     //* @param existingMusicTagsInLibrary List of MusicTags representing the user's current music library.
     * @return List of MusicTags for songs defined in the playlist but not found in the library.
     */
    /*
    public static List<MusicTag> getMissingSongsForPlaylist(String playlistName, List<MusicTag> existingMusicTagsInLibrary) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        if (!playlistOpt.isPresent() || !TYPE_SONG.equalsIgnoreCase(playlistOpt.get().getType()) || playlistOpt.get().getSongs() == null) {
            return Collections.emptyList();
        }

        List<MediaTrack> songsInJsonPlaylist = playlistOpt.get().getSongs();
        List<MusicTag> missingTags = new ArrayList<>();
        AtomicInteger pseudoIdCounter = new AtomicInteger(2999000);

        for (MediaTrack songFromPlaylist : songsInJsonPlaylist) {
            if (songFromPlaylist == null) continue;

            boolean foundInLibrary = existingMusicTagsInLibrary.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(libraryTag ->
                            Objects.equals(trimToEmpty(songFromPlaylist.getTitle()).toLowerCase(), trimToEmpty(libraryTag.getTitle()).toLowerCase()) &&
                                    Objects.equals(trimToEmpty(songFromPlaylist.getArtist()).toLowerCase(), trimToEmpty(libraryTag.getArtist()).toLowerCase()) &&
                                    (isEmpty(songFromPlaylist.getAlbum()) || isEmpty(libraryTag.getAlbum()) || Objects.equals(trimToEmpty(songFromPlaylist.getAlbum()).toLowerCase(), trimToEmpty(libraryTag.getAlbum()).toLowerCase()))
                    );

            if (!foundInLibrary) {
                MusicTag missingTag = songFromPlaylist.toMusicTag();
                missingTag.setId(pseudoIdCounter.getAndIncrement());
                missingTag.setUniqueKey(String.valueOf(missingTag.getId()));
                missingTag.setMusicManaged(true);
                missingTag.setQualityInd("NA");
                missingTags.add(missingTag);
            }
        }
        return missingTags;
    } */

    public static boolean isSongInPlaylistName(MusicTag tagToCheck, String playlistname) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistname);
        if (!playlistOpt.isPresent()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getSongs() == null) {
            return false;
        }
        String key = getKeyForPlaylist(entry, tagToCheck);
        return entry.getMappedSongs().containsKey(key);
    }

    public static boolean isSongInPlaylist(MusicTag tagToCheck, String playlistUuid) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByUuid(playlistUuid);
        if (!playlistOpt.isPresent()) { // || !TYPE_GROUPING.equalsIgnoreCase(playlistOpt.get().getType())) {
            return false;
        }

        PlaylistEntry entry = playlistOpt.get();
        if (entry.getSongs() == null) {
            return false;
        }

        String key = getKeyForPlaylist(entry, tagToCheck);
        return entry.getMappedSongs().containsKey(key);
    }

    private static String getKeyForPlaylist(PlaylistEntry entry, MusicTag tagToCheck) {
        return switch (entry.getType()) {
            case TYPE_SONG -> getKeyForSong(tagToCheck);
            case TYPE_ALBUM -> getKeyForAlbum(tagToCheck);
            case TYPE_GENRE -> getKeyForGenre(tagToCheck);
           // case TYPE_GROUPING -> getKeyForGrouping(tagToCheck);
            case TYPE_RATING -> getKeyForRating(tagToCheck);
            default -> "";
        };
    }

    private static String getKeyForRating(MusicTag song) {
        return trimToEmpty(song.getQualityRating()).toLowerCase();
    }

    public static boolean isTitlePlaylist(String playlistName) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        return playlistOpt.isPresent() && TYPE_SONG.equalsIgnoreCase(playlistOpt.get().getType());
    }

    public static PlaylistEntry getPlaylistByName(String playlistName) {
        Optional<PlaylistEntry> playlistOpt = findPlaylistByName(playlistName);
        return playlistOpt.orElse(null);
    }
}
