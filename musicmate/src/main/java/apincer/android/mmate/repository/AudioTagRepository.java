package apincer.android.mmate.repository;

import android.content.Context;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.objectbox.AudioTag_;
import apincer.android.mmate.objectbox.ObjectBox;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.StringUtils;
import io.objectbox.Box;
import io.objectbox.query.Query;
import io.objectbox.query.QueryFilter;
import timber.log.Timber;

public class AudioTagRepository {
    private static final Box<AudioTag> tagBox = ObjectBox.get().boxFor(AudioTag.class);
    public static List<String> lossyAudioFormatList = new ArrayList<>();
    private static AudioTagRepository INSTANCE;

    private AudioTagRepository() {
        lossyAudioFormatList.add("MP3");
        lossyAudioFormatList.add("AAC");
        lossyAudioFormatList.add("WMA");
    }

    public static AudioTagRepository getInstance() {
        if(INSTANCE==null) {
            INSTANCE = new AudioTagRepository();
        }
        return INSTANCE;
    }

    public void saveTag(AudioTag tag) {
        ObjectBox.get().runInTx(() -> {
            if(tag.getId()!=0) {
                tagBox.remove(tag);
            }
            tagBox.put(tag);
        });
    }

    public void removeTag(AudioTag tag) {
        ObjectBox.get().runInTx(() -> {
            if (tag.getId() != 0) {
                tagBox.remove(tag);
            }
        });
    }

