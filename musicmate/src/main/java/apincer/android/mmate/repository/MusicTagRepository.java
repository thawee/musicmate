package apincer.android.mmate.repository;

import android.content.Context;

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
    public static List<String> lossyAudioFormatList;

    MusicTagRepository() {
        lossyAudioFormatList = new ArrayList<>();
        lossyAudioFormatList.add("MP3");
        lossyAudioFormatList.add("AAC");
        lossyAudioFormatList.add("WMA");
    }

    private static Box<MusicTag> getMusicTagBox() {
        return ObjectBox.get().boxFor(MusicTag.class);
    }

    public static void saveTag(MusicTag tag) {
       // ObjectBox.get().runInTx(() -> {
            //if(tag.getId()!=0) {
            //    tagBox.remove(tag);
            //}
       // MusicTagUtils.initMusicTag(tag);
        if(StringUtils.isEmpty(tag.getUniqueKey())) {
            tag.setUniqueKey(tag.getPath()+"_"+ tag.getAudioStartTime());
        }
        getMusicTagBox().put(tag); // add or update
      //  });
    }

    public static void removeTag(MusicTag tag) {
        //ObjectBox.get().runInTx(() -> {
            if (tag.getId() != 0) {
                getMusicTagBox().remove(tag);
            }
       // });
    }

    public static void cleanMusicMate() {
        try {
            List<MusicTag> list = getMusicTagBox().getAll();
            for(int i=0; i<list.size();i++) {
                MusicTag mdata = list.get(i);
                String path = mdata.getPath();
                if(!FileRepository.isMediaFileExist(path)) {
                    getMusicTagBox().remove(mdata);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public static List<String> getGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.genre).distinct().findStrings();
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

    public static List<String> getDefaultGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.genre).distinct().findStrings();
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
	
	public static List<String> getDefaultGroupingList(Context context) {
        String[] groupings =  context.getResources().getStringArray(R.array.default_groupings);
        List<String> list = new ArrayList<>(Arrays.asList(groupings));
        Collections.sort(list);
        return list;
    }

    public static List<String> getGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.grouping).distinct().findStrings();
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

    public static List<String> getArtistList() {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.artist).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public static List<String> getDefaultAlbumArtistList(Context context) {
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

    public static List<String> getAlbumList() {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.album).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public static boolean checkSACDOutdated(String path, long lastModified) {
        List<MusicTag> tags = findByPath(path);
        if(tags.isEmpty()) {
            // found new file
            return true;
        }
        if(tags.get(0).getFileLastModified() < lastModified) {
            // found updated, remove old tags, and re scan
            for (MusicTag tag: tags) {
                removeTag(tag);
            }
            return true;
        }
        return false;
    }

    public static MusicTag getOutdatedMusicTag(String path, long lastModified) {
        // return null if not outdate, else return object
        List<MusicTag> tags = findByPath(path);
        if(tags.isEmpty()) {
            // found new file
            return new MusicTag();
        }else if (tags.size() == 1) {
            if(tags.get(0).getFileLastModified() < lastModified) {
                // tag in library already up-to-dated
                //removeTag(tags.get(0));
                return tags.get(0);
            }else {
                return null; // tag database already up to date
            }
        }else {
            // found >1, could be duplicated, clean all up and re scan
            //ObjectBox.get().runInTx(() -> {
                for (MusicTag tag: tags) {
                    getMusicTagBox().remove(tag);
                }
           // });
            return new MusicTag();
        }
    }

    public static MusicTag getAudioTagById(MusicTag md) {
        return getMusicTagBox().get(md.getId());
    }

    public static List<MusicTag> findByPath(String path) {
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.path.equal(path)).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public static List<MusicTag> findMediaByTitle(String title) {
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.title.contains(title).or(MusicTag_.path.contains(title))).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    //public static synchronized List<MusicTag> findMediaTag(SearchCriteria criteria) {
    public static List<MusicTag> findMediaTag(SearchCriteria criteria) {
        List<MusicTag> list = new ArrayList<>();
        if(criteria.getType()==SearchCriteria.TYPE.MY_SONGS) {
            if(StringUtils.isEmpty(criteria.getKeyword()) || Constants.TITLE_ALL_SONGS.equals(StringUtils.trimToEmpty(criteria.getKeyword()))) {
                // default for MY_SONGS
                Query<MusicTag> query = getMusicTagBox().query().order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
            }else if(Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                Query<MusicTag> query = getMusicTagBox().query().filter(tag -> !tag.isMusicManaged()).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
            }else if(Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.fileSizeRatio.less(Constants.MIN_FILE_SIZE_RATIO)).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
            }else if(Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                Query<MusicTag> query = getMusicTagBox().query().order(MusicTag_.title).order(MusicTag_.artist).build();
                List<MusicTag> audioTags = query.find();
                String title = "";
                String artist = "";
                MusicTag prvTag = null;
                for (MusicTag tag : audioTags) {
                    if (StringUtils.isEmpty(title)) {
                        title = tag.getTitle();
                    } else if ((StringUtils.similarity(title, tag.getTitle()) > Constants.MIN_TITLE)) {// ||
                        // found similar title
                        // check artist
                        if ((StringUtils.similarity(artist, tag.getArtist()) > Constants.MIN_ARTIST) ||
                                StringUtils.contains(artist, tag.getArtist())) {
                            if (prvTag != null && !list.contains(prvTag)) {
                                list.add(prvTag);
                            }
                            list.add(tag);
                        } else {
                            // found different artist
                            title = tag.getTitle();
                            artist = tag.getArtist();
                        }
                    } else {
                        // found different title
                        title = tag.getTitle();
                        artist = tag.getArtist();
                    }
                    prvTag = tag;
                }
                query.close();
            }
        }else if(criteria.getType() == SearchCriteria.TYPE.RECORDINGS_QUALITY) {
                Query<MusicTag> query;
                if (StringUtils.isEmpty(criteria.getKeyword()) || Constants.QUALITY_NORMAL.equals(criteria.getKeyword())) {
                    query = getMusicTagBox().query(MusicTag_.mediaQuality.isNull().or(MusicTag_.mediaQuality.equal(""))).build();
                } else {
                    query = getMusicTagBox().query(MusicTag_.mediaQuality.equal(StringUtils.trimToEmpty(criteria.getKeyword()))).build();
                }
                list = query.find();
                query.close();

        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_DSD.equals(criteria.keyword)){
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.audioBitsDepth.equal(1)).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_PCM_MQA.equals(criteria.keyword)){
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.mqaInd.equal("MQA").or(MusicTag_.mqaInd.equal("MQA Studio"))).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HI_QUALITY.equals(criteria.keyword)){
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.lossless.notEqual(true).and(MusicTag_.audioBitsDepth.notEqual(1))).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)){
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.lossless.equal(true)
                               // .and(MusicTag_.mqaInd.mqa(false))
                                .and(MusicTag_.mqaInd.notEqual("MQA"))
                                .and(MusicTag_.mqaInd.notEqual("MQA Studio"))
                                .and(MusicTag_.audioBitsDepth.notEqual(1)))
                        .filter(tag -> {
                            // drop from results
                            return !MusicTagUtils.isPCMHiRes(tag); // include to results
                        })
                        .order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIRES.equals(criteria.keyword)){
               // Query<MusicTag> query = getMusicTagBox().query(MusicTag_.mqa.equal(false)).filter(tag -> {
            Query<MusicTag> query = getMusicTagBox().query().filter(tag -> {
                    // drop from results
                    return MusicTagUtils.isPCMHiRes(tag); // && !tag.isMQA(); // include to results
                }).order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GROUPING){
                Query<MusicTag> query;
                if(StringUtils.isEmpty(criteria.getKeyword()) || StringUtils.EMPTY.equalsIgnoreCase(criteria.getKeyword())) {
                    query = getMusicTagBox().query(MusicTag_.grouping.isNull().or(MusicTag_.grouping.equal(""))).order(MusicTag_.title).order(MusicTag_.artist).build();
                }else {
                    query = getMusicTagBox().query(MusicTag_.grouping.equal(criteria.getKeyword())).order(MusicTag_.title).order(MusicTag_.artist).build();
                }
                list = query.find();
                query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GENRE){
                Query<MusicTag> query;
                if(StringUtils.isEmpty(criteria.getKeyword()) || StringUtils.EMPTY.equalsIgnoreCase(criteria.getKeyword())) {
                    query = getMusicTagBox().query(MusicTag_.genre.isNull().or(MusicTag_.genre.equal(""))).order(MusicTag_.title).order(MusicTag_.artist).build();
                }else {
                    query = getMusicTagBox().query(MusicTag_.genre.equal(criteria.getKeyword())).order(MusicTag_.title).order(MusicTag_.artist).build();
                }
                list = query.find();
                query.close();
                // }else if(criteria.getType()== SearchCriteria.TYPE.AUDIOPHILE){
                //     Query<MusicTag> query = tagBox.query(MusicTag_.audiophile.equal(true)).order(MusicTag_.title).order(MusicTag_.artist).build();
                //     list = query.find();
                //     query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.SEARCH) {
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
                Query<MusicTag> query = getMusicTagBox().query(MusicTag_.title.contains(criteria.getKeyword()) //.filter(tag -> {
                                // drop to results
                                //    return StringUtils.contains(tag.getPath(), criteria.getKeyword()); // include from results
                                //})
                                .or(MusicTag_.artist.contains(criteria.getKeyword())))
                        .order(MusicTag_.title).build();
                list = new ArrayList<>(query.find());
                query.close();
        } else {
                // default for MY_SONGS and others
                Query<MusicTag> query = getMusicTagBox().query().order(MusicTag_.title).order(MusicTag_.artist).build();
                list = query.find();
                query.close();
        }
        return list;
    }

    public static void populateAudioTag(MusicTag md) {
        MusicTag tag = getMusicTagBox().get(md.getId());
        if(tag != null) {
            md.cloneFrom(tag);
        }
    }

    public static List<MusicTag> getAllMusics() {
        return getMusicTagBox().getAll();
    }

    public static List<MusicTag> getAudioTagWithoutLoudness() {
        //Query<MusicTag> query = getMusicTagBox().query(MusicTag_.audioBitsDepth.notEqual(1).and(MusicTag_.trackScanned.equals(true))).build();
        Query<MusicTag> query = getMusicTagBox().query(MusicTag_.audioBitsDepth.notEqual(1)).build();
        List<MusicTag> list = query.find();
        query.close();
        return list;
    }

    public static List<String> getDefaultPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.publisher).distinct().findStrings();
        if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
        }
        String[] genres =  context.getResources().getStringArray(R.array.default_publisher);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public static List<String> getDefaultLanguageList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = getMusicTagBox().query().build().property(MusicTag_.language).distinct().findStrings();
        if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
        }
        String[] genres =  context.getResources().getStringArray(R.array.default_language);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }
}