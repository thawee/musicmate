package apincer.android.mmate.repository;

import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.objectbox.ObjectBox;
import io.objectbox.Box;

public class MusicTagObjectBox {
    private static Box<MusicTag> getMusicTagBox() {
        return ObjectBox.get().boxFor(MusicTag.class);
    }

    public static void delete(MusicTag tag) {
        if (tag.getId() != 0) {
            getMusicTagBox().remove(tag);
        }
    }

    public static void save(MusicTag tag) {
        getMusicTagBox().put(tag); // add or update
    }
}