    public static void cleanMusicMate() {
        try {
            List<AudioTag> list = tagBox.getAll();
            for(int i=0; i<list.size();i++) {
                AudioTag mdata = list.get(i);
                if(!AudioFileRepository.isMediaFileExist(mdata.getPath())) {
                    tagBox.remove(mdata);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public List<String> getGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(AudioTag_.genre).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        /*String[] genres =  context.getResources().getStringArray(R.array.default_genres);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        } */

        Collections.sort(list);
        return list;
    }

    public List<String> getDefaultGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names = tagBox.query().build().property(AudioTag_.genre).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
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
        String[] names = tagBox.query().build().property(AudioTag_.grouping).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
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
        String[] names = tagBox.query().build().property(AudioTag_.artist).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public List<String> getDefaultAlbumArtistList(Context context) {
        List<String> list = new ArrayList<>();
       // String[] names = tagBox.query().build().property(AudioTag_.albumArtist).distinct().findStrings();
        //if(names!=null) {
       //     list.addAll(Arrays.asList(names));
       // }
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
        String[] names = tagBox.query().build().property(AudioTag_.album).distinct().findStrings();
        if(names!=null) {
            list.addAll(Arrays.asList(names));
        }
        Collections.sort(list);
        return list;
    }

    public boolean isMediaOutdated(String path, long lastModified) {
        List<AudioTag> tags = findByPath(path);
        if(tags.size()==1 && tags.get(0).getLastModified() >= lastModified) {
            // tag in library already up-to-dated
            return false;
        }

        return true;
    }

    public long getAudioTagId(AudioTag tag) {
        List<AudioTag> tags = findByPath(tag.getPath());
        if(tags.size()==0) {
            return 0;
        }else if(tags.size()==1) {
            // tag in library already up-to-dated
            return tags.get(0).getId();
        }else {
           //TODO support cue or iso
            // check trackNo
            for(AudioTag t: tags)  {
                if(!t.isCueSheet()) {
                    removeTag(t);
                }else if(t.getTrack().equals(tag.getTrack())){
                    return t.getId();
                }
            }
        }

        return 0;
    }

    private List<AudioTag> findByPath(String path) {
        Query<AudioTag> query = tagBox.query(AudioTag_.path.equal(path)).build();
        List<AudioTag> list = query.find();
        query.close();
        return list;
    }

    private static String parseSampleRateString(String keyword) {
        String []text = keyword.split(" ");
        int rate = 0;
        float val = 0;
        try {
            val = NumberFormat.getInstance().parse(text[0]).floatValue();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if("kHz".equalsIgnoreCase(text[1])) {
            rate = (int) (val*1000);
        }else if("MHz".equalsIgnoreCase(text[1])) {
            rate = (int) (val*1000000);
        }
        return String.valueOf(rate);
    }

    /*
    private int parseSamplingRate(String samplingRate) {
        int sampleRate = 0;
        String str = "";

        for(char ch: samplingRate.toCharArray()) {
            if(Character.isDigit(ch)) {
                str = str+ch;
            }else if(!Character.isDigit(ch)){
                break;
            }
        }
        try {
            sampleRate = Integer.parseInt(str);
        }catch (Exception ex){
            Timber.e(ex);
        }
        return sampleRate;
    }

    private int parseSamplingRate(String samplingRate, boolean bitdept) {
        int sampleRate = 0;
        String str = "";
        boolean start = bitdept;
        for(char ch: samplingRate.toCharArray()) {
            if(start && ch=='/') {
                break; //
            }else if(!start && ch=='/') {
                start = true;
                continue;
            }
            if(start && Character.isDigit(ch)) {
                str = str+ch;
            }else if(start && !Character.isDigit(ch)){
                break;
            }
        }
        try {
            sampleRate = Integer.parseInt(str);
        }catch (Exception ex){
            Timber.e(ex);
        }
        return sampleRate;
    } */

    public List<AudioTag> findMediaByTitle(String title) throws SQLException {
        Query<AudioTag> query = tagBox.query(AudioTag_.title.contains(title).or(AudioTag_.path.contains(title))).build();
        List<AudioTag> list = query.find();
        query.close();
        return list;
    }

    public synchronized List findMediaTag(SearchCriteria criteria) {
        List list = new ArrayList();
        if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_DSD.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query(AudioTag_.audioBitsPerSample.equal(1)).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_PCM_MQA.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query(AudioTag_.mqa.equal(true)).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_QUALITY.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query(AudioTag_.lossless.notEqual(true).and(AudioTag_.audioBitsPerSample.notEqual(1))).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query(AudioTag_.lossless.equal(true).and(AudioTag_.audioBitsPerSample.notEqual(1)))
                    .filter(new QueryFilter<AudioTag>() {
                        @Override
                        public boolean keep(AudioTag tag) {
                            if (!AudioTagUtils.isPCMHiRes(tag) && !tag.isMQA()) {
                                return true; // include to results
                            } else {
                                return false; // drop from results
                            }
                        }
                    })
                    .order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIRES.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query().filter(new QueryFilter<AudioTag>() {
                @Override
                public boolean keep(AudioTag tag) {
                    if (AudioTagUtils.isPCMHiRes(tag) && !tag.isMQA()) {
                        return true; // include to results
                    } else {
                        return false; // drop from results
                    }
                }
            }).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)){
            Query<AudioTag> query = tagBox.query().filter(new QueryFilter<AudioTag>() {
                @Override
                public boolean keep(AudioTag tag) {
                    if (AudioTagUtils.isPCMLossless(tag) && !tag.isMQA()) {
                    //if (AudioTagUtils.isHiRes(tag) && !tag.isMQA()) {
                        return true; // include to results
                    } else {
                        return false; // drop from results
                    }
                }
            }).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()==SearchCriteria.TYPE.MY_SONGS && Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())){
            Query<AudioTag> query = tagBox.query().filter(new QueryFilter<AudioTag>() {
                @Override
                public boolean keep(AudioTag tag) {
                    return !tag.isManaged();
                }
            }).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GROUPING){
            Query<AudioTag> query = tagBox.query(AudioTag_.grouping.equal(criteria.getKeyword())).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.GENRE){
            Query<AudioTag> query = tagBox.query(AudioTag_.genre.equal(criteria.getKeyword())).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.AUDIOPHILE){
            Query<AudioTag> query = tagBox.query(AudioTag_.audiophile.equal(true)).order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }else if(criteria.getType()== SearchCriteria.TYPE.MY_SONGS && Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())){
            Query<AudioTag> query = tagBox.query().order(AudioTag_.title).order(AudioTag_.artist).build();
            List<AudioTag> audioTags = query.find();
            String title = "";
            String artist = "";
            AudioTag prvTag = null;
            for (AudioTag tag: audioTags) {
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
            list = new ArrayList();
            // search title only, limit 5 songs
            Query<AudioTag> query = tagBox.query().filter(new QueryFilter<AudioTag>() {
                @Override
                public boolean keep(AudioTag tag) {
                    if (StringUtils.contains(tag.getTitle(), criteria.getKeyword())) {
                        return true; // include from results
                    } else {
                        return false; // drop to results
                    }
                }
            }).order(AudioTag_.artist).order(AudioTag_.title).build();
            List titles = query.find();
            if(titles.size() > 0) {
                AudioTag title = new AudioTag();
                title.setTitle("Top Results");
                title.setId(99999999);
                list.add(title);
                list.addAll(titles);
            }
            query.close();

            // search path
            query = tagBox.query().filter(new QueryFilter<AudioTag>() {
                @Override
                public boolean keep(AudioTag tag) {
                    if (StringUtils.contains(tag.getPath(), criteria.getKeyword())) {
                        return true; // include from results
                    } else {
                        return false; // drop to results
                    }
                }
            }).order(AudioTag_.artist).order(AudioTag_.title).build();
            AudioTag title = new AudioTag();
            title.setTitle("Search Results");
            title.setId(888888888);
            list.add(title);
            list.addAll(query.find());
            query.close();
        } else {
            // for MY_SONGS and others
          //  return tagBox.getAll();
            Query<AudioTag> query = tagBox.query().order(AudioTag_.title).order(AudioTag_.artist).build();
            list = query.find();
            query.close();
        }
        return list;
    }

    public void populateAudioTag(AudioTag md) {
        AudioTag tag = tagBox.get(md.getId());
        if(tag != null) {
            md.cloneFrom(tag);
        }
    }

    public List<AudioTag> getAllMusics() {
        return tagBox.getAll();
    }
}