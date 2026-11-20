package apincer.music.core.playback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import apincer.music.core.codec.FFMpegHelper;
import apincer.music.core.model.Track;
import apincer.music.core.repository.TagRepository;

/**
 * Measures tracks and writes their ReplayGain tags into the files. Album gain goes only to an album
 * whose library tracks are all measured together, so measuring part of an album never rewrites the
 * album gain of files left out.
 */
public final class LoudnessTagger {
    public interface Meter {
        Loudness measure(Track track);
    }

    public interface Writer {
        boolean write(String path, Loudness track, Loudness album);
    }

    /** Every library track of the album {@code track} belongs to; empty when it has no album. */
    public interface AlbumLookup {
        List<Track> tracksOf(Track track);
    }

    public interface Listener {
        default void onMeasuring(Track track) {}
        void onTagged(Track track, boolean written);
    }

    private final Meter meter;
    private final Writer writer;
    private final AlbumLookup albums;

    public LoudnessTagger(Meter meter, Writer writer, AlbumLookup albums) {
        this.meter = meter;
        this.writer = writer;
        this.albums = albums;
    }

    public static LoudnessTagger create(TagRepository tagRepos) {
        return new LoudnessTagger(
                track -> FFMpegHelper.measureLoudness(track.getPath(), track.getAudioDuration()),
                ReplayGainManager.getInstance()::writeReplayGainToFile,
                track -> isBlank(track.getAlbum()) ? List.of()
                        : tagRepos.findByAlbumAndAlbumArtist(track.getAlbum(), track.getAlbumArtist(), 0, 0));
    }

    /** Splits tracks by album and album artist, in their order; a track with no album stands alone. */
    public static Collection<List<Track>> byAlbum(List<Track> tracks) {
        Map<String, List<Track>> groups = new LinkedHashMap<>();
        for (Track track : tracks) {
            String key = isBlank(track.getAlbum()) ? "\u0001" + track.getPath()
                    : track.getAlbum() + "\u0000" + track.getAlbumArtist();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(track);
        }
        return groups.values();
    }

    /**
     * Measures one album's tracks, then writes each measured track, with album gain when every
     * library track of the album measured.
     */
    public void tag(List<Track> album, Listener listener) {
        Loudness[] measured = new Loudness[album.size()];
        List<Loudness> all = new ArrayList<>(album.size());
        for (int i = 0; i < album.size(); i++) {
            Track track = album.get(i);
            listener.onMeasuring(track);
            measured[i] = meter.measure(track);
            if (measured[i] == null) {
                listener.onTagged(track, false);
            } else {
                all.add(measured[i]);
            }
        }
        if (all.isEmpty()) return;
        Loudness albumLoudness = all.size() == album.size() && coversAlbum(album, albums.tracksOf(album.get(0)))
                ? Loudness.album(all) : null;
        for (int i = 0; i < album.size(); i++) {
            if (measured[i] == null) continue;
            Track track = album.get(i);
            listener.onTagged(track, writer.write(track.getPath(), measured[i], albumLoudness));
        }
    }

    static boolean coversAlbum(List<Track> measured, List<Track> library) {
        if (library == null || library.isEmpty()) return false;
        Set<String> paths = new HashSet<>();
        for (Track track : measured) paths.add(track.getPath());
        for (Track track : library) {
            if (!paths.contains(track.getPath())) return false;
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
