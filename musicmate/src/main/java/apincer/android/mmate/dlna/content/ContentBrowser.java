package apincer.android.mmate.dlna.content;
import android.content.Context;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;


/**
 * Super class for all content directory browsers.
 *
 * @author openbit (Tobias Schoene)
 */
public abstract class ContentBrowser {
    private static final String TAG = "ContentBrowser";
    Context context;

    protected ContentBrowser(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }


    public abstract DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<Container> browseContainer(
            ContentDirectory content, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<? extends Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public List<DIDLObject> browseChildren(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<DIDLObject> result = new ArrayList<>();
        result.addAll(browseContainer(contentDirectory, myId, firstResult, maxResults, orderby));
        result.addAll(browseItem(contentDirectory, myId, firstResult, maxResults, orderby));
        return result;
    }

    public String getUriString(ContentDirectory contentDirectory, String id, MimeType mimeType) {
        String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.toString());
        if (fileExtension == null) {
            Log.d(getClass().getName(), "Can't lookup file extension from mimetype: " + mimeType);
            //try subtype
            fileExtension = mimeType.getSubtype();

        }
        return "http://" + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/res/" + id + "/file." + fileExtension;
    }

    public String getUriString(ContentDirectory contentDirectory, MusicTag tag) {
        return "http://" + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/res/" + tag.getId() + "/file." + tag.getFileFormat();
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, MusicTag tag) {
       /* String absPath = tag.getPath().toLowerCase();
        if(absPath.contains("/music/") && !absPath.contains("/telegram/")) {
            // if has alblum, use parent dir
            if(!StringUtils.isEmpty(tag.getAlbum())) {
                // use directory
                absPath = FileUtils.getParentName(absPath);
            }
        }
        String uri = DigestUtils.md5Hex(absPath)+".png";

        return URI.create("http://"
                + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/album/" + uri); */
        return getAlbumArtUri(contentDirectory, tag.getAlbumUniqueKey());
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, String key) {
        String uri = key+".png";
        return URI.create("http://"
                + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/album/" + uri);
    }

    protected MusicTrack toMusicTrack(ContentDirectory contentDirectory, MusicTag tag,String folderId, String itemPrefix) {
        long id = tag.getId();
        String title = tag.getTitle();
        MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
        String parentId = folderId;
        // album, artist, genre, grouping
        if(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getAlbum();
        }else  if(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getArtist();
        }else  if(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGenre();
        }else  if(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping();
        }
        // file parameter only needed for media players which decide
        // the ability of playing a file by the file extension
        String uri = getUriString(contentDirectory, tag);
        URI albumArtUri = getAlbumArtUri(contentDirectory, tag);
        Res resource = new Res(mimeType, tag.getFileSize(), uri);
        resource.setDuration(tag.getAudioDurationAsString());
        MusicTrack musicTrack = new MusicTrack(itemPrefix + id, parentId,
                title, "", tag.getAlbum(), tag.getArtist(), resource);
        musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                albumArtUri));
        musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(tag.getArtist(), "AlbumArtist")});
        resource.setBitrate(tag.getAudioBitRate());
        musicTrack.setGenres(tag.getGenre().split(",", -1));
        //musicTrack.setOriginalTrackNumber(tag.getTrack());

       // Log.d(TAG, "MusicTrack: " + id + " Title: "
       //         + title + " Uri: " + uri);
        return musicTrack;
    }
}