package apincer.music.server.jupnp.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.StringUtils;

/**
 * Browser for a music genre folder.
 */
public class AlbumFolderBrowser extends AbstractContentBrowser {
    //this is album (by this is artist)
    private final Pattern pattern = Pattern.compile("(?i)(.*)\\s*\\(by\\s*(.*)\\)");
    public AlbumFolderBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
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
        return tagRepos.findByAlbumAndAlbumArtist(album, albumArtist, 0, 0).size();
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

        List<MusicTag> tags = tagRepos.findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults);
        for(MusicTag tag: tags) {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId());
                result.add(musicTrack);
        }

        return result;
    }
}