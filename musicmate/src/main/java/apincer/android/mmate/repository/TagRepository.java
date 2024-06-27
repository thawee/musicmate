package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class TagRepository {
    private static final String TAG = "TagRepository";
    public static List<String> lossyAudioFormatList;

    TagRepository() {
        lossyAudioFormatList = new ArrayList<>();
        lossyAudioFormatList.add("MP3");
        lossyAudioFormatList.add("AAC");
        lossyAudioFormatList.add("OGG");
        lossyAudioFormatList.add("WMA");
    }

    private static String escapeString(String text) {
        return text.replace("'","''");
    }

    public static void saveTag(MusicTag tag) {
        if(StringUtils.isEmpty(tag.getUniqueKey())) {
            String keyword = escapeString(tag.getPath()+"_"+ tag.getAudioStartTime());
            tag.setUniqueKey(keyword);
        }

        String path = tag.getPath().toLowerCase();
        if(path.contains("/music/") && !path.contains("/telegram/")) {
            // if has alblum, use parent dir
            if(!StringUtils.isEmpty(tag.getAlbum())) {
                // use directory
                path = FileUtils.getParentName(path);
            }
        }
        tag.setAlbumUniqueKey(DigestUtils.md5Hex(path));

        if(StringUtils.isEmpty(tag.getGenre())) {
            tag.setGenre(Constants.NONE);
        }
        if(StringUtils.isEmpty(tag.getGrouping())) {
            tag.setGrouping(Constants.NONE);
        }
        if(StringUtils.isEmpty(tag.getArtist())) {
            tag.setArtist(Constants.EMPTY);
        }
        if(StringUtils.isEmpty(tag.getAlbumArtist())) {
            tag.setAlbumArtist(tag.getArtist());
        }

        MusixMateApp.getInstance().getOrmLite().save(tag);
    }

    public static void removeTag(MusicTag tag) {
        MusixMateApp.getInstance().getOrmLite().delete(tag);
    }

    public static void cleanMusicMate() {
        try {
            List<MusicTag> list =  MusixMateApp.getInstance().getOrmLite().findMySongs();
            for(int i=0; i<list.size();i++) {
                MusicTag mdata = list.get(i);
                String path = mdata.getPath();
                if(!FileRepository.isMediaFileExist(path) || mdata.getFileSize()==0.00) {
                    removeTag(mdata);
                //}else if(!mdata.getUniqueKey().equals(mdata.getPath()+"_"+ mdata.getAudioStartTime())) {
                    // old files
                //    removeTag(mdata);
                }
            }
        } catch (Exception e) {
            Log.e(TAG,"cleanMusicMate",e);
        }
    }

    public static List<String> getActualGenreList(Context context) {
        List<String> list = new ArrayList<>();

        List<String> names = MusixMateApp.getInstance().getOrmLite().getGenres();
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    if(!list.contains(StringUtils.EMPTY)) {
                        list.add(StringUtils.EMPTY);
                    }
                }else {
                    list.add(group);
                }
            }

        Collections.sort(list);
        return list;
    }

    public static List<String> getDefaultGenreList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getGenres();
       // if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
           // list.addAll(Arrays.asList(names));
        //}
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

    public static List<String> getActualGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getGrouping();
        for (String group:names) {
            if(StringUtils.isEmpty(group)) {
                if(!list.contains(StringUtils.EMPTY)) {
                    list.add(StringUtils.EMPTY);
                }
            }else {
                list.add(group);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static List<String> getArtistList() {
        List<String> list = MusixMateApp.getInstance().getOrmLite().getArtits();
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

    public static List<MusicTag> findByPath(String path) {
       return MusixMateApp.getInstance().getOrmLite().findByPath(path);
    }

    public static List<MusicTag> findMediaByTitle(String title) {
        return MusixMateApp.getInstance().getOrmLite().findByTitle(title);
    }

    public static List<MusicTag> findMediaTag(SearchCriteria criteria) {
        List<MusicTag> results = findMediaTagOrEmpty(criteria);
        if(results.isEmpty()) {
            // try again
            results =  findMediaTagOrEmpty(criteria);
        }
        return results;
    }

    private static List<MusicTag> findMediaTagOrEmpty(SearchCriteria criteria) {
        try {
            return findMediaTagByCriteria(criteria);
        }catch (Exception e) {
            // retry one more time
            return Collections.EMPTY_LIST;
        }
    }

    private static List<MusicTag> findMediaTagByCriteria(SearchCriteria criteria) {
        List<MusicTag> list = new ArrayList<>();
        if (criteria.isSearchMode()) {
            // search title only, limit 5 songs
            list = MusixMateApp.getInstance().getOrmLite().findByKeyword(criteria.getSearchText());
        }else if (criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                if (StringUtils.isEmpty(criteria.getKeyword()) || Constants.TITLE_ALL_SONGS.equals(StringUtils.trimToEmpty(criteria.getKeyword()))) {
                    list = MusixMateApp.getInstance().getOrmLite().findMySongs();
                } else if (Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs();
                } else if (Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyBrokenSongs();
                } else if (Constants.TITLE_NOT_DR.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyNoneDRSongs();
                } else if (Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
                } else if (Constants.TITLE_NO_COVERART.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findNoEmbedCoverArtSong();
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                list = MusixMateApp.getInstance().getOrmLite().findByPublisher(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                list = MusixMateApp.getInstance().getOrmLite().findByMediaQuality(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_DSD.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findDSDSongs();
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ && Constants.AUDIO_SQ_PCM_MQA.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findMQASongs();

            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIGH_QUALITY.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findHighQuality();
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findLosslessSong();
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ && Constants.TITLE_HIRES.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findHiRes();
            } else if (criteria.getType() == SearchCriteria.TYPE.GROUPING) {
                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = MusixMateApp.getInstance().getOrmLite().findByGrouping(val);
            } else if (criteria.getType() == SearchCriteria.TYPE.GENRE) {

                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = MusixMateApp.getInstance().getOrmLite().findByGenre(val);
          //  } else if (criteria.getType() == SearchCriteria.TYPE.SEARCH) {
                // search title only, limit 5 songs
          //      list = MusixMateApp.getInstance().getOrmLite().findByKeyword(criteria.getKeyword());
            } else {
                // default for MY_SONGS and others
                list = MusixMateApp.getInstance().getOrmLite().findMySongs();
            }
        return list;
    }

    /*
    public static void populateAudioTag(MusicTag md) {
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(md.getId());
        if(tag != null) {
            md.cloneFrom(tag);
        }
    }*/

    public static MusicTag getMusicTag(long id) {
        return MusixMateApp.getInstance().getOrmLite().findById(id);
    }

    public static List<MusicTag> getAllMusics() {
        return MusixMateApp.getInstance().getOrmLite().findMySongs();
    }

    public static List<String> getDefaultPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getPublishers();
        //if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
       // }
        String[] genres =  context.getResources().getStringArray(R.array.default_publisher);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public static List<String> getPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getPublishers();
        // if(names!=null) {
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
      //  }

        Collections.sort(list);
        return list;
    }

    public static boolean cleanOutdatedMusicTag(String mediaPath, long lastModified) {
        // clean all existing outdated tag in database
        List<MusicTag> tags = findByPath(mediaPath);
        if(tags ==null || tags.isEmpty()) return true;
        boolean toRead = false;
        for (MusicTag tag : tags) {
            if((lastModified < 0) || tag.getFileLastModified() < lastModified) {
                removeTag(tag);
                toRead = true;
            }
        }
        return toRead;
    }

    public static void load(MusicTag tag) {
        MusicTag newTag = MusixMateApp.getInstance().getOrmLite().findById(tag.getId());
        if(newTag !=null) {
            tag.cloneFrom(newTag);
        }
    }

    public static List<String> getArtistForGrouping(Context context, String grouping) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getArtistForGrouping(grouping);
        for (String group:names) {
            if(StringUtils.isEmpty(group)) {
                list.add(StringUtils.EMPTY);
            }else {
                list.add(group);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static List<MusicTag> getMusicTags(Context context, String grouping, String artist) {
        return MusixMateApp.getInstance().getOrmLite().findByGroupingAndArtist(grouping, artist);
    }

    /*
    public static long getTotalSongCont() {
        return MusixMateApp.getInstance().getOrmLite().getTotalSongCont();
    }*/

    public static long getMaxId() {
        return MusixMateApp.getInstance().getOrmLite().getMaxId();
    }

    public static long getMinId() {
        return MusixMateApp.getInstance().getOrmLite().getMinId();
    }

    public static List<MusicTag> getMusicTags(long idRange1, long idRange2) {
        return MusixMateApp.getInstance().getOrmLite().findByIdRanges(idRange1, idRange2);
    }

    public static MusicTag getMusicTagAlbumUniqueKey(String albumUniqueKey) {
        return MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
    }
}