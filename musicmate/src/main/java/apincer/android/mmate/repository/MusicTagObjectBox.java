package apincer.android.mmate.repository;

import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.objectbox.MusicTag_;
import apincer.android.mmate.objectbox.ObjectBox;
import io.objectbox.Box;
import io.objectbox.query.Query;

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

    public static List<MusicTag> findMySongs(SearchCriteria criteria) {
        Query<MusicTag> query = getMusicTagBox().query().order(MusicTag_.title).order(MusicTag_.artist).build();
        List<MusicTag> list = query.find();
        query.close();

        return list;
    }

    public static List<MusicTag> findMyIncommingSongs(SearchCriteria criteria) {
        Query<MusicTag> query = getMusicTagBox().query().filter(tag -> !tag.isMusicManaged()).order(MusicTag_.title).order(MusicTag_.artist).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public static List<MusicTag> findMyBrokenSongs(SearchCriteria criteria) {
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.fileSizeRatio.less(Constants.MIN_FILE_SIZE_RATIO).or(MusicTag_.mmReadError.equal(true))).orderDesc(MusicTag_.fileSizeRatio).order(MusicTag_.fileSize).build();
         List<MusicTag> list = query.find();
         query.close();
        return list;
    }

    public static List<MusicTag> findByPath(String path) {
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.path.equal(path)).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public static List<MusicTag> findByTitle(String title) {
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.title.contains(title).or(MusicTag_.path.contains(title))).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }
}
