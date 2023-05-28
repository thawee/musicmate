package apincer.android.mmate.broadcast;

import java.util.Collection;
import java.util.List;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;

public class AudioTagEditEvent {
    public final String message;
    public final SearchCriteria criteria;
    public final List<MusicTag> items;

    public AudioTagEditEvent(String message, SearchCriteria criteria, List<MusicTag> items) {
        this.message = message;
        this.criteria = criteria;
        this.items = items;
    }

    public SearchCriteria getSearchCriteria() {
        return criteria;
    }

    public Collection<? extends MusicTag> getItems() {
        return items;
    }
}