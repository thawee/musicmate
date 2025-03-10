package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicAlbum;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.utils.StringUtils;

/**
 * Browser  for the music albums folder.
 */
public class AlbumsBrowser extends AbstractContentBrowser {
    //this is album (by this is artist)
   private final Pattern pattern = Pattern.compile("(?i)(.*)\\s*\\(by\\s*(.*)\\)");
   private static final String TAG = "AlbumsBrowser";
    public AlbumsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.albums), creator, getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return MusixMateApp.getInstance().getOrmLite().getAlbumAndArtistWithChildrenCount().size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> groupings = MusixMateApp.getInstance().getOrmLite().getAlbumAndArtistWithChildrenCount();
        for(MusicFolder group: groupings) {
            String name = group.getName();
            MusicAlbum musicAlbum = new MusicAlbum(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), group.getName(), creator, 0);
            musicAlbum.setChildCount((int)group.getChildCount());

            // set artist
            String albumArtist = "";
            // split album and albumArtist - album (by artist)
            Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                    albumArtist = StringUtils.trimToEmpty(matcher.group(2));
            }
            if(!StringUtils.isEmpty(albumArtist)) {
                musicAlbum.setArtists(new PersonWithRole[]{new PersonWithRole(albumArtist, "AlbumArtist")});
            }

            //set albumArt
            URI albumArtUri = getAlbumArtUri(contentDirectory, group.getUniqueKey());
            musicAlbum.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                    albumArtUri));

            result.add(musicAlbum);
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();

    }
}