package apincer.android.mmate.dlna;

import static java.util.Arrays.stream;

import android.content.Context;
import android.util.Log;

import org.jupnp.binding.annotations.UpnpAction;
import org.jupnp.binding.annotations.UpnpInputArgument;
import org.jupnp.binding.annotations.UpnpOutputArgument;
import org.jupnp.binding.annotations.UpnpService;
import org.jupnp.binding.annotations.UpnpServiceId;
import org.jupnp.binding.annotations.UpnpServiceType;
import org.jupnp.binding.annotations.UpnpStateVariable;
import org.jupnp.binding.annotations.UpnpStateVariables;
import org.jupnp.internal.compat.java.beans.PropertyChangeSupport;
import org.jupnp.model.types.ErrorCode;
import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.csv.CSV;
import org.jupnp.model.types.csv.CSVString;
import org.jupnp.support.contentdirectory.ContentDirectoryErrorCode;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import apincer.android.mmate.dlna.content.ContentBrowser;
import apincer.android.mmate.dlna.content.ContentDirectoryIDs;
import apincer.android.mmate.dlna.content.MusicAlbumFolderBrowser;
import apincer.android.mmate.dlna.content.MusicAlbumItemBrowser;
import apincer.android.mmate.dlna.content.MusicAlbumsFolderBrowser;
import apincer.android.mmate.dlna.content.MusicAllTitleItemBrowser;
import apincer.android.mmate.dlna.content.MusicAllTitlesFolderBrowser;
import apincer.android.mmate.dlna.content.MusicArtistFolderBrowser;
import apincer.android.mmate.dlna.content.MusicArtistItemBrowser;
import apincer.android.mmate.dlna.content.MusicArtistsFolderBrowser;
import apincer.android.mmate.dlna.content.MusicDownloadedFolderBrowser;
import apincer.android.mmate.dlna.content.MusicFolderBrowser;
import apincer.android.mmate.dlna.content.MusicGenreFolderBrowser;
import apincer.android.mmate.dlna.content.MusicGenreItemBrowser;
import apincer.android.mmate.dlna.content.MusicGenresFolderBrowser;

/**
 * a content directory which uses the content of the MediaStore in order to
 * provide it via upnp.
 *
 * @author Tobias Schoene (tobexyz)
 */
