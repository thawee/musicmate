package apincer.android.mmate.ui;

import java.util.HashSet;
import java.util.Set;

public class MySelectionTracker {
    private final Set<Long> selection = new HashSet<>();
    private SelectionObserver observer;

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    public Set<Long> getSelection() {
        return new HashSet<>(selection);
    }

    public void select(long id) {
        if (selection.add(id)) {
            if (observer != null) observer.onSelectionChanged();
        }
    }

    public void deselect(long id) {
        if (selection.remove(id)) {
            if (observer != null) observer.onSelectionChanged();
        }
    }

    public boolean isSelected(long id) {
        return selection.contains(id);
    }

    public void clearSelection() {
        if (!selection.isEmpty()) {
            selection.clear();
            if (observer != null) observer.onSelectionChanged();
        }
    }

    public void setObserver(SelectionObserver observer) {
        this.observer = observer;
    }

    public interface SelectionObserver {
        void onSelectionChanged();
    }

    public java.util.Set<apincer.music.core.model.Track> getSelectionAsTracks() {
        java.util.Set<apincer.music.core.model.Track> tracks = new java.util.HashSet<>();
        java.util.List<apincer.music.core.model.Track> all = apincer.android.mmate.ui.compose.ListInterop.getTracks();
        for (Long pos : selection) {
            if (pos >= 0 && pos < all.size()) {
                tracks.add(all.get(pos.intValue()));
            }
        }
        return tracks;
    }

}