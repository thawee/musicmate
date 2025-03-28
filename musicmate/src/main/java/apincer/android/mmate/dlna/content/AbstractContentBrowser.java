package apincer.android.mmate.dlna.content;

import static apincer.android.mmate.dlna.MediaServerConfiguration.CONTENT_SERVER_PORT;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.Person;
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
import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;


/**
 * Super class for all content directory browsers.
 */
public abstract class AbstractContentBrowser {
    private static final String TAG = "ContentBrowser";
    Context context;
    protected  String creator = "MusicMate";

    protected AbstractContentBrowser(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public abstract DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<Container> browseContainer(
            ContentDirectory content, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<? extends Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);
    public abstract Integer getTotalMatches(ContentDirectory contentDirectory, String myId);
    public List<DIDLObject> browseChildren(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<DIDLObject> result = new ArrayList<>();
        result.addAll(browseContainer(contentDirectory, myId, firstResult, maxResults, orderby));
        result.addAll(browseItem(contentDirectory, myId, firstResult, maxResults, orderby));
        return result;
    }

    public String extractName(String id, ContentDirectoryIDs prefix) {
        String name = id.substring(prefix.getId().length());
        return name;
    }

    public String getUriString(ContentDirectory contentDirectory, MusicTag tag) {
        return "http://" + StreamServerImpl.streamServerHost + ":" +CONTENT_SERVER_PORT + "/res/" + tag.getId() + "/file." + tag.getFileType();

     //   return "http://" + MediaServerSession.getIpAddress() + ":" +MediaServerConfiguration.STREAM_SERVER_PORT + "/res/" + tag.getId() + "/file." + tag.getFileFormat();
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, MusicTag tag) {
        return getAlbumArtUri(contentDirectory, tag.getAlbumUniqueKey());
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, String key) {
        String uri = key+".png";
        return URI.create("http://"
                + StreamServerImpl.streamServerHost + ":"
               // + HCContentServer.SERVER_PORT + "/album/" + uri);
                + MediaServerConfiguration.UPNP_SERVER_PORT + "/coverart/" + uri);
    }

    protected MusicTrack buildMusicTrack(ContentDirectory contentDirectory, MusicTag tag,String folderId, String itemPrefix) {
        long id = tag.getId();
        String title = tag.getTitle();
        String parentId = buildParentId(tag, folderId);
        // file parameter only needed for media players which decide
        // the ability of playing a file by the file extension

        // Create the resource (streaming URL) with technical metadata
        ProtocolInfo protocolInfo = getProtocolInfo(tag); //new MimeType("audio", tag.getAudioEncoding());
        String uri = getUriString(contentDirectory, tag);
        Res resource = new Res(protocolInfo, tag.getFileSize(), uri);
        // Add technical metadata for streaming optimization
        resource.setBitrate(tag.getAudioBitRate());
        resource.setBitsPerSample((long) tag.getAudioBitsDepth());
        resource.setSampleFrequency(tag.getAudioSampleRate());
        resource.setNrAudioChannels((long) MusicTagUtils.getChannels(tag));
        resource.setDuration(StringUtils.formatDuration(tag.getAudioDuration(), false));

        // Create the MusicTrack with required ID and title
        MusicTrack musicTrack = new MusicTrack(itemPrefix + id,
                parentId, // Parent container ID
                escapeXml(title), // Track title
                creator,
                escapeXml(StringUtils.trim(tag.getAlbum(),"-")), // Album name
                escapeXml(StringUtils.trim(tag.getArtist(),"-")), // Artist name
                resource);

        // Add album art - critical for mConnectHD display
        URI albumArtUri = getAlbumArtUri(contentDirectory, tag);
        DIDLObject.Property<URI> albumArtProperty = new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                albumArtUri);
        musicTrack.replaceFirstProperty(albumArtProperty);

        // Add track number
        int trackNum = StringUtils.extractTrackNumber(tag.getTrack());
        if (trackNum > 0) {
            musicTrack.setOriginalTrackNumber(trackNum);
        }

        // Add technical metadata for streaming optimization
       // if(!isEmpty(tag.getAlbumArtist())) {
        //    musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(tag.getAlbumArtist(), "AlbumArtist")});
        //}
       // DIDLObject.Property.

        if(!isEmpty(tag.getGenre())) {
            musicTrack.setGenres(tag.getGenre().split(",", -1));
        }

        // Add optional extended properties for better display
        if(!isEmpty(tag.getComposer())) {
            musicTrack.addProperty(new DIDLObject.Property.DC.CONTRIBUTOR(new Person(tag.getComposer())));
        }

        if (!isEmpty(tag.getYear())) {
            musicTrack.addProperty(new DIDLObject.Property.DC.DATE(tag.getYear() + "-01-01"));
        }

        // Add high-resolution audio indicator for compatible players
        if (tag.getAudioSampleRate() > 44100 || tag.getAudioBitsDepth() > 16) {
            musicTrack.addProperty(new DIDLObject.Property.UPNP.ARTIST_DISCO_URI(
                    URI.create("http://" + StreamServerImpl.streamServerHost + ":" +
                            MediaServerConfiguration.UPNP_SERVER_PORT + "/hires_badge")));
        }

        return musicTrack;
    }

    private String buildParentId(MusicTag tag, String folderId) {
        String parentId;
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
        }else {
            parentId =  folderId;
        }
        return  parentId;
    }

    private ProtocolInfo getProtocolInfo(MusicTag tag) {
        // DLNA parameters for streaming optimization - enabling seeking and other features
        String formatSuffix = ";DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";

        // AIFF files
        if(MusicTagUtils.isAIFFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/x-aiff",
                    "DLNA.ORG_PN=AIFF" + formatSuffix
            );
        }

        // MP3 files
        else if(MusicTagUtils.isMPegFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/mpeg",
                    "DLNA.ORG_PN=MP3" + formatSuffix
            );
        }

        // FLAC files - RoPieeeXL has excellent FLAC support
        else if(MusicTagUtils.isFLACFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/flac",  // Updated from audio/x-flac for better compatibility
                    "DLNA.ORG_PN=FLAC" + formatSuffix
            );
        }

        // ALAC files (Apple Lossless)
        else if(MusicTagUtils.isALACFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/mp4", // Changed from audio/x-mp4
                    "DLNA.ORG_PN=AAC_ISO_320" + formatSuffix // Best match for ALAC in DLNA
            );
        }

        // MP4/AAC files
        else if(MusicTagUtils.isMp4File(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/mp4",
                    "DLNA.ORG_PN=AAC_ISO" + formatSuffix
            );
        }

        // WAV files
        else if(MusicTagUtils.isWavFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/wav",  // Changed from audio/x-wav for better compatibility
                    "DLNA.ORG_PN=WAV" + formatSuffix
            );
        }

        // AAC files - added explicit handling
        else if(MusicTagUtils.isAACFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/aac",
                    "DLNA.ORG_PN=AAC_ADTS" + formatSuffix
            );
        }

        // DSD/DSF high-res audio - added for completeness
        else if(MusicTagUtils.isDSDFile(tag)) {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/x-dsd",
                    "DLNA.ORG_PN=DSF" + formatSuffix
            );
        }

        // Default fallback for unknown types
        else {
            return new ProtocolInfo(
                    Protocol.HTTP_GET,
                    ProtocolInfo.WILDCARD,
                    "audio/" + MimeType.WILDCARD,
                    formatSuffix.substring(1)
            );
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return //input.replace("&", "&amp;")
                input.replace("<", "&lt;")
                .replace(">", "&gt;");
                //.replace("\"", "&quot;");
                //.replace("'", "&apos;");
    }
}