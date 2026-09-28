package apincer.android.mmate.ui;

import apincer.music.core.model.Track;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns library multi-selection state. Selection is keyed by stable track identity rather
 * than row position, so refreshing, re-sorting, or replacing the list can never retarget a
 * batch action at a track the user did not select. Kept free of Android dependencies so it
 * can be unit tested.
 */
public class LibrarySelectionModel {

    public interface SelectionListener {
        void onSelectionChanged(int count, List<Track> selectedTracks);
    }

    private final Set<String> selectedKeys = new LinkedHashSet<>();
    private final List<Track> tracks = new ArrayList<>();
    private final List<Track> selectedTracks = new ArrayList<>();
    private final SelectionListener listener;

    public LibrarySelectionModel() {
        this(null);
    }

    public LibrarySelectionModel(SelectionListener listener) {
        this.listener = listener;
    }

    /**
     * Applies a new list. Selections whose track is no longer present are dropped; the rest
     * stay selected and follow their track to the new position.
     */
    public void setTracks(List<Track> newTracks) {
        List<String> previousOrder = orderedSelectedKeys();
        tracks.clear();
        if (newTracks != null) {
            tracks.addAll(newTracks);
        }
        selectedKeys.retainAll(currentKeys());
        resolveSelectedTracks();
        if (!orderedSelectedKeys().equals(previousOrder)) {
            publish();
        }
    }

    public boolean hasSelection() {
        return !selectedKeys.isEmpty();
    }

    public int getSelectionCount() {
        return selectedKeys.size();
    }

    public List<Track> getSelectedTracks() {
        return new ArrayList<>(selectedTracks);
    }

    public boolean isSelected(int position) {
        Track track = trackAt(position);
        return track != null && selectedKeys.contains(keyOf(track));
    }

    public void select(int position) {
        Track track = trackAt(position);
        if (track == null) {
            return;
        }
        if (selectedKeys.add(keyOf(track))) {
            resolveSelectedTracks();
            publish();
        }
    }

    public void deselect(int position) {
        Track track = trackAt(position);
        if (track == null) {
            return;
        }
        if (selectedKeys.remove(keyOf(track))) {
            resolveSelectedTracks();
            publish();
        }
    }

    public void toggle(int position) {
        if (isSelected(position)) {
            deselect(position);
        } else {
            select(position);
        }
    }

    /** Selects every track, or clears when the whole list is already selected. */
    public void selectAll() {
        if (tracks.isEmpty()) {
            return;
        }
        if (selectedKeys.size() == tracks.size()) {
            clear();
            return;
        }
        selectedKeys.clear();
        selectedKeys.addAll(currentKeys());
        resolveSelectedTracks();
        publish();
    }

    public void clear() {
        if (selectedKeys.isEmpty()) {
            return;
        }
        selectedKeys.clear();
        resolveSelectedTracks();
        publish();
    }

    private Track trackAt(int position) {
        return position >= 0 && position < tracks.size() ? tracks.get(position) : null;
    }

    private Set<String> currentKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Track track : tracks) {
            keys.add(keyOf(track));
        }
        return keys;
    }

    /** Resolves selections in the order tracks currently appear on screen. */
    private void resolveSelectedTracks() {
        selectedTracks.clear();
        for (Track track : tracks) {
            if (selectedKeys.contains(keyOf(track))) {
                selectedTracks.add(track);
            }
        }
    }

    /** Selected keys in current on-screen order, used to detect real selection changes. */
    private List<String> orderedSelectedKeys() {
        List<String> keys = new ArrayList<>();
        for (Track track : tracks) {
            String key = keyOf(track);
            if (selectedKeys.contains(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private void publish() {
        if (listener != null) {
            listener.onSelectionChanged(selectedKeys.size(), getSelectedTracks());
        }
    }

    /**
     * Stable identity for a library row. Persisted tracks carry a unique key; containers
     * are identified by their type and title because they are synthesized per query.
     */
    private static String keyOf(Track track) {
        if (track == null) {
            return "";
        }
        String uniqueKey = track.getUniqueKey();
        if (uniqueKey != null && !uniqueKey.isEmpty()) {
            return "u:" + uniqueKey;
        }
        if (track.isContainer()) {
            return "c:" + track.getContainerType() + "|" + track.getTitle();
        }
        String path = track.getPath();
        if (path != null && !path.isEmpty()) {
            return "p:" + path;
        }
        return "i:" + track.getId();
    }
}
