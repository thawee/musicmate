package apincer.android.mmate.dlna.content;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.Protocol;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.dlna.MediaServerConfiguration;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.dlna.transport.HCContentServer;
import apincer.android.mmate.dlna.transport.NettyStreamServerImpl;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;


/**
 * Super class for all content directory browsers.
 */
public abstract class ContentBrowser {
    private static final String TAG = "ContentBrowser";
    Context context;
    protected  String creator = "MusicMate";

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

    public String extractName(String id, ContentDirectoryIDs prefix) {
        String name = id.substring(prefix.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) { // ||
            //  "None".equalsIgnoreCase(name)) {
            name = null;
        }
        return name;
    }

    public String getUriString(ContentDirectory contentDirectory, MusicTag tag) {
        if(MediaServerSession.isTransCoded(tag)) {
            return "http://" + MediaServerSession.getIpAddress() + ":" + HCContentServer.SERVER_PORT + "/res/" + tag.getId() + "/file.raw";
        }
        return "http://" + MediaServerSession.getIpAddress() + ":" +HCContentServer.SERVER_PORT + "/res/" + tag.getId() + "/file." + tag.getFileFormat();
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, MusicTag tag) {
        return getAlbumArtUri(contentDirectory, tag.getAlbumUniqueKey());
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, String key) {
        String uri = key+".png";
        return URI.create("http://"
                + MediaServerSession.getIpAddress() + ":"
               // + HCContentServer.SERVER_PORT + "/album/" + uri);
                + MediaServerConfiguration.STREAM_SERVER_PORT + "/album/" + uri);
    }

    protected MusicTrack toMusicTrack(ContentDirectory contentDirectory, MusicTag tag,String folderId, String itemPrefix) {
        long id = tag.getId();
        String title = tag.getTitle();
        ProtocolInfo protocolInfo = getProtocolInfo(tag); //new MimeType("audio", tag.getAudioEncoding());
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
        }else  if(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping();
        }else  if(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping();
        }
        // file parameter only needed for media players which decide
        // the ability of playing a file by the file extension
        String uri = getUriString(contentDirectory, tag);
        URI albumArtUri = getAlbumArtUri(contentDirectory, tag);
        Res resource = new Res(protocolInfo, tag.getFileSize(), uri);
        resource.setDuration(tag.getAudioDurationAsString());
        MusicTrack musicTrack = new MusicTrack(itemPrefix + id, parentId,
                title, creator, tag.getAlbum(), tag.getArtist(), resource);
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

    private ProtocolInfo getProtocolInfo(MusicTag tag) {
        if(MediaServerSession.isTransCoded(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=44100;channels=2", "DLNA.ORG_PN=LPCM");
        }else if(MusicTagUtils.isAIFFile(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-aiff", ProtocolInfo.WILDCARD);
        }else  if(MusicTagUtils.isMPegFile(tag)) {
     //   }else  if(MusicTagUtils.isMPegFile(tag) || MusicTagUtils.isAACFile(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3");
           // return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=44100;channels=2", "DLNA.ORG_PN=LPCM");
           // return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-wav", ProtocolInfo.WILDCARD);
        }else if(MusicTagUtils.isFLACFile(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-flac", ProtocolInfo.WILDCARD);
        }else if(MusicTagUtils.isALACFile(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-mp4", ProtocolInfo.WILDCARD);
        }else if(MusicTagUtils.isMp4File(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mp4", "DLNA.ORG_PN=AAC_ISO");
        }else if(MusicTagUtils.isMp4File(tag)) {
          return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mp4", "DLNA.ORG_PN=AAC_ISO");
        }else  if(MusicTagUtils.isWavFile(tag)) {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-wav", ProtocolInfo.WILDCARD);
        }else {
            return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD);
        }
    }
}