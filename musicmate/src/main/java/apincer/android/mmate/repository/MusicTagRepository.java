package apincer.android.mmate.repository;

import android.content.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.objectbox.MusicTag_;
import apincer.android.mmate.objectbox.ObjectBox;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import io.objectbox.Box;
import io.objectbox.query.Query;
import timber.log.Timber;

public class MusicTagRepository {
    private static Box<MusicTag> tagBox;
    public static List<String> lossyAudioFormatList;
    private static MusicTagRepository INSTANCE;

    private MusicTagRepository() {
        lossyAudioFormatList = new ArrayList<>();
        lossyAudioFormatList.add("MP3");
        lossyAudioFormatList.add("AAC");
        lossyAudioFormatList.add("WMA");
        tagBox = ObjectBox.get().boxFor(MusicTag.class);
    }

    public static MusicTagRepository getInstance() {
        if(INSTANCE==null) {
            INSTANCE = new MusicTagRepository();
        }
        return INSTANCE;
    }

    public void saveTag(MusicTag tag) {
        ObjectBox.get().runInTx(() -> {
            if(tag.getId()!=0) {
                tagBox.remove(tag);
            }
            tagBox.put(tag);
        });
    }

    public void removeTag(MusicTag tag) {
        ObjectBox.get().runInTx(() -> {
            if (tag.getId() != 0) {
                tagBox.remove(tag);
            }
        });
    }

    public static void cleanMusicMate() {
        try {
            List<MusicTag> list = tagBox.getAll();
            for(int i=0; i<list.size();i++) {
                MusicTag mdata = list.get(i);
                String path = mdata.getPath();
                if(!FileRepository.isMediaFileExist(path)) {
                    tagBox.remove(mdata);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public List<String> getGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(MusicTag_.genre).distinct().findStrings();
        if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
        }

        Collections.sort(list);
        return list;
    }

    public List<String> getDefaultGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(MusicTag_.genre).distinct().findStrings();
        if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
           // list.addAll(Arrays.asList(names));
        }
        String[] genres =  context.getResources().getStringArray(R.array.default_genres);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }
	