@UpnpService(serviceId = @UpnpServiceId("ContentDirectory"), serviceType = @UpnpServiceType(value = "ContentDirectory"))
@UpnpStateVariables({
        @UpnpStateVariable(name = "A_ARG_TYPE_ObjectID", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Result", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_BrowseFlag", sendEvents = false, datatype = "string", allowedValuesEnum = BrowseFlag.class),
        @UpnpStateVariable(name = "A_ARG_TYPE_Filter", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_SortCriteria", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Index", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Count", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(name = "A_ARG_TYPE_UpdateID", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(name = "A_ARG_TYPE_URI", sendEvents = false, datatype = "uri")})
public class ContentDirectory {
    private static final String TAG = "ContentDirectory";
    @UpnpStateVariable(sendEvents = false)
    final private CSV<String> searchCapabilities;
    @UpnpStateVariable(sendEvents = false)
    final private CSV<String> sortCapabilities;
    final private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(
            this);
    // test content only
    private final Map<String, DIDLObject> content = new HashMap<>();
    private final Context context;
    @UpnpStateVariable(defaultValue = "0", eventMaximumRateMilliseconds = 200)
    private final UnsignedIntegerFourBytes systemUpdateID = new UnsignedIntegerFourBytes(
            0);
    private final String ipAddress;

    public ContentDirectory(Context context, String ipAddress) {
        this.context = context;
        this.searchCapabilities = new CSVString();
        this.sortCapabilities = new CSVString();
        this.ipAddress = ipAddress;
    }

    public Context getContext() {
        return context;
    }

    /**
     *

    private void createContentDirectory() {
        StorageFolder rootContainer = new StorageFolder("0", "-1", "root",
                "mmate", 2, 907000L);
        rootContainer.setClazz(new DIDLObject.Class("object.container"));
        rootContainer.setRestricted(true);
        addContent(rootContainer.getId(), rootContainer);
        List<MusicTrack> musicTracks = createMusicTracks("1");
        MusicAlbum musicAlbum = new MusicAlbum("1", rootContainer, "Music",
                null, musicTracks.size(), musicTracks);
        musicAlbum.setClazz(new DIDLObject.Class("object.container"));
        musicAlbum.setRestricted(true);
        rootContainer.addContainer(musicAlbum);
        addContent(musicAlbum.getId(), musicAlbum);
    } */

    /*
    private List<MusicTrack> createMusicTracks(String parentId) {
        String album = "Music";
        String creator = "freetestdata.com";
        PersonWithRole artist = new PersonWithRole(creator, "");
        MimeType mimeType = new MimeType("audio", "mpeg");
        List<MusicTrack> result = new ArrayList<>();
        Res res = new Res(
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, mimeType.toString(), ProtocolInfo.WILDCARD),
                123456L,
                "00:01:27",
                26752L,
                "https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_2MB_MP3.mp3");
        res.setSampleFrequency(44100L);
        res.setNrAudioChannels(2L);
        MusicTrack musicTrack = new MusicTrack(
                "101",
                parentId,
                "Free_Test_Data_2MB_MP3",
                creator,
                album,
                artist,
                res);
        musicTrack.setRestricted(true);
        addContent(musicTrack.getId(), musicTrack);
        result.add(musicTrack);
        mimeType = new MimeType("audio", "ogg");
        musicTrack = new MusicTrack(
                "102",
                parentId,
                "Free_Test_Data_2MB_OGG",
                creator,
                album,
                artist,
                new Res(
                        new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, mimeType.toString(), ProtocolInfo.WILDCARD),
                        123456L,
                        "00:01:49",
                        8192L,
                        "https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_2MB_OGG.ogg"));
        musicTrack.setRestricted(true);
        addContent(musicTrack.getId(), musicTrack);
        result.add(musicTrack);

        return result;
    } */

    // *******************************************************************

    @UpnpAction(out = @UpnpOutputArgument(name = "SearchCaps"))
    public CSV<String> getSearchCapabilities() {
        return searchCapabilities;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "SortCaps"))
    public CSV<String> getSortCapabilities() {
        return sortCapabilities;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Id"))
    synchronized public UnsignedIntegerFourBytes getSystemUpdateID() {
        return systemUpdateID;
    }

    public PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    /**
     * Call this method after making changes to your content directory.
     * <p>
     * This will notify clients that their view of the content directory is
     * potentially outdated and has to be refreshed.
     * </p>
     */
    synchronized protected void changeSystemUpdateID() {
        Long oldUpdateID = getSystemUpdateID().getValue();
        systemUpdateID.increment(true);
        getPropertyChangeSupport().firePropertyChange("SystemUpdateID",
                oldUpdateID, getSystemUpdateID().getValue());
    }

    /**
     * add an object to the content of the directory
     *
     * @param id      of the object
     * @param content the object
     */
    private void addContent(String id, DIDLObject content) {
        this.content.put(id, content);
    }

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "Result", stateVariable = "A_ARG_TYPE_Result", getterName = "getResult"),
            @UpnpOutputArgument(name = "NumberReturned", stateVariable = "A_ARG_TYPE_Count", getterName = "getCount"),
            @UpnpOutputArgument(name = "TotalMatches", stateVariable = "A_ARG_TYPE_Count", getterName = "getTotalMatches"),
            @UpnpOutputArgument(name = "UpdateID", stateVariable = "A_ARG_TYPE_UpdateID", getterName = "getContainerUpdateID")})
    public BrowseResult browse(
            @UpnpInputArgument(name = "ObjectID", aliases = "ContainerID") String objectId,
            @UpnpInputArgument(name = "BrowseFlag") String browseFlag,
            @UpnpInputArgument(name = "Filter") String filter,
            @UpnpInputArgument(name = "StartingIndex", stateVariable = "A_ARG_TYPE_Index") UnsignedIntegerFourBytes firstResult,
            @UpnpInputArgument(name = "RequestedCount", stateVariable = "A_ARG_TYPE_Count") UnsignedIntegerFourBytes maxResults,
            @UpnpInputArgument(name = "SortCriteria") String orderBy)
            throws ContentDirectoryException {

        SortCriterion[] orderByCriteria;
        try {
            orderByCriteria = SortCriterion.valueOf(orderBy);
        } catch (Exception ex) {
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.UNSUPPORTED_SORT_CRITERIA,
                    ex.toString());
        }

        try {
            return browse(objectId, BrowseFlag.valueOrNullOf(browseFlag),
                    filter, firstResult.getValue(), maxResults.getValue(),
                    orderByCriteria);
        } catch (ContentDirectoryException ex) {
            throw ex;
        } catch (Exception ex) {
            Log.d(TAG, "exception on browse", ex);
            throw new ContentDirectoryException(ErrorCode.ACTION_FAILED,
                    ex.toString());
        }
    }

    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter, long firstResult, long maxResults,
                               SortCriterion[] orderby) throws ContentDirectoryException {

        Log.d(TAG, "Browse: objectId: " + objectID
                + " browseFlag: " + browseFlag + " filter: " + filter
                + " firstResult: " + firstResult + " maxResults: " + maxResults
                + " orderby: " + stream(orderby).map(SortCriterion::toString).collect(Collectors.joining(",")));
        int childCount;
        DIDLObject didlObject;
        DIDLContent didl = new DIDLContent();
       /* if (isUsingTestContent()) {
            didlObject = content.get(objectID);
            if (didlObject == null) {
                // object not found return root
                didlObject = content.get("0");
            }
            if (browseFlag == BrowseFlag.METADATA) {
                didl.addObject(didlObject);
                childCount = 1;
            } else {
                if (didlObject instanceof Container) {
                    Container container = (Container) didlObject;
                    childCount = container.getChildCount();
                    List<DIDLObject> allChilds = new ArrayList<>();
                    allChilds.addAll(container.getItems());
                    allChilds.addAll(container.getContainers());
                    for (int i = 0; i < allChilds.size(); i++) {
                        if (i >= firstResult) {
                            if (allChilds.get(i) instanceof Item) {
                                didl.addItem((Item) allChilds.get(i));
                            } else {
                                didl.addContainer((Container) allChilds.get(i));
                            }
                        }
                    }

                } else {
                    didl.addObject(didlObject);
                    childCount = 1;
                }
            }

        } else { */
            childCount = 0;
            if (findBrowserFor(objectID) != null) {
                if (browseFlag == BrowseFlag.METADATA) {
                    didlObject = findBrowserFor(objectID).browseMeta(this, objectID, firstResult, maxResults, orderby);
                    didl.addObject(didlObject);
                    childCount = 1;
                } else {
                    List<DIDLObject> children = findBrowserFor(objectID).browseChildren(this, objectID, firstResult, maxResults, orderby);
                    for (DIDLObject child : children) {
                        didl.addObject(child);
                        childCount++;

                    }

                }
            }
     //   }
        BrowseResult result;
        try {
            // Generate output with nested items
            String didlXml = new DIDLParser().generate(didl, false);
            Log.d(TAG, "CDResponse: " + didlXml);
            result = new BrowseResult(didlXml, childCount, childCount);
        } catch (Exception e) {
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS.getCode(),
                    "Error while generating BrowseResult", e);
        }
        return result;

    }

    private ContentBrowser findBrowserFor(String objectID) {
        ContentBrowser result = null;
        if (objectID == null || objectID.equals("") || ContentDirectoryIDs.MUSIC_FOLDER.getId().equals(objectID)) {
        //    result = new RootFolderBrowser(getContext());
       // } else if (ContentDirectoryIDs.MUSIC_FOLDER.getId().equals(objectID)) {
            result = new MusicFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId().equals(objectID)) {
            result = new MusicGenresFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId().equals(objectID)) {
            result = new MusicAlbumsFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId().equals(objectID)) {
            result = new MusicArtistsFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId().equals(objectID)) {
            result = new MusicAllTitlesFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId())) {
            result = new MusicAlbumFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId())) {
            result = new MusicArtistFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId())) {
            result = new MusicGenreFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId())) {
            result = new MusicAllTitleItemBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId())) {
            result = new MusicGenreItemBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId())) {
            result = new MusicAlbumItemBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId())) {
            result = new MusicArtistItemBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId())) {
            result = new MusicDownloadedFolderBrowser(getContext());
        }

        return result;
    }


    public String formatDuration(String millisStr) {

        if (millisStr == null || millisStr.equals("")) {
            return String.format(Locale.US, "%02d:%02d:%02d", 0L, 0L, 0L);
        }
        String res;
        long duration = Long.parseLong(millisStr);
        long hours = TimeUnit.MILLISECONDS.toHours(duration)
                - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(duration));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
                - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                .toHours(duration));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration)
                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                .toMinutes(duration));

        res = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes,
                seconds);

        return res;
    }

    public String getIpAddress() {
        return ipAddress;
    }

}