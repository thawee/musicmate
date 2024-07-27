package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;

public class DIRFolderBrowser extends ContentBrowser {
    private static final String TAG = "DIRFolderBrowser";
    public static final String ALL_SONGS = "All Songs";

    public DIRFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        String name = extractName(myId);
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_DIRS_FOLDER.getId(), name, "mmate", 0, null);
    }

    private String extractName(String myId) {
        int indx = myId.lastIndexOf("/");
        if(indx >0) {
            return myId.substring(indx+1);
        }
        return myId;
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        String id = myId.substring(ContentDirectoryIDs.MUSIC_DIR_PREFIX.getId().length());
        List<Container> result = new ArrayList<>();
        Map<String,Integer> count = new HashMap<>();
        if(!id.endsWith(ALL_SONGS)) {
            List<MusicTag> list = TagRepository.findInPath(id);
            List<String> dirs = new ArrayList<>();
            int idlength = id.length();
            for (MusicTag tag : list) {
                String path = tag.getPath();
                int idx = path.indexOf("/", idlength + 1); // for
                if (idx > idlength) {
                    String name = path.substring(idlength + 1, idx);
                    if (!dirs.contains(name)) {
                        dirs.add(name);
                    }
                    // count song
                    if(count.containsKey(name)) {
                        count.put(name, count.get(name) +1);
                    }else {
                        count.put(name, 1);
                    }
                }
            }
            if(!dirs.isEmpty()) {
                // if found sub directory, show all songs
                dirs.add(ALL_SONGS);
            }

            for (String dir : dirs) {
                String dirid = myId + "/" + dir;
                StorageFolder folder = new StorageFolder(dirid, myId, dir, "mmate", 0, null);
                if(dir.equals(ALL_SONGS)) {
                    folder.setChildCount(list.size());
                }else {
                    folder.setChildCount(count.get(dir));
                }
                result.add(folder);
            }
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }

    @SuppressLint("Range")
    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<MusicTrack> result = new ArrayList<>();
        String id = myId.substring(ContentDirectoryIDs.MUSIC_DIR_PREFIX.getId().length());

        boolean allSongs = id.endsWith(ALL_SONGS);
        if(allSongs) {
            id = id.substring(0, id.length()-ALL_SONGS.length()-1);
        }

        List<MusicTag> list = TagRepository.findInPath(id);
        int idlength = id.length();
        int currentCount = 0;
        for(MusicTag tag: list) {
            String path = tag.getPath();
            int idx = path.indexOf("/", idlength+1); // for
            if(allSongs || (idx <= idlength)) {
                // found music on t=current directory
                if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                    MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_DIR_ITEM_PREFIX.getId());
                    result.add(musicTrack);
                }
                if(!forceFullContent) currentCount++;
            }
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }
}