package apincer.android.mmate.service;

import java.util.Collection;
import java.util.List;

import apincer.android.mmate.objectbox.AudioTag;

public class AudioTagCommandEvent {
    public final String message;
    public final List<AudioTag> items;

    public AudioTagCommandEvent(String message, List<AudioTag> items) {
        this.message = message;
        this.items = items;
    }

    public Collection<? extends AudioTag> getItems() {
        return items;
    }
}