	public List<String> getDefaultGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        String[] groupings =  context.getResources().getStringArray(R.array.default_groupings);
        list.addAll(Arrays.asList(groupings));
        Collections.sort(list);
        return list;
    }

    public List<String> getGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(MusicTag_.grouping).distinct().findStrings();
        if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
            //list.addAll(Arrays.asList(names));
        }
        String[] groupings =  context.getResources().getStringArray(R.array.default_groupings);
        for(String grp: groupings) {
            if(!list.contains(grp)) {
                list.add(grp);
            }
        }
        Collections.sort(list);
        return list;
    }

    public List<String> getArtistList() {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(MusicTag_.artist).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public List<String> getDefaultAlbumArtistList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names =  context.getResources().getStringArray(R.array.default_album_artist);
        for(String name: names) {
            if(!list.contains(name)) {
                list.add(name);
            }
        }
        Collections.sort(list);
        return list;
    }

    public List<String> getAlbumList() {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(MusicTag_.album).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public boolean checkSACDOutdated(String path, long lastModified) {
        List<MusicTag> tags = findByPath(path);
        if(tags == null || tags.isEmpty()) {
            // found new file
            return true;
        }
        if(tags.get(0).getLastModified() < lastModified) {
            // found updated, remove old tags, and re scan
            for (MusicTag tag: tags) {
                removeTag(tag);
            }
            return true;
        }
        return false;
    }

    public boolean checkJAudioTaggerOutdated(String path, long lastModified) {
        List<MusicTag> tags = findByPath(path);
        if(tags == null || tags.isEmpty()) {
            // found new file
            return true;
        }else if (tags.size() == 1) {
            if(tags.get(0).getLastModified() < lastModified) {
                // tag in library already up-to-dated
                removeTag(tags.get(0));
                return true;
            }
            return false;
        }else {
            // found >1, could be duplicated, clean up
            for (MusicTag tag: tags) {
                removeTag(tag);
            }
        }
        return true;
    }

    public MusicTag getAudioTagById(MusicTag md) {
        return tagBox.get(md.getId());
    }

    public List<MusicTag> findByPath(String path) {
        Query<MusicTag> query = tagBox.query(MusicTag_.path.equal(path)).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public List<MusicTag> findMediaByTitle(String title) throws SQLException {
        Query<MusicTag> query = tagBox.query(MusicTag_.title.contains(title).or(MusicTag_.path.contains(title))).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public synchronized List<MusicTag> findMediaTag(SearchCriteria criteria) {
        List<MusicTag> list = new ArrayList<>();
        if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_DSD.equals(criteria.keyword)){
            Query<MusicTag> query = tagBox.query(MusicTag_.audioBitsPerSample.equal(1)).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_PCM_MQA.equals(criteria.keyword)){
            Query<MusicTag> query = tagBox.query(MusicTag_.mqa.equal(true)).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_QUALITY.equals(criteria.keyword)){
            Query<MusicTag> query = tagBox.query(MusicTag_.lossless.notEqual(true).and(MusicTag_.audioBitsPerSample.notEqual(1))).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)){
            Query<MusicTag> query = tagBox.query(MusicTag_.lossless.equal(true).and(MusicTag_.audioBitsPerSample.notEqual(1)))
                    .filter(tag -> {
                        // drop from results
                        return !MusicTagUtils.isPCMHiRes(tag) && !tag.isMQA(); // include to results
                    })
                    .order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIRES.equals(criteria.keyword)){
            Query<MusicTag> query = tagBox.query().filter(tag -> {
                // drop from results
                return MusicTagUtils.isPCMHiRes(tag) && !tag.isMQA(); // include to results
            }).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()==SearchCriteria.TYPE.MY_SONGS && Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())){
            Query<MusicTag> query = tagBox.query().filter(tag -> !tag.isManaged()).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GROUPING){
            Query<MusicTag> query;
            if(StringUtils.isEmpty(criteria.getKeyword()) || StringUtils.EMPTY.equalsIgnoreCase(criteria.getKeyword())) {
                query = tagBox.query(MusicTag_.grouping.isNull().or(MusicTag_.grouping.equal(""))).order(MusicTag_.title).order(MusicTag_.artist).build();
            }else {
                query = tagBox.query(MusicTag_.grouping.equal(criteria.getKeyword())).order(MusicTag_.title).order(MusicTag_.artist).build();
            }
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GENRE){
            Query<MusicTag> query;
            if(StringUtils.isEmpty(criteria.getKeyword()) || StringUtils.EMPTY.equalsIgnoreCase(criteria.getKeyword())) {
                query = tagBox.query(MusicTag_.genre.isNull().or(MusicTag_.genre.equal(""))).order(MusicTag_.title).order(MusicTag_.artist).build();
            }else {
                query = tagBox.query(MusicTag_.genre.equal(criteria.getKeyword())).order(MusicTag_.title).order(MusicTag_.artist).build();
            }
           // Query<AudioTag> query = tagBox.query(AudioTag_.genre.equal(criteria.getKeyword())).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIOPHILE){
            Query<MusicTag> query = tagBox.query(MusicTag_.audiophile.equal(true)).order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.MY_SONGS && Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())){
            Query<MusicTag> query = tagBox.query().order(MusicTag_.title).order(MusicTag_.artist).build();
            List<MusicTag> audioTags = query.find();
            String title = "";
            String artist = "";
            MusicTag prvTag = null;
            for (MusicTag tag: audioTags) {
                if(StringUtils.isEmpty(title)) {
                    title = tag.getTitle();
                }else if((StringUtils.similarity(title, tag.getTitle()) > Constants.MIN_TITLE)) {// ||
                    // found similar title
                    // check artist
                    if((StringUtils.similarity(artist, tag.getArtist()) > Constants.MIN_ARTIST) ||
                            StringUtils.contains(artist, tag.getArtist())) {
                        if(prvTag !=null && !list.contains(prvTag)) {
                            list.add(prvTag);
                        }
                        list.add(tag);
                    }else {
                        // found different artist
                        title = tag.getTitle();
                        artist = tag.getArtist();
                    }
                }else {
                    // found different title
                    title = tag.getTitle();
                    artist = tag.getArtist();
                }
                prvTag = tag;
            }
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.SEARCH) {
            list = new ArrayList<>();
            // search title only, limit 5 songs
            /*Query<AudioTag> query = tagBox.query().filter(tag -> {
                // drop to results
                return StringUtils.contains(tag.getTitle(), criteria.getKeyword()); // include from results
            }).order(AudioTag_.artist).order(AudioTag_.title).build();
            List titles = query.find();
            if(titles.size() > 0) {
                AudioTag title = new AudioTag();
                title.setTitle("Top Results");
                title.setId(99999999);
                list.add(title);
                list.addAll(titles);
            }
            query.close(); */

            // search path
            Query<MusicTag> query = tagBox.query(MusicTag_.title.contains(criteria.getKeyword()) //.filter(tag -> {
                // drop to results
            //    return StringUtils.contains(tag.getPath(), criteria.getKeyword()); // include from results
            //})
                    .or(MusicTag_.artist.contains(criteria.getKeyword())))
                //.order(AudioTag_.artist).order(AudioTag_.title).build();
                    .order(MusicTag_.title).build();
            list.addAll(query.find());
            query.close();
        } else {
            // for MY_SONGS and others
            Query<MusicTag> query = tagBox.query().order(MusicTag_.title).order(MusicTag_.artist).build();
            list = query.find();
            query.close();
        }
        return list;
    }

    public void populateAudioTag(MusicTag md) {
        MusicTag tag = tagBox.get(md.getId());
        if(tag != null) {
            md.cloneFrom(tag);
        }
    }

    public List<MusicTag> getAllMusics() {
        return tagBox.getAll();
    }

    public List<MusicTag> getAudioTagWithoutLoudness() {
        Query<MusicTag> query = tagBox.query(MusicTag_.loudnessIntegrated.isNull().or(MusicTag_.loudnessIntegrated.equal(""))).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }
}