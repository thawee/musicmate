package apincer.android.mmate.broadcast;

import java.util.Collection;
import java.util.List;

import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.SearchCriteria;

public class AudioTagEditEvent {
    public final String message;
    public final SearchCriteria criteria;
    public final List<AudioTag> items;

    public AudioTagEditEvent(String message, SearchCriteria criteria, List<AudioTag> items) {
        this.message = message;
        this.criteria = criteria;
        this.items = items;
    }

    public SearchCriteria getSearchCriteria() {
        return criteria;
    }

    public Collection<? extends AudioTag> getItems() {
        return items;
    }
}