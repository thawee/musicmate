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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

/**
 * Browser for a music genre folder.
 */
public class AlbumFolderBrowser extends AbstractContentBrowser {
    //this is album (by this is artist)
    private final Pattern pattern = Pattern.compile("(?i)(.*)\\s*\\(by\\s*(.*)\\)");
    public AlbumFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), myId, "mmate", getTotalMatches(
                contentDirectory, myId), null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().length());
        String album = "";
        String albumArtist = "";
        if(Constants.NONE.equalsIgnoreCase(name) ||
                Constants.UNKNOWN.equalsIgnoreCase(name)) {
            album = "";
            albumArtist = "";
        }else {
            // split album and albumArtist - album (by artist)
            Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                album = StringUtils.trimToEmpty(matcher.group(1));
                albumArtist = StringUtils.trimToEmpty(matcher.group(2));
            }else {
                album = name;
            }
        }
        return MusixMateApp.getInstance().getOrmLite().findByAlbumAndAlbumArtist(album, albumArtist, 0, 0).size();
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @SuppressLint("Range")
    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        List<MusicTrack> result = new ArrayList<>();
        String name = myId.substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().length());
        String album = "";
        String albumArtist = "";
        if(Constants.NONE.equalsIgnoreCase(name) ||
                Constants.UNKNOWN.equalsIgnoreCase(name)) {
            album = "";
            albumArtist = "";
        }else {
            // split album and albumArtist - album (by artist)
            Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                album = StringUtils.trimToEmpty(matcher.group(1));
                albumArtist = StringUtils.trimToEmpty(matcher.group(2));
            }else {
                album = name;
            }
        }

        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults);
       // int currentCount = 0;
        for(MusicTag tag: tags) {
           // if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId());
                result.add(musicTrack);
           // }
           // currentCount++;
        }

       // result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;

    }

}