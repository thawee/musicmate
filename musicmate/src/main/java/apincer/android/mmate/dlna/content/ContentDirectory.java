package apincer.android.mmate.dlna.content;

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
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * UPnP Content Directory Service
 * A content directory which exposes the music content of MusicMate via UPnP/DLNA.
 * This class implements the standard ContentDirectory:1 service allowing DLNA clients
 * to browse and search through the media library.
 */
@UpnpService(
        serviceId = @UpnpServiceId("ContentDirectory"),
        serviceType = @UpnpServiceType(value = "ContentDirectory", version = 1))
@UpnpStateVariables({
        @UpnpStateVariable(name = "A_ARG_TYPE_ContainerID", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_ObjectID", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Result", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_SearchCriteria",sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_BrowseFlag", sendEvents = false, datatype = "string", allowedValuesEnum = BrowseFlag.class),
        @UpnpStateVariable(name = "A_ARG_TYPE_Filter", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_SortCriteria", sendEvents = false, datatype = "string"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Index", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(name = "A_ARG_TYPE_Count", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(name = "A_ARG_TYPE_UpdateID", sendEvents = false, datatype = "ui4"),
        @UpnpStateVariable(
                name = "A_ARG_Type_TransferID",
                sendEvents = false,
                datatype = "uri"),
        @UpnpStateVariable(
                name = "A_ARG_Type_TransferStatus",
                sendEvents = false,
                datatype = "string",
                allowedValuesEnum = TransferStatus.class),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_TransferLength",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_TransferTotal",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_TagValueList",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_URI",
                sendEvents = false,
                datatype = "uri"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_PosSecond",
                sendEvents = false,
                datatype = "ui4"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_CategoryType",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_RID",
                sendEvents = false,
                datatype = "string")
})
public class ContentDirectory {
    private static final String TAG = "ContentDirectory";
    private static final List<String> CAPS_SEARCH = List.of("dc:title", "upnp:artist", "upnp:album", "upnp:genre");
    private static final List<String> CAPS_SORT = List.of("dc:title", "upnp:artist");

    // Cache for browse results to improve performance
    private final Map<String, CachedBrowseResult> browseResultCache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private static final long CACHE_EXPIRATION_MS = 30000; // 30 seconds

    private final Context context;
    private final Map<String, Supplier<AbstractContentBrowser>> directBrowserRegistry = new HashMap<>();
    private final Map<String, Supplier<AbstractContentBrowser>> prefixBrowserRegistry = new HashMap<>();

    @UpnpStateVariable(sendEvents = false)
    final CSV<String> searchCapabilities;
    @UpnpStateVariable(sendEvents = false)
    final CSV<String> sortCapabilities;
    final private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(
            this);
    @UpnpStateVariable(
            sendEvents = true,
            defaultValue = "0",
            datatype = "ui4",
            eventMaximumRateMilliseconds = 200)
    private final UnsignedIntegerFourBytes systemUpdateID = new UnsignedIntegerFourBytes(
            0);

    /**
     * Creates a new ContentDirectory service
     *
     * @param context The Android application context
     */
    public ContentDirectory(Context context) {
        TimerTask systemUpdateIdTask = new TimerTask() {
            @Override
            public void run() {
                changeSystemUpdateID();
            }
        };
        systemUpdateIdTask.run();
        this.context = context;
        this.searchCapabilities = new CSVString();
        this.sortCapabilities = new CSVString();
        this.searchCapabilities.addAll(CAPS_SEARCH);
        this.sortCapabilities.addAll(CAPS_SORT);

        // Initialize browser registries
        initBrowserRegistries();
    }

    /**
     * Initialize the browser registries with direct matches and prefix-based matches
     */
    private void initBrowserRegistries() {
        // Direct matches
        directBrowserRegistry.put(null, () -> new LibraryBrowser(getContext()));
        directBrowserRegistry.put("", () -> new LibraryBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_FOLDER.getId(), () -> new LibraryBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), () -> new SourcesBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), () -> new GenresBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), () -> new AlbumsBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), () -> new ArtistsBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), () -> new GroupingsBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), () -> new CollectionsBrowser(getContext()));
        directBrowserRegistry.put(ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), () -> new ResolutionsBrowser(getContext()));

        // Prefix matches
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_SOURCE_PREFIX.getId(), () -> new SourceFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId(), () -> new AlbumFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId(), () -> new ArtistFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId(), () -> new GenreFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId(), ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId(), ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId(), ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId(), () -> new GroupingFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId(), ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId(), () -> new CollectionFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId(), ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId(), () -> new ResolutionFolderBrowser(getContext()));
        prefixBrowserRegistry.put(ContentDirectoryIDs.MUSIC_RESOLUTION_ITEM_PREFIX.getId(),
                () -> new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId(), ContentDirectoryIDs.MUSIC_RESOLUTION_ITEM_PREFIX.getId()));
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

        // Clear cache when content changes
        cacheLock.writeLock().lock();
        try {
            browseResultCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Handles UPnP browse requests from DLNA clients
     *
     * @param objectId The ID of the container or item to browse
     * @param browseFlag Whether to retrieve metadata of the object or list its children
     * @param filter Property filter string
     * @param firstResult Starting index for returned objects
     * @param maxResults Maximum number of objects to return
     * @param orderBy Sort criteria
     * @return The browse results containing matching objects in DIDL-Lite format
     * @throws ContentDirectoryException if the browse operation fails
     */
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
            Log.e(TAG, "Invalid sort criteria: " + orderBy, ex);
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.UNSUPPORTED_SORT_CRITERIA,
                    ex.toString());
        }

        try {
            return browse(objectId, BrowseFlag.valueOrNullOf(browseFlag),
                    filter, firstResult.getValue(), maxResults.getValue(),
                    orderByCriteria);
        } catch (ContentDirectoryException ex) {
            Log.e(TAG, "Browse failed for objectID: " + objectId + ", flag: " + browseFlag, ex);
            throw ex;
        } catch (Exception ex) {
            Log.e(TAG, "Browse failed for objectID: " + objectId + ", flag: " + browseFlag, ex);
            throw new ContentDirectoryException(ErrorCode.ACTION_FAILED,
                    ex.toString());
        }
    }

    /**
     * Handles UPnP search requests from DLNA clients
     *
     * @param containerId The ID of the container to search within
     * @param searchCriteria The search criteria
     * @param filter Property filter string
     * @param firstResult Starting index for returned objects
     * @param maxResults Maximum number of objects to return
     * @param orderBy Sort criteria
     * @return The search results containing matching objects in DIDL-Lite format
     * @throws ContentDirectoryException if the search operation fails
     */
    @UpnpAction(out = {
            @UpnpOutputArgument(name = "Result", stateVariable = "A_ARG_TYPE_Result", getterName = "getResult"),
            @UpnpOutputArgument(name = "NumberReturned", stateVariable = "A_ARG_TYPE_Count", getterName = "getCount"),
            @UpnpOutputArgument(name = "TotalMatches", stateVariable = "A_ARG_TYPE_Count", getterName = "getTotalMatches"),
            @UpnpOutputArgument(name = "UpdateID", stateVariable = "A_ARG_TYPE_UpdateID", getterName = "getContainerUpdateID")})
    public BrowseResult search(
            @UpnpInputArgument(name = "ContainerID", stateVariable = "A_ARG_TYPE_ContainerID") String containerId,
            @UpnpInputArgument(name = "SearchCriteria", stateVariable = "A_ARG_TYPE_SearchCriteria") String searchCriteria,
            @UpnpInputArgument(name = "Filter") String filter,
            @UpnpInputArgument(name = "StartingIndex", stateVariable = "A_ARG_TYPE_Index") UnsignedIntegerFourBytes firstResult,
            @UpnpInputArgument(name = "RequestedCount", stateVariable = "A_ARG_TYPE_Count") UnsignedIntegerFourBytes maxResults,
            @UpnpInputArgument(name = "SortCriteria") String orderBy)
            throws ContentDirectoryException {

        // This is a placeholder implementation - in a full implementation
        // you would implement proper search logic based on the criteria
        Log.d(TAG, "Search request received - containerId: " + containerId + ", criteria: " + searchCriteria);

        // TODO Convert search criteria into a filter that can be used by a browser
        // For now, return an empty result
        return new BrowseResult("", 0, 0);
    }


    /**
     * Browses the content directory.
     *
     * @param objectID The ID of the object to browse
     * @param browseFlag Whether to browse metadata or children
     * @param filter Filter string limiting which properties should be returned
     * @param firstResult The starting index (0-based) of the first object to return
     * @param maxResults The maximum number of objects to return
     * @param orderby Array of sort criteria
     * @return A BrowseResult containing the requested objects
     * @throws ContentDirectoryException If the browse operation cannot be completed
     */
    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter, long firstResult, long maxResults,
                               SortCriterion[] orderby) throws ContentDirectoryException {
        // Check if we can use a cached result
        String cacheKey = generateCacheKey(objectID, browseFlag, filter, firstResult, maxResults, orderby);

        cacheLock.readLock().lock();
        try {
            CachedBrowseResult cachedResult = browseResultCache.get(cacheKey);
            if (cachedResult != null && !cachedResult.isExpired()) {
                Log.d(TAG, "Cache hit for browse request: " + cacheKey);
                return cachedResult.getBrowseResult();
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        // Cache miss or expired, fetch the result
        int childCount =0;
        int totalMatches;
        DIDLObject didlObject;
        DIDLContent didl = new DIDLContent();
        AbstractContentBrowser contentBrowser = findBrowserFor(objectID);
        if (contentBrowser != null) {
                if (browseFlag == BrowseFlag.METADATA) {
                    didlObject = contentBrowser.browseMeta(this, objectID, firstResult, maxResults, orderby);
                    didl.addObject(didlObject);
                    childCount = 1;
                    totalMatches = 1;
                } else {
                    List<DIDLObject> children = contentBrowser.browseChildren(this, objectID, firstResult, maxResults, orderby);
                    for (DIDLObject child : children) {
                        didl.addObject(child);
                        childCount++;
                    }
                    totalMatches = contentBrowser.getTotalMatches(this, objectID);
                }

            try {
                // Generate output with nested items
                String didlXml = new DIDLParser().generate(didl, false);
                BrowseResult result = new BrowseResult(didlXml, childCount, totalMatches);

                // Cache the result
                cacheLock.writeLock().lock();
                try {
                    browseResultCache.put(cacheKey, new CachedBrowseResult(result));
                } finally {
                    cacheLock.writeLock().unlock();
                }

                return result;
            } catch (Exception e) {
                Log.e(TAG, "Error generating DIDL-Lite XML", e);
                throw new ContentDirectoryException(
                        ContentDirectoryErrorCode.CANNOT_PROCESS.getCode(),
                        "Error while generating BrowseResult", e);
            }
        } else {
            Log.w(TAG, "No browser found for objectID: " + objectID);
            return new BrowseResult("", 0, 0);
        }
    }

    /**
     * Finds the appropriate browser for a given object ID
     *
     * @param objectID The object ID to find a browser for
     * @return The appropriate content browser, or null if none found
     */
    private AbstractContentBrowser findBrowserFor(String objectID) {
        // Check direct matches first
        Supplier<AbstractContentBrowser> directSupplier = directBrowserRegistry.get(objectID);
        if (directSupplier != null) {
            return directSupplier.get();
        }

        // Check prefix matches
        for (Map.Entry<String, Supplier<AbstractContentBrowser>> entry : prefixBrowserRegistry.entrySet()) {
            if (objectID != null && objectID.startsWith(entry.getKey())) {
                return entry.getValue().get();
            }
        }

        // Fallback to root browser
        if (objectID == null || objectID.isEmpty()) {
            return new LibraryBrowser(getContext());
        }

        return null;
    }

    private AbstractContentBrowser findBrowserForOld(String objectID) {
        AbstractContentBrowser result = null;
        if (objectID == null || objectID.isEmpty() || ContentDirectoryIDs.MUSIC_FOLDER.getId().equals(objectID)) {
            result = new LibraryBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId().equals(objectID)) {
            result = new SourcesBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_SOURCE_PREFIX.getId())) {
            result = new SourceFolderBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId().equals(objectID)) {
            result = new GenresBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId().equals(objectID)) {
            result = new AlbumsBrowser(getContext());
        } else if (ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId().equals(objectID)) {
            result = new ArtistsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId())) {
            result = new AlbumFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId())) {
            result = new ArtistFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId())) {
            result = new GenreFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId(),ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId(),ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId(),ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId())) {
            result = new GroupingsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId())) {
            result = new GroupingFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId(), ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId());
        }else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId())) {
            result = new CollectionsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId())) {
            result = new CollectionFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId(), ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId());
        }else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId())) {
            result = new ResolutionsBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId())) {
            result = new ResolutionFolderBrowser(getContext());
        } else if (objectID.startsWith(ContentDirectoryIDs.MUSIC_RESOLUTION_ITEM_PREFIX.getId())) {
            result = new MusicItemBrowser(getContext(), ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId(), ContentDirectoryIDs.MUSIC_RESOLUTION_ITEM_PREFIX.getId());
        }

        return result;
    }

    /**
     * Generates a cache key for a browse request
     */
    private String generateCacheKey(String objectID, BrowseFlag browseFlag, String filter,
                                    long firstResult, long maxResults, SortCriterion[] orderby) {
        StringBuilder sb = new StringBuilder();
        sb.append(objectID != null ? objectID : "root").append('|')
                .append(browseFlag).append('|')
                .append(firstResult).append('|')
                .append(maxResults).append('|');

        if (orderby != null) {
            for (SortCriterion criterion : orderby) {
                sb.append(criterion.toString()).append(',');
            }
        }

        return sb.toString();
    }

    /**
     * Wrapper class for cached browse results
     */
    private static class CachedBrowseResult {
        private final BrowseResult browseResult;
        private final long timestamp;

        public CachedBrowseResult(BrowseResult browseResult) {
            this.browseResult = browseResult;
            this.timestamp = System.currentTimeMillis();
        }

        public BrowseResult getBrowseResult() {
            return browseResult;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }

}