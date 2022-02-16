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


/*
    private boolean isHiRes(String bitDepth, String sampleRate) {
        if(extractNumber(bitDepth) >= Constants.QUALITY_BIT_DEPTH_HD) { // &&  extractNumber(sampleRate) > Constants.QUALITY_SAMPLING_RATE_48_KHZ) {
            return true;
        } else {
            return sampleRate != null && sampleRate.endsWith("MHz");
        }
    }

    private float extractNumber(String text) {
        if(text!=null) {
            String str = "";
            for (char ch : text.toCharArray()) {
                if (Character.isDigit(ch) || ch == '.') {
                    str = str + ch;
                } else {
                    break;
                }
            }
            try {
                return Float.parseFloat(str);
            } catch (Exception ex) {
            }
        }
        //  LogHelper.d(TAG,samplingRate+":"+sampleRate);
        return 0;
    } */

    public List<String> getGenreList(Context context) {
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

    public List<String> getAlbumArtistList(Context context) {
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
    }

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
                    /*if (tag.getPath().contains("/Music/")) {
                        return false; // drop from results
                    } else {
                        return true; // include to results
                    } */
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
               // StringUtils.contains(title, tag.getTitle())) {
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

/*
    @Deprecated
    public List<AudioTag> findMedia(String title) throws SQLException {
        QueryBuilder<AudioTag, String> qb = dao.queryBuilder();
        SelectArg selectTitle = new SelectArg();
        SelectArg selectPath = new SelectArg();
        qb.where().like("title", selectTitle).or().like("mediaPath", selectPath);
        // prepare it so it is ready for later query or iterator calls
        PreparedQuery<AudioTag> preparedQuery = qb.prepare();

        selectTitle.setValue(StringUtils.trimToEmpty(title));
        selectPath.setValue(StringUtils.trimToEmpty(title));
        return dao.query(preparedQuery);
    } */

    /*
    private static class QueryAsyncTask extends
            AsyncTask<SearchCriteria, Void, List<AudioTag>> {

        private Dao asyncTaskDao;
        private AudioTagRepository delegate = null;

        QueryAsyncTask(Dao dao) {
            asyncTaskDao = dao;
        }

        private QueryBuilder orderBy(QueryBuilder builder) {
            return builder.orderByRaw("title COLLATE NOCASE, artist COLLATE NOCASE");
            //return builder;
        }

        @Override
        protected List<AudioTag> doInBackground(final SearchCriteria... params) {
            //return asyncTaskDao.find(params[0]);
            try {
                if(params.length==0 || params[0]==null || params[0].getType()== SearchCriteria.TYPE.ALL) {
                    //return asyncTaskDao.queryBuilder().orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(asyncTaskDao.queryBuilder()).query();
               }else if(params[0].getType()== SearchCriteria.TYPE.DOWNLOAD){
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    qb.where().not().like("mediaPath", "%/Music/%");
                   // return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                }else if(params[0].getType()== SearchCriteria.TYPE.SIMILAR_TITLE){
                    //List<MediaMetadata> list = asyncTaskDao.queryBuilder().orderBy("title", true).orderBy("artist", true).query();
                    List<AudioTag> list = orderBy(asyncTaskDao.queryBuilder()).query();
                    List<AudioTag> items = new ArrayList();
                    AudioTag pmdata = null;
                    boolean preAdded = false;
                    for(AudioTag mdata: list) {
                        //similarity
                        if (pmdata!=null && (StringUtils.similarity(mdata.getTitle(), pmdata.getTitle())>Constants.MIN_TITLE_ONLY)) {
                            if(!preAdded && pmdata != null) {
                                items.add(pmdata);
                            }
                            items.add(mdata);
                            preAdded = true;
                        }else {
                            preAdded = false;
                        }
                        pmdata = mdata;
                    }
                    return items;
                }else if(params[0].getType()== SearchCriteria.TYPE.SIMILAR_TITLE_ARTIST){
                    //List<MediaMetadata> list = asyncTaskDao.queryBuilder().orderBy("title", true).orderBy("artist", true).query();
                    List<AudioTag> list = orderBy(asyncTaskDao.queryBuilder()).query();
                    List<AudioTag> items = new ArrayList();
                    AudioTag pmdata = null;
                    boolean preAdded = false;
                    for(AudioTag mdata: list) {
                        //similarity
                        if (pmdata!=null && (StringUtils.similarity(mdata.getTitle(), pmdata.getTitle())>Constants.MIN_TITLE) &&
                                (StringUtils.similarity(mdata.getArtist(), pmdata.getArtist())>Constants.MIN_ARTIST)) {
                            if(!preAdded && pmdata != null) {
                                items.add(pmdata);
                            }
                            items.add(mdata);
                            preAdded = true;
                        }else {
                            preAdded = false;
                        }
                        pmdata = mdata;
                    }
                    return items;
                }else if(params[0].getType()== SearchCriteria.TYPE.GENRE){
                    String genre = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    if("EMPTY".equalsIgnoreCase(genre)) {
                        qb.where().isNull("genre").or().eq("genre", "");
                    }else {
                        qb.where().eq("genre", sqlEscapeString(genre));
                    }
                    //return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                } else if(params[0].getType()== SearchCriteria.TYPE.GROUPING){
                    String grouping = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    if(SearchCriteria.DEFAULT_MUSIC_GROUPING.equalsIgnoreCase(grouping)) {
                        qb.where().isNull("grouping").or().eq("grouping","");
                    }else {
                        qb.where().eq("grouping", sqlEscapeString(grouping));
                    }
                    //return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                }else if(params[0].getType()== SearchCriteria.TYPE.AUDIO_FORMAT){
                    String format = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    if(SearchCriteria.OTHER_AUDIO_FORMAT.equalsIgnoreCase(format)) {
                       qb.where().in("audioFormat", lossyAudioFormatList);
                    }else if(SearchCriteria.UNKNOWN_AUDIO_FORMAT.equalsIgnoreCase(format)) {
                        qb.where().isNull("audioFormat");
                    }else {
                        int indx = format.indexOf(" ");
                        indx = indx < 0 ? 0 : indx;
                        String audioFormat = format.substring(0, indx);
                        qb.where().eq("audioFormat", audioFormat);
                    }
                    //return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                }else if(params[0].getType()== SearchCriteria.TYPE.AUDIO_SAMPLE_RATE){
                    String sampleRate = parseSmapleRateString(params[0].keyword);
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    //if(SearchCriteria.OTHER_SAMPLING_RATE.equalsIgnoreCase(title)) {
                    //    qb.where().lt("audioBitsPerSample", Constants.QUALITY_BIT_DEPTH_HD).and().lt("audioSampleRate","");
                    //}else {
                        //int indx = title.indexOf("/");
                        //indx = indx < 0 ? 0 : indx;
                        //String bitdepht = title.substring(0, indx);
                        //String sampleRate = title.substring(indx + 1);
                        qb.where().eq("audioSampleRate", sampleRate);
                    //}
                    //return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                } else if (params[0].getType() == SearchCriteria.TYPE.AUDIO_SQ) {
                    String sq = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    if(Constants.AUDIO_SQ_DSD.equalsIgnoreCase(sq)) {
                        qb.where().eq("audioBitsPerSample", Constants.QUALITY_BIT_DEPTH_DSD);
                    }else if(Constants.AUDIO_SQ_PCM_LD.equalsIgnoreCase(sq)) {
                        qb.where().eq("lossless", false).and().gt("audioBitsPerSample", Constants.QUALITY_BIT_DEPTH_DSD);
                    }else if(Constants.AUDIO_SQ_PCM_HD.equalsIgnoreCase(sq)) {
                        qb.where().eq("lossless", true).and().eq("mqa", false).and().ge("audioBitsPerSample", Constants.QUALITY_BIT_DEPTH_HD); //.and().ge("audioSampleRate", Constants.QUALITY_SAMPLING_RATE_SD48);
                    }else if(Constants.AUDIO_SQ_PCM_SD.equalsIgnoreCase(sq)) {
                        qb.where().eq("lossless", true).and().eq("mqa", false).and().eq("audioBitsPerSample", Constants.QUALITY_BIT_DEPTH_SD); //.and().le("audioSampleRate", Constants.QUALITY_SAMPLING_RATE_SD48);
                    }else if(Constants.AUDIO_SQ_PCM_MQA.equalsIgnoreCase(sq)) {
                        qb.where().eq("lossless", true).and().eq("mqa", true);
                    }

                    //return qb.orderBy("title", true).orderBy("artist", true).query();
                    return orderBy(qb).query();
                } else if(params[0].getType()== SearchCriteria.TYPE.SEARCH_BY_ARTIST){
                    String keyword = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    qb.where().like("artist", "%"+sqlEscapeString(keyword)+"%");
                    return orderByForArtist(qb).query();
                } else if(params[0].getType()== SearchCriteria.TYPE.SEARCH_BY_ALBUM){
                    String keyword = params[0].keyword;
                    QueryBuilder qb =asyncTaskDao.queryBuilder();
                    qb.where().eq("album", sqlEscapeString(keyword));
                    return orderByForAlbum(qb).query();
                }else if (params[0].getType() == SearchCriteria.TYPE.SEARCH) {
                    List<AudioTag> list = new ArrayList<AudioTag>();
                    String keyword = StringUtils.trimToEmpty(params[0].keyword);
                    // for for top result (matched title) , artist, album, tracks

                    // top results
                    QueryBuilder<AudioTag, String> qb = asyncTaskDao.queryBuilder();
                    qb.where().like("title", sqlEscapeString(keyword)+"%");
                    qb = orderBy(qb);
                    List<AudioTag> tmpList = qb.query();
                    int i=0;
                    for(AudioTag met: tmpList) {
                        met.setResultType(SearchCriteria.RESULT_TYPE.TOP_RESULT);
                        list.add(met);
                        if(i++ > 5) break;
                    }

                    // artist
                    GenericRawResults<String[]> rawResults =
                            asyncTaskDao.queryRaw("SELECT DISTINCT artist FROM MediaItem where artist like '%"+sqlEscapeString(keyword)+"%' order by artist");
                    Map<String, String> found = new HashMap<>();
                    for (String[] resultColumns : rawResults) {
                        //String artist = resultColumns[0];
                        String[] artists = StringUtils.splitArtists(resultColumns[0]);
                        if (artists != null) {
                            for (String artist : artists) {
                                if((!found.containsKey(artist.toLowerCase())) && artist.toLowerCase().contains(keyword.toLowerCase())) {
                                    AudioTag met = new AudioTag();
                                    met.setMediaId("artist_" + artist);
                                    met.setTitle(artist);
                                    met.setArtist(artist);
                                    met.setResultType(SearchCriteria.RESULT_TYPE.ARTIST);
                                    list.add(met);
                                    found.put(artist.toLowerCase(), artist);
                                }
                            }
                        }
                    }

                    //album
                    rawResults =
                            asyncTaskDao.queryRaw("SELECT DISTINCT album FROM MediaItem where album like '%"+sqlEscapeString(keyword)+"%' order by album");
                    for (String[] resultColumns : rawResults) {
                        String album = resultColumns[0];
                        AudioTag met = new AudioTag();
                        met.setMediaId("album_"+album);
                        met.setTitle(album);
                        met.setAlbum(album);
                        met.setResultType(SearchCriteria.RESULT_TYPE.ALBUM);
                        list.add(met);
                    }

                    // tracks
                    qb = asyncTaskDao.queryBuilder();
                    qb.where().like("title", "%"+sqlEscapeString(keyword)+"%")
                            .or().like("artist", "%"+sqlEscapeString(keyword)+"%")
                            .or().like("mediaPath", "%"+sqlEscapeString(keyword)+"%");
                    qb = orderBy(qb);
                    tmpList = qb.query();
                    for(AudioTag met: tmpList) {
                        met.setResultType(SearchCriteria.RESULT_TYPE.TRACKS);
                        list.add(met);
                    }

                    return list;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null; // FIXME
        }

        private QueryBuilder orderByForAlbum(QueryBuilder qb) {
            return qb.orderByRaw("artist COLLATE NOCASE, CAST (\"+ track + \" AS INTEGER), title COLLATE NOCASE");
        }

        private QueryBuilder orderByForArtist(QueryBuilder qb) {
            return qb.orderByRaw("title COLLATE NOCASE, album COLLATE NOCASE, CAST (\" track + \" AS INTEGER), title COLLATE NOCASE");
        }

        @Override
        protected void onPreExecute() {
            delegate.asyncFinished(new ArrayList<AudioTag>());
        }

        @Override
        protected void onPostExecute(List<AudioTag> result) {
            delegate.asyncFinished(result);
        }
    } */
}