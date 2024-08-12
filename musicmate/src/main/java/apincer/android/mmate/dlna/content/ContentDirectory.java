package apincer.android.mmate.dlna.content;

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

import apincer.android.mmate.dlna.MediaServerConfiguration;

/**
 * UPnP Content Directory Service
 * a content directory which uses the content of the MusicMate via upnp.
 *
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


    public ContentDirectory(Context context) {
        this.context = context;
        this.searchCapabilities = new CSVString();
        this.sortCapabilities = new CSVString();
    }

    public Context getContext() {
        return context;
    }

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
        if (objectID == null || objectID.isEmpty() || ContentDirectoryIDs.MUSIC_FOLDER.getId().equals(objectID)) {
        //    result = new RootFolderBrowser(getContext());
       // } else if (ContentDirectoryIDs.MUSIC_FOLDER.getId().equals(objectID)) {
            result = new LibraryBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_DIRS_FOLDER.getId().equals(objectID)) {
            result = new DIRsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_DIR_PREFIX.getId())) {
            result = new DIRFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId().equals(objectID)) {
            result = new GenresBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId().equals(objectID)) {
            result = new AlbumsBrowser(getContext());
       // } else if (ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId().equals(objectID)) {
       //     result = new ArtistsBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId().equals(objectID)) {
            result = new AllTitlesBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId())) {
            result = new AlbumFolderBrowser(getContext());
      //  } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId())) {
      //      result = new ArtistFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId())) {
            result = new GenreFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId())) {
            //result = new MusicAllTitleItemBrowser(getContext());
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId(),ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId())) {
        //   result = new MusicAlbumItemBrowser(getContext());
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId(),ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId());
       // } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId())) {
           // result = new MusicArtistItemBrowser(getContext());
      //      result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId(),ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId())) {
            result = new DownloadsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId())) {
            result = new GroupingsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId())) {
            result = new GroupingFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId(), ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId());
       // } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_DIR_PREFIX.getId())) {
       //     result = new DIRIFolderBrowser(getContext(), ContentDirectoryIDs.MUSIC_DIR_PREFIX.getId(), ContentDirectoryIDs.MUSIC_DIR_ITEM_PREFIX.getId());
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
}