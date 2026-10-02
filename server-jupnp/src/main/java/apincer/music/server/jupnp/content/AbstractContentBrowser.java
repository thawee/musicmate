package apincer.music.server.jupnp.content;

import static apincer.music.core.server.BaseServer.CONTEXT_PATH_COVERART;
import static apincer.music.core.server.BaseServer.CONTEXT_PATH_MUSIC;
import static apincer.music.core.server.BaseServer.WEB_SERVER_PORT;
import static apincer.music.core.utils.StringUtils.isEmpty;

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

import apincer.music.core.model.Track;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.NetworkUtils;
import apincer.music.core.utils.TagUtils;
import apincer.music.core.utils.StringUtils;


/**
 * Super class for all content directory browsers.
 */
public abstract class AbstractContentBrowser {
    private static final String TAG = "ContentBrowser";
    protected Context context;
    protected TagRepository tagRepos;
    protected String creator = "MusicMate";

    protected AbstractContentBrowser(Context context, TagRepository tagRepos) {
        this.context = context;
        this.tagRepos = tagRepos;
    }

    public Context getContext() {
        return context;
    }

    public abstract DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<Container> browseContainer(
            ContentDirectory content, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<? extends Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);
    public abstract Integer getTotalMatches(ContentDirectory contentDirectory, String myId);
    /**
     * Children of {@code myId}, paged by StartingIndex and RequestedCount. Browsers that build
     * their lists in memory return everything and are paged here; ones that page in the
     * database say so with {@link #pagesChildren()}.
     */
    public List<DIDLObject> browseChildren(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<DIDLObject> result = new ArrayList<>();
        java.util.Comparator<DIDLObject> sort = BrowseSort.didl(orderby);
        boolean hiding = clientProfile.adapts();
        // A sorted or filtered request needs every child before paging, unless the browser does it itself
        if (pagesChildren() && (sort == null || sortsChildren()) && (!hiding || filtersChildren())) {
            result.addAll(browseContainer(contentDirectory, myId, firstResult, maxResults, orderby));
            result.addAll(browseItem(contentDirectory, myId, firstResult, maxResults, orderby));
            return result;
        }
        result.addAll(browseContainer(contentDirectory, myId, 0, 0, orderby));
        result.addAll(browseItem(contentDirectory, myId, 0, 0, orderby));
        if (hiding && result.removeIf(AbstractContentBrowser::isDsdItem)) {
            filteredTotal = result.size();
        }
        if (sort != null) result.sort(sort);
        return page(result, firstResult, maxResults);
    }

    /** The requesting client's format profile; set per request by ContentDirectory. */
    protected apincer.music.core.server.ClientFormatProfile clientProfile =
            apincer.music.core.server.ClientFormatProfile.DEFAULT;
    private Integer filteredTotal;

    void setClientProfile(apincer.music.core.server.ClientFormatProfile profile) {
        this.clientProfile = profile != null ? profile : apincer.music.core.server.ClientFormatProfile.DEFAULT;
    }

    /** The total after hiding formats this client cannot play, when browseChildren filtered; else null. */
    Integer filteredTotal() {
        return filteredTotal;
    }

    /** DSD items carry audio/x-dsd in their res protocolInfo. */
    private static boolean isDsdItem(DIDLObject object) {
        if (!(object instanceof Item)) return false;
        for (Res res : object.getResources()) {
            if (res.getProtocolInfo() != null && "audio/x-dsd".equals(res.getProtocolInfo().getContentFormat())) return true;
        }
        return false;
    }

    /** True when a browser that pages itself also hides the client's unplayable formats (before paging). */
    protected boolean filtersChildren() {
        return false;
    }

    /** True when a browser that pages itself also applies SortCriteria (before paging). */
    protected boolean sortsChildren() {
        return false;
    }

    /** True when browseContainer and browseItem already apply StartingIndex and RequestedCount. */
    protected boolean pagesChildren() {
        return false;
    }

    /** UPnP paging: RequestedCount 0 means all remaining; a start past the end gives an empty page. */
    static <T> List<T> page(List<T> all, long firstResult, long maxResults) {
        int from = (int) Math.min(Math.max(firstResult, 0), all.size());
        int to = maxResults <= 0 ? all.size() : (int) Math.min(from + maxResults, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    public String extractName(String id, ContentDirectoryIDs prefix) {
        String name = id.substring(prefix.getId().length());
        return name;
    }

    /** dc:date as an ISO date: "2023-06-09" kept, "1959" -> "1959-01-01", anything else null. */
    static String didlDate(String year) {
        if (year == null) return null;
        String y = year.trim();
        if (y.matches("\\d{4}-\\d{2}-\\d{2}")) return y;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{4})\\b").matcher(y);
        return m.find() ? m.group(1) + "-01-01" : null;
    }

    /** res@bitrate is bytes per second; tags hold bits per second (or kbps in older entries). */
    static long didlBitrate(long bitrate) {
        if (bitrate <= 0) return 0;
        return bitrate < 10_000 ? bitrate * 1000 / 8 : bitrate / 8;
    }

    /**
     * This server's address, looked up once per browser. Browsers are created per Browse
     * request; getIpAddress() scans every network interface, and calling it two or three times
     * per track made "All Songs" (8,000+ tracks) take about 16 s.
     */
    protected String serverHost() {
        if (serverHost == null) serverHost = NetworkUtils.getIpAddress();
        return serverHost;
    }
    private String serverHost;

    /** The stream URL with another file extension (the server identifies tracks by id only). */
    public String getUriString(ContentDirectory contentDirectory, Track tag, String extension) {
        return "http://" + serverHost() + ":" + WEB_SERVER_PORT + CONTEXT_PATH_MUSIC + tag.getId() + "/file." + extension;
    }

    public String getUriString(ContentDirectory contentDirectory, Track tag) {
        return "http://" + serverHost() + ":" +WEB_SERVER_PORT +  CONTEXT_PATH_MUSIC + tag.getId() + "/file." + tag.getFileType();
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, Track tag) {
        return getAlbumArtUri(contentDirectory, tag.getAlbumArtFilename());
    }

    /**
     * Two albumArtURI entries: the full-size cover first and untagged (most renderers use the
     * first), then a 160x160 JPEG tagged dlna:profileID="JPEG_TN" for renderers that only show
     * art carrying a DLNA image profile. The thumbnail is made on the phone when first requested.
     */
    protected void addAlbumArt(ContentDirectory contentDirectory, DIDLObject object, String name) {
        object.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(getAlbumArtUri(contentDirectory, name)));
        DIDLObject.Property.DLNA.PROFILE_ID jpegTn = new DIDLObject.Property.DLNA.PROFILE_ID(
                new org.jupnp.support.model.DIDLAttribute(DIDLObject.Property.DLNA.NAMESPACE.URI, "dlna", "JPEG_TN"));
        object.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                getAlbumArtUri(contentDirectory, apincer.music.core.server.BaseServer.THUMBNAIL_KEY_PREFIX + name),
                java.util.List.of(jpegTn)));
    }

    protected URI getAlbumArtUri(ContentDirectory contentDirectory, String name) {
        //String uri = key+".png";
        return URI.create("http://"
                + serverHost() + ":"
                + WEB_SERVER_PORT + CONTEXT_PATH_COVERART  + name);
    }

    protected MusicTrack buildMusicTrack(ContentDirectory contentDirectory, Track tag, String folderId, String itemPrefix) {
        long id = tag.getId();
        String title = tag.getTitle();
        String parentId = buildParentId(tag, folderId);
        // file parameter only needed for media players which decide
        // the ability of playing a file by the file extension

        // Create the resource (streaming URL) with technical metadata
        // A TV that cannot play FLAC is offered the same track as WAV, which the server converts
        boolean asWav = clientProfile.convertsToPcm(tag) && TagUtils.isFLACFile(tag);
        ProtocolInfo protocolInfo = asWav
                ? new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wav",
                        apincer.music.server.jupnp.transport.DLNAHeaderHelper.getConvertedPcmContentFeatures())
                : getProtocolInfo(tag);
        String uri = asWav ? getUriString(contentDirectory, tag, "wav") : getUriString(contentDirectory, tag);
        int pcmFrameBytes = TagUtils.getChannels(tag) * ((tag.getAudioBitsDepth() + 7) / 8);
        long size = asWav
                ? 44 + (long) (tag.getAudioDuration() * tag.getAudioSampleRate()) * pcmFrameBytes // estimate; HTTP has the exact length
                : tag.getFileSize();
        Res resource = new Res(protocolInfo, size, uri);
        // Add technical metadata for streaming optimization
        resource.setBitrate(asWav ? (long) tag.getAudioSampleRate() * pcmFrameBytes : didlBitrate(tag.getAudioBitRate()));
        resource.setBitsPerSample((long) tag.getAudioBitsDepth());
        resource.setSampleFrequency(tag.getAudioSampleRate());
        resource.setNrAudioChannels((long) TagUtils.getChannels(tag));
        // UPnP res@duration is H+:MM:SS[.F+]; "04:23" was rejected or ignored by renderers
        resource.setDuration(apincer.music.server.jupnp.MediaServerHubImpl.formatDurationForDidl(
                (long) (tag.getAudioDuration() * 1000)));

        // Create the MusicTrack with required ID and title
        String artist = StringUtils.trim(tag.getArtist(),"-");
        MusicTrack musicTrack = new MusicTrack(itemPrefix + id,
                parentId, // Parent container ID
                title, // Track title (DIDLParser escapes XML)
                // dc:creator: clients show it as the artist; it used to be "MusicMate"
                isEmpty(artist) ? creator : artist,
                StringUtils.trim(tag.getAlbum(),"-"), // Album name
                artist, // Artist name
                resource);

        // Add album art - critical for mConnectHD display
        addAlbumArt(contentDirectory, musicTrack, tag.getAlbumArtFilename());

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

        String date = didlDate(tag.getYear());
        if (date != null) {
            musicTrack.addProperty(new DIDLObject.Property.DC.DATE(date));
        }

        return musicTrack;
    }

    private String buildParentId(Track tag, String folderId) {
        String parentId;
        // album, artist, genre, grouping
        if(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getAlbum();
        }else  if(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getArtist();
        }else  if(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGenre();
     /*   }else  if(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping();
        }else  if(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping();
        }else  if(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId().equalsIgnoreCase(folderId)) {
            parentId = folderId + tag.getGrouping(); */
        }else {
            parentId =  folderId;
        }
        return  parentId;
    }

    private ProtocolInfo getProtocolInfo(Track tag) {
        String mime;
        if (TagUtils.isAIFFile(tag)) mime = "audio/x-aiff";
        else if (TagUtils.isMPegFile(tag)) mime = "audio/mpeg";
        else if (TagUtils.isFLACFile(tag)) mime = "audio/flac";
        else if (TagUtils.isWavFile(tag)) mime = "audio/wav";
        else if (TagUtils.isDSDFile(tag)) mime = "audio/x-dsd";
        else if (TagUtils.isAACFile(tag) && tag.getPath() != null && tag.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".aac")) mime = "audio/aac";
        else if (TagUtils.isMp4File(tag)) mime = "audio/mp4"; // AAC or ALAC in an MP4 container
        else mime = "audio/" + MimeType.WILDCARD;
        // Same DLNA parameters as the stream's contentFeatures.dlna.org header
        return new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, mime,
                apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures(tag));
    }
}