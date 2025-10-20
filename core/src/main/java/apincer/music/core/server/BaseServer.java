package apincer.music.core.server;

import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import apincer.music.core.codec.MusicAnalyser;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.QueueItem;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.TrackInfo;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.RemoteWebPlayer;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.MusicInfoService;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.service.spi.MusicMateServiceBinder;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.music.core.utils.NetworkUtils;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;
import io.reactivex.rxjava3.functions.Consumer;

public class BaseServer {
    private static final String TAG = "BaseServer";
    public static final int UPNP_SERVER_PORT = 49152; // IANA-recommended range 49152-65535 for UPnP
    public static final int CONTENT_SERVER_PORT = 9000; //8089;
    public static final int WEB_SERVER_PORT = 9000;
    public static final String CONTEXT_PATH_WEBSOCKET = "/ws";
    protected static final String CONTEXT_PATH_ROOT = "/";
    public static final String CONTEXT_PATH_COVERART = "/coverart/";
    public static final String CONTEXT_PATH_MUSIC = "/music/";

    protected final Context context;
    private final TagRepository tagRepos;
    private final FileRepository fileRepos;
    private final File coverartDir;
    private final String appVersion;
    private final String osVersion;
    private final List<String> libInfos = new ArrayList<>();

    // common cache, used by all services
    private final LruCache<String, String> memoryCache;
    final int cacheSize = 10240; // 10 k

    public BaseServer(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        this.context = context;
        this.fileRepos = fileRepos;
        this.tagRepos = tagRepos;

        this.coverartDir = context.getExternalCacheDir();
        this.appVersion = ApplicationUtils.getVersionNumber(context);
        this.osVersion = Build.VERSION.RELEASE;
        this.memoryCache = new LruCache<>(cacheSize);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("apincer.android.mmate", "apincer.android.mmate.service.MusicMateServiceImpl"));
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public PlaybackService getPlaybackService() {
        return playbackService;
    }

    private PlaybackService playbackService;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MusicMateServiceBinder binder = (MusicMateServiceBinder) service;
            playbackService = binder.getPlaybackService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackService = null;
            //  Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    public void destroy() {
        if(playbackService != null) {
            context.unbindService(serviceConnection);
            playbackService = null;
        }
    }

    public String getServerSignature(String componentName) {
        //Server:  WebServer MusicMate/3.11.0-251014 (Android/16; Jetty/12.1.1;)
        String libInfos = String.join("; ", this.libInfos);

        return String.format("%s MusicMate/%s (Android/%s; %s)", trimToEmpty(componentName), appVersion, osVersion, trimToEmpty(libInfos));
    }

    public void addLibInfo(String name, String version) {
        if(isEmpty(version)) {
            libInfos.add(name);
        }else {
            libInfos.add(name + "/" + version);
        }
    }

    public Context getContext() {
        return context;
    }

    public File getCoverartDir(String coverartName) {
        return new File(coverartDir, coverartName);
    }

    public WebSocketContent buildWebSocketContent() {
       return new WebSocketContent();
    }

    public void subscribePlaybackState(Consumer<PlaybackState> consumer) {
        if(playbackService != null) {
            playbackService.subscribePlaybackState(consumer);
        }
    }

    public void subscribeNowPlayingSong(Consumer<Optional<MediaTrack>> consumer) {
        if(playbackService != null) {
            playbackService.subscribeNowPlayingSong(consumer);
        }
    }

    public void subscribePlaybackTarget(Consumer<Optional<PlaybackTarget>> consumer) {
        if(playbackService != null) {
            playbackService.subscribePlaybackTarget(consumer);
        }
    }

    public void notifyPlayback(String clientIp, String userAgent, MusicTag tag) {
        MusicMateExecutors.execute(() -> {
            if(playbackService != null) {
                PlaybackTarget player = RemoteWebPlayer.Factory.create(clientIp, userAgent, clientIp);
                playbackService.switchPlayer(player, false);
                playbackService.onMediaTrackChanged(tag);
            }
        });
    }

    /**
     * Formats the given time value as a string in the standard IMF-fixdate format (RFC 1123).
     *
     * @param time the time in milliseconds since the Java epoch (00:00:00 GMT, January 1, 1970)
     * @return the given time value as a formatted string
     */
    public String formatDate(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC)
        );
    }


    protected String formatAudioQuality(MusicTag tag) {
        // Pre-calculate capacity to avoid StringBuilder resizing
        String key = "quality_"+ tag.getId();
        String qualityText = memoryCache.get(key);

        if(qualityText == null) {

            StringBuilder quality = new StringBuilder(64);

            if (tag.getAudioSampleRate() >= 88200 && tag.getAudioBitsDepth() >= 24) {
                quality.append("Hi-Res ");
            } else if (tag.getAudioSampleRate() >= 44100 && tag.getAudioBitsDepth() >= 16) {
                quality.append("CD-Quality ");
            }

            quality.append(tag.getFileType());

            if (tag.getAudioSampleRate() > 0) {
                quality.append(' ').append(tag.getAudioSampleRate() / 1000.0).append("kHz");
            }

            if (tag.getAudioBitsDepth() > 0) {
                quality.append('/').append(tag.getAudioBitsDepth()).append("-bit");
            }

            int channels = TagUtils.getChannels(tag);
            if (channels == 1) {
                quality.append(" Mono");
            } else if (channels == 2) {
                quality.append(" Stereo");
            } else if (channels > 2) {
                quality.append(" Multichannel (").append(channels).append(')');
            }

            qualityText = quality.toString();
            memoryCache.put(key, qualityText);
        }

        return qualityText;
    }

    public static String getMusicUrl(MediaTrack track) {
        return "http://"+ NetworkUtils.getIpAddress()+":"+  CONTENT_SERVER_PORT+CONTEXT_PATH_MUSIC + track.getId() + "/file." + track.getFileType();
    }

    public TagRepository getTagRepos() {
        return tagRepos;
    }

    public FileRepository getFileRepos() {
        return fileRepos;
    }

    /**
     * get the ip address of the device
     *
     * @return the address or null if anything went wrong
     */
    /*
    @NonNull
    public static String getIpAddress() {
        String hostAddress = null;
        try {
            for (Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
                    .getNetworkInterfaces(); networkInterfaces
                         .hasMoreElements(); ) {
                NetworkInterface networkInterface = networkInterfaces
                        .nextElement();
                if (!networkInterface.getName().startsWith("rmnet")) {
                    for (Enumeration<InetAddress> inetAddresses = networkInterface
                            .getInetAddresses(); inetAddresses.hasMoreElements(); ) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress
                                .getHostAddress() != null
                                && IPV4_PATTERN.matcher(inetAddress
                                .getHostAddress()).matches()) {

                            hostAddress = inetAddress.getHostAddress();
                        }

                    }
                }
            }
        } catch (SocketException se) {
            Log.d(TAG,
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    } */

    public class WebSocketContent {
        private static final String TAG = "WebSocketContent";
        private final MusicInfoService musicInfoService = new MusicInfoService();
        // Cache for generated waveforms to avoid repeated heavy processing.
        private final LruCache<String, float[]> memoryCache;
        final int cacheSize = 10240; // Approx 10 MB based on average waveform size

        /**
         * Constructs the WebSocket content handler.
         */
        protected WebSocketContent() {
            // Initialize the waveform cache
            memoryCache = new LruCache<>(cacheSize) {
                @Override
                protected int sizeOf(@NonNull String key, @NonNull float[] waveform) {
                    // Size calculation based on float size (4 bytes) converted to kilobytes
                    return (waveform.length * 4) / 1024;
                }
            };
        }

        /**
         * The main entry point for processing commands received from the WebSocket client.
         * Parses the command and delegates to the appropriate handler method.
         *
         * @param command The command string (e.g., "browse", "play", "getTrackMetadata").
         * @param message A Map containing the payload associated with the command (e.g., path, trackId).
         * @return A Map representing the JSON response to send back to the client,
         * or {@code null} if the command does not require a direct response (e.g., control commands like 'next').
         */
        @Nullable
        public Map<String, Object> handleCommand(String command, Map<String, Object> message) {
            if (command == null) {
                Log.w(TAG, "Received null command");
                return null;
            }

            try { // Add try-catch for robustness against parsing errors
                switch (command) {
                    case "browse":
                        String path = String.valueOf(message.getOrDefault("path", "Library"));
                        return sendBrowseResult(path);
                    case "getTrackMetadata":
                        Object idObj = message.get("id");
                        Object artistObj = message.get("artist");
                        Object albumObj = message.get("album");
                        if (!(idObj instanceof Number) || artistObj == null || albumObj == null) {
                            Log.w(TAG, "getTrackMetadata command failed: missing or invalid id, artist, or album.");
                            return createErrorResponse("Invalid parameters for getTrackMetadata");
                        }
                        long trackIdMeta = ((Number) idObj).longValue();
                        String artist = String.valueOf(artistObj);
                        String album = String.valueOf(albumObj);
                        return handleGetTrackMetadata(trackIdMeta, artist, album);
                    case "setRenderer":
                        String udn = String.valueOf(message.get("udn"));
                        if(udn == null || udn.isEmpty() || "null".equalsIgnoreCase(udn)) {
                            Log.w(TAG, "setRenderer command failed: missing or invalid udn.");
                            return createErrorResponse("Invalid UDN for setRenderer");
                        }
                        setRenderer(udn);
                        break; // No direct response needed, state will be pushed
                    case "getQueue":
                        return sendQueueUpdate();
                    case "addToQueue":
                        String songIdAdd = String.valueOf(message.get("id"));
                        if(songIdAdd == null || songIdAdd.isEmpty() || "null".equalsIgnoreCase(songIdAdd)) {
                            Log.w(TAG, "addToQueue command failed: missing or invalid id.");
                            return createErrorResponse("Invalid ID for addToQueue");
                        }
                        handleAddToQueue(songIdAdd);
                        break; // Queue update will be pushed
                    case "emptyQueue":
                        handleEmptyQueue();
                        break; // Queue update will be pushed
                    case "play":
                        String songIdPlay = String.valueOf(message.get("id"));
                        if(songIdPlay == null || songIdPlay.isEmpty() || "null".equalsIgnoreCase(songIdPlay)) {
                            Log.w(TAG, "play command failed: missing or invalid id.");
                            return createErrorResponse("Invalid ID for play");
                        }
                        handlePlay(songIdPlay);
                        break; // Playback state updates will be pushed
                    case "playFromContext":
                        String songIdContext = String.valueOf(message.get("id"));
                        String pathContext = String.valueOf(message.get("path"));
                        if(songIdContext == null || songIdContext.isEmpty() || "null".equalsIgnoreCase(songIdContext) || pathContext == null || pathContext.isEmpty()) {
                            Log.w(TAG, "playFromContext command failed: missing or invalid id or path.");
                            return createErrorResponse("Invalid ID or Path for playFromContext");
                        }
                        handlePlayFormContext(songIdContext, pathContext);
                        break; // Playback state and queue updates will be pushed
                    case "next":
                        handlePlayNext();
                        break; // Playback state update will be pushed
                    case "previous":
                        handlePlayPrevious();
                        break; // Playback state update will be pushed
                    case "getTrackDetails":
                        String trackIdDetails = String.valueOf(message.get("id"));
                        if(trackIdDetails == null || trackIdDetails.isEmpty() || "null".equalsIgnoreCase(trackIdDetails)) {
                            Log.w(TAG, "getTrackDetails command failed: missing or invalid id.");
                            return createErrorResponse("Invalid ID for getTrackDetails");
                        }
                        return handleGetTrackDetails(trackIdDetails);
                    case "setRepeatMode":
                        String mode = String.valueOf(message.get("mode"));
                        if(mode == null || (!mode.equals("none") && !mode.equals("all") && !mode.equals("one"))) {
                            Log.w(TAG, "setRepeatMode command failed: invalid mode.");
                            return createErrorResponse("Invalid mode for setRepeatMode");
                        }
                        handleSetRepeatMode(mode);
                        break; // Playback state update will be pushed
                    case "setShuffle":
                        // Safely handle potential ClassCastException
                        Object enabledObj = message.get("enabled");
                        boolean enabled = Boolean.TRUE.equals(enabledObj); // Defaults to false if null or not boolean
                        handleSetShuffleMode(enabled);
                        break; // Playback state update will be pushed
                    // Add case for "getNowPlaying" if needed for desync fix
                    case "getNowPlaying":
                        return sendNowPlaying();
                    default:
                        Log.w(TAG, "Unknown command received: " + command);
                        return createErrorResponse("Unknown command: " + command);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing numeric ID for command: " + command, e);
                return createErrorResponse("Invalid numeric ID format");
            } catch (ClassCastException e) {
                Log.e(TAG, "Error casting parameter for command: " + command, e);
                return createErrorResponse("Invalid parameter type");
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error handling command: " + command, e);
                return createErrorResponse("Internal server error");
            }
            return null; // Return null for commands that don't send a direct response
        }

        /**
         * Handles the "getTrackMetadata" command. Fetches detailed track information including
         * waveform data, artist/album bios, genres, and credits. This involves potentially
         * slow operations like file analysis and external API calls.
         *
         * @param trackId The ID of the track to fetch metadata for.
         * @param artist  The artist name (used for external API lookups).
         * @param album   The album name (used for external API lookups).
         * @return A Map representing the "trackMetadata" response, or {@code null} if the track is not found.
         */
        @Nullable
        private Map<String, Object> handleGetTrackMetadata(long trackId, String artist, String album) {
            MusicTag song = tagRepos.findById(trackId);
            if (song == null) {
                Log.w(TAG, "handleGetTrackMetadata: Track not found for ID " + trackId);
                return null;
            }

            TrackInfo info = null;
            try {
                info = musicInfoService.getFullTrackInfo(artist, album);
            } catch (Exception ex) {
                Log.e(TAG, "getTrackMetadata (bio fetch) failed for track ID " + trackId, ex);
            }

            float[] waveform = getWaveform(song);

            Map<String, Object> infoPayload = new HashMap<>();
            if (info != null) {
                infoPayload.put("artistBio", info.artistBio());
                infoPayload.put("albumBio", info.albumBio());
                infoPayload.put("genres", info.genres() != null ? info.genres() : Collections.emptyList()); // Ensure not null
                infoPayload.put("highResArtUrl", info.highResArtUrl());
            } else {
                infoPayload.put("genres", Collections.emptyList()); // Default empty list if info fetch fails
            }

            Map<String, String> credits = new HashMap<>();
            if (!isEmpty(song.getComposer())) {
                credits.put("composer", song.getComposer());
            }
            // Assuming MusicTag has getLyricist() - uncomment if it exists
        /*
        if (!isEmpty(song.getLyricist())) {
            credits.put("lyricist", song.getLyricist());
        }
        */
            // Add more credits (producer, personnel) if available in MusicTag
            infoPayload.put("credits", credits);

            return Map.of(
                    "type", "trackMetadata",
                    "trackId", song.getId(),
                    "waveform", waveform != null ? waveform : new float[0], // Ensure waveform is not null
                    "info", infoPayload
            );
        }

        /**
         * Retrieves overall statistics for the music library.
         *
         * @return A Map representing the "statsUpdate" response containing total songs, size, and duration.
         */
        public Map<String, Object> getLibraryStats() {
            // Consider caching these stats if the library is large and doesn't change often
            List<MusicTag> list = tagRepos.getAllMusics();
            long songCount = list.size();
            long totalSize = 0;
            long totalDuration = 0;
            for (MusicTag tag : list) {
                if (tag != null) { // Add null check for safety
                    totalSize += tag.getFileSize();
                    totalDuration += (long) tag.getAudioDuration();
                }
            }

            Map<String, Object> stats = Map.of(
                    "totalSize", StringUtils.formatStorageSize(totalSize),
                    "totalDuration", StringUtils.formatDuration(totalDuration, true),
                    "songs", songCount
            );
            return Map.of("type", "statsUpdate", "stats", stats);
        }

        /**
         * Handles the "play" command. Finds the track by ID and initiates playback.
         *
         * @param id The ID string of the track to play.
         */
        private void handlePlay(String id) {
            if (playbackService != null) {
                try {
                    long trackId = Long.parseLong(id);
                    MusicTag tag = tagRepos.findById(trackId);
                    if (tag != null) {
                        playbackService.play(tag);
                    } else {
                        Log.w(TAG, "handlePlay: Track not found for ID " + id);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "handlePlay: Invalid ID format " + id, e);
                }
            }
        }

        /**
         * Handles the "setShuffle" command. Enables or disables shuffle mode.
         *
         * @param enabled {@code true} to enable shuffle, {@code false} to disable.
         */
        private void handleSetShuffleMode(boolean enabled) {
            if (playbackService != null) {
                playbackService.setShuffleMode(enabled);
            }
        }

        /**
         * Handles the "setRepeatMode" command. Sets the repeat mode (none, all, one).
         *
         * @param mode The repeat mode string ("none", "all", "one").
         */
        private void handleSetRepeatMode(String mode) {
            if (playbackService != null) {
                playbackService.setRepeatMode(mode);
            }
        }

        /**
         * Handles the "getTrackDetails" command. Fetches basic track information suitable
         * for quickly populating UI elements before heavier metadata arrives.
         *
         * @param id The ID string of the track.
         * @return A Map representing the "trackDetailsResult" response, or {@code null} if the track is not found.
         */
        @Nullable
        private Map<String, Object> handleGetTrackDetails(String id) {
            try {
                long trackId = Long.parseLong(id);
                MusicTag tag = tagRepos.findById(trackId);
                if (tag != null) {
                    Map<String, Object> track = getMap(tag); // Use getMap for consistency
                    return Map.of("type", "trackDetailsResult", "track", track);
                } else {
                    Log.w(TAG, "handleGetTrackDetails: Track not found for ID " + id);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "handleGetTrackDetails: Invalid ID format " + id, e);
            }
            return null;
        }

        /**
         * Handles the "emptyQueue" command. Clears the current playback queue.
         */
        private void handleEmptyQueue() {
            tagRepos.emptyPlayingQueue();
            //if(playbackService != null) {
            //    playbackService.clearQueue(); // Also inform playback service if necessary
            //}
        }

        /**
         * Handles the "playFromContext" command. Starts playback of a specific track
         * and sets the playback queue based on the context (e.g., album, genre, playlist).
         *
         * @param id   The ID string of the track to start playing.
         * @param path The context path (e.g., "Library/Genres/Pop") defining the queue.
         */
        private void handlePlayFormContext(String id, String path) {
            if (playbackService == null) return;

            try {
                long trackId = Long.parseLong(id);
                MusicTag songToPlay = tagRepos.findById(trackId);
                if(songToPlay == null) {
                    Log.w(TAG, "handlePlayFormContext: Track to play not found for ID " + id);
                    return;
                }

                playbackService.setShuffleMode(false); // Reset shuffle mode for context playback

                List<MusicTag> songsInContext = new ArrayList<>();
                // Determine the list of songs based on the path
                if (path.equalsIgnoreCase("Library/All Songs")) {
                    songsInContext = tagRepos.getAllMusics();
                } else if (path.equalsIgnoreCase("Library/Recently Added")) {
                    songsInContext = tagRepos.findRecentlyAdded(0, 0);
                } else if (path.startsWith("Library/Genres/")) {
                    String name = path.substring("Library/Genres/".length());
                    songsInContext = tagRepos.findByGenre(name);
                } else if (path.startsWith("Library/Artists/")) {
                    String name = path.substring("Library/Artists/".length());
                    songsInContext = tagRepos.findByArtist(name, 0, 0);
                } else if (path.startsWith("Library/Groupings/")) {
                    String name = path.substring("Library/Groupings/".length());
                    songsInContext = tagRepos.findByGrouping(name, 0, 0);
                } else if (path.startsWith("Library/Playlists/")) {
                    String name = path.substring("Library/Playlists/".length());
                    // Assuming isSongInPlaylistName works correctly
                    songsInContext = tagRepos.getAllMusicsForPlaylist().stream()
                            .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                            .collect(Collectors.toList());
                } else {
                    Log.w(TAG, "handlePlayFormContext: Unknown context path: " + path);
                    // Optionally default to playing just the single song
                    songsInContext.add(songToPlay);
                }

                // Set the queue in the repository and load it into the playback service
                tagRepos.setPlayingQueue(songsInContext);
                playbackService.loadPlayingQueue(songToPlay); // Load queue and start playing 'songToPlay'

                // Explicitly start playback (handlePlay might not be needed if loadPlayingQueue starts it)
                // handlePlay(id); // Re-evaluate if this is necessary

            } catch (NumberFormatException e) {
                Log.e(TAG, "handlePlayFormContext: Invalid ID format " + id, e);
            } catch (Exception e) {
                Log.e(TAG, "Error in handlePlayFormContext", e);
            }
        }

        /**
         * Handles the "previous" command. Skips to the previous track in the queue.
         */
        private void handlePlayPrevious() {
            if (playbackService != null) {
                playbackService.playPrevious();
            }
        }

        /**
         * Handles the "next" command. Skips to the next track in the queue.
         */
        private void handlePlayNext() {
            if (playbackService != null) {
                playbackService.playNext();
            }
        }

        /**
         * Handles the "setRenderer" command. Switches the active playback target (UPnP/DLNA device).
         *
         * @param udn The Unique Device Name (UDN) or identifier of the target device.
         */
        private void setRenderer(String udn) {
            if (playbackService != null) {
                playbackService.switchPlayer(udn, true);
            }
        }

        /**
         * Handles the "addToQueue" command. Adds the specified track to the end of the playback queue.
         *
         * @param songId The ID string of the track to add.
         */
        private void handleAddToQueue(String songId) {
            if (songId == null || songId.isBlank()) return;
            try {
                long trackId = Long.parseLong(songId);
                MusicTag song = tagRepos.findById(trackId);
                if (song != null) {
                    tagRepos.addToPlayingQueue(song);
                    //if (playbackService != null) {
                    //    playbackService.addToQueue(song); // Also inform playback service if needed
                    // }
                } else {
                    Log.w(TAG, "handleAddToQueue: Track not found for ID " + songId);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "handleAddToQueue: Invalid ID format " + songId, e);
            }
        }

        /**
         * Retrieves the current state of the playback queue.
         *
         * @return A Map representing the "updateQueue" response containing the list of tracks in the queue.
         * Returns {@code null} in case of a database error.
         */
        @Nullable
        public Map<String, Object> sendQueueUpdate() {
            try {
                // Fetching QueueItems which contain the order and the MusicTag
                List<QueueItem> playingQueue = tagRepos.getQueueItemDao().queryBuilder().orderBy("position", true).query(); // Order by 'position'
                List<Map<String, ?>> queueAsMaps = playingQueue.stream()
                        .map(QueueItem::getTrack) // Get the MusicTag from QueueItem
                        .filter(java.util.Objects::nonNull) // Filter out any null tracks
                        .map(this::getMap) // Convert MusicTag to Map
                        .collect(Collectors.toList());
                return Map.of("type", "updateQueue", "path", "Playing Queue", "queue", queueAsMaps);
            } catch (SQLException e) {
                Log.e(TAG, "Error fetching playing queue", e);
            }
            return null;
        }

        /**
         * Retrieves the currently playing track information.
         *
         * @return A Map representing the "nowPlaying" response, or {@code null} if nothing is playing.
         */
        @Nullable
        public Map<String, Object> sendNowPlaying() {
            if (playbackService != null) {
                MediaTrack nowPlaying = playbackService.getNowPlayingSong();
                if (nowPlaying != null) {
                    Map<String, Object> track = getMap(nowPlaying);
                    return Map.of("type", "nowPlaying", "track", track);
                }
            }
            return null; // Send null or a specific "stopped" message if needed
        }

        /**
         * Handles the "browse" command. Retrieves the list of items (songs or folders)
         * for the specified library path.
         *
         * @param path The library path to browse (e.g., "Library/Genres/Pop").
         * @return A Map representing the "browseResult" response containing the items and the current path.
         */
        private Map<String, Object> sendBrowseResult(String path) {
            List<Map<String, ?>> items = new ArrayList<>(); // Use ArrayList for modification safety
            try {
                if (path == null || path.isEmpty() || path.equalsIgnoreCase("Library")) {
                    // Default top-level view
                    items = List.of(
                            Map.of("type", "folder", "name", "Recently Added", "path", "Library/Recently Added"),
                            Map.of("type", "folder", "name", "All Songs", "path", "Library/All Songs"),
                            Map.of("type", "folder", "name", "Artists", "path", "Library/Artists"),
                            Map.of("type", "folder", "name", "Genres", "path", "Library/Genres"),
                            Map.of("type", "folder", "name", "Playlists", "path", "Library/Playlists")
                    );
                } else if (path.equalsIgnoreCase("Library/All Songs")) {
                    List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
                    items = songs.stream().map(this::getMap).collect(Collectors.toList());
                } else if (path.equalsIgnoreCase("Library/Recently Added")) {
                    List<MusicTag> songs = tagRepos.findRecentlyAdded(0, 0);
                    items = songs.stream().map(this::getMap).collect(Collectors.toList());
                } else if (path.equalsIgnoreCase("Library/Genres")) {
                    List<String> genres = tagRepos.getActualGenreList();
                    items = genres.stream()
                            .map(entry -> Map.<String, Object>of("type", "folder", "name", entry, "path", "Library/Genres/" + entry))
                            .collect(Collectors.toList());
                } else if (path.startsWith("Library/Genres/")) {
                    String name = path.substring("Library/Genres/".length());
                    List<MusicTag> songs = tagRepos.findByGenre(name);
                    items = songs.stream().map(this::getMap).collect(Collectors.toList());
                } else if (path.equalsIgnoreCase("Library/Artists")) {
                    List<String> artists = tagRepos.getArtistList(); // Assuming this method exists
                    items = artists.stream()
                            .map(entry -> Map.<String, Object>of("type", "folder", "name", entry, "path", "Library/Artists/" + entry))
                            .collect(Collectors.toList());
                } else if (path.startsWith("Library/Artists/")) {
                    String name = path.substring("Library/Artists/".length());
                    List<MusicTag> songs = tagRepos.findByArtist(name, 0, 0);
                    items = songs.stream().map(this::getMap).collect(Collectors.toList());
                } else if (path.equalsIgnoreCase("Library/Playlists")) {
                    List<PlaylistEntry> list = PlaylistRepository.getPlaylists();
                    items = list.stream()
                            .sorted(Comparator.comparing(PlaylistEntry::getName))
                            .map(entry -> Map.<String, Object>of("type", "folder", "name", entry.getName(), "path", "Library/Playlists/" + entry.getName()))
                            .collect(Collectors.toList());
                } else if (path.startsWith("Library/Playlists/")) {
                    String name = path.substring("Library/Playlists/".length());
                    // This might be inefficient if getAllMusicsForPlaylist is large
                    List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
                    items = songs.stream()
                            .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                            .map(this::getMap)
                            .collect(Collectors.toList());
                } else {
                    Log.w(TAG, "sendBrowseResult: Unknown browse path: " + path);
                    // Return empty list for unknown paths
                }
            } catch (Exception e) {
                Log.e(TAG, "Error browsing path: " + path, e);
                // Return empty list on error
            }
            return Map.of("type", "browseResult", "items", items, "path", path);
        }

        /**
         * Converts a {@link MediaTrack} object (like {@link MusicTag}) into a Map suitable for sending as JSON.
         * Contains basic track information used in various responses (browse, queue, nowPlaying, trackDetails).
         *
         * @param song The {@link MediaTrack} to convert.
         * @return A Map containing key track details. Returns an empty map if the input song is null.
         */
        @NonNull
        private Map<String, Object> getMap(@Nullable MediaTrack song) {
            if (song == null) return new HashMap<>();

            // Use Map.of for immutable core fields, then add optional fields to a HashMap
            Map<String, Object> track = new HashMap<>(Map.of(
                    "type", "song",
                    "id", song.getId(),
                    "title", trimToEmpty(song.getTitle()), // Use trimToEmpty for safety
                    "artist", trimToEmpty(song.getArtist()),
                    "album", trimToEmpty(song.getAlbum()),
                    "duration", song.getAudioDuration(),
                    "format", trimToEmpty(song.getAudioEncoding()).toUpperCase(),
                    "bitDepth", StringUtils.formatAudioBitsDepth(song.getAudioBitsDepth()),
                    "sampleRate", StringUtils.formatAudioSampleRate(song.getAudioSampleRate(),true),
                    "artUrl", "/coverart/" + song.getAlbumCoverUniqueKey() + ".png"
            ));

            if (!isEmpty(song.getYear())) {
                track.put("year", song.getYear());
            }

            if (song.getDynamicRangeScore()>0) {
                track.put("drs", song.getDynamicRangeScore());
            }

            String qualityIndicator = song.getQualityInd();
            if (!isEmpty(qualityIndicator)) {
                // Check specifically for MQA using TagUtils, otherwise use the indicator
                if (TagUtils.isMQA(song) || TagUtils.isMQAStudio(song)) { // Combine MQA checks
                    track.put("quality", "MQA");
                } else {
                    track.put("quality", qualityIndicator);
                }
            }

            return track;
        }

        /**
         * Retrieves the current playback state (playing/paused, position, duration, track ID).
         *
         * @param state The current {@link PlaybackState} object from the {@link PlaybackService}.
         * @return A Map representing the "playbackState" response, or {@code null} if the state or track is invalid.
         */
        @Nullable
        public Map<String, Object> getPlaybackState(@Nullable PlaybackState state) {
            if (state == null || state.currentTrack == null || state.currentState == null) return null;

            // Ensure state name is lowercase as expected by the client
            String stateName = state.currentState.name().toLowerCase();

            return Map.of(
                    "type", "playbackState",
                    "trackId", state.currentTrack.getId(),
                    "elapsed", state.currentPositionSecond, // Convert ms to seconds for client
                    "state", stateName,
                    "duration", state.currentTrack.getAudioDuration() // Assuming duration is already in seconds
            );
        }

        /**
         * Generates or retrieves the waveform data for a given track from the cache or by analysis.
         *
         * @param tag The {@link MediaTrack} to get the waveform for.
         * @return A float array representing the waveform peaks, or {@code null} if generation fails.
         */
        @Nullable
        private float[] getWaveform(@NonNull MediaTrack tag) {
            String cacheKey = String.valueOf(tag.getId());

            // --- 1. First check (fast, no lock) ---
            // LruCache.get() is thread-safe
            float[] waveform = memoryCache.get(cacheKey);

            if (waveform == null) {
                // --- 2. Lock only on a cache miss ---
                // We synchronize on the cache itself
                synchronized (memoryCache) {

                    // --- 3. Second check (inside the lock) ---
                    // This stops the race condition. While this thread was
                    // waiting for the lock, another thread might have
                    // finished and populated the cache.
                    waveform = memoryCache.get(cacheKey);

                    if (waveform == null) {
                        // --- 4. Generate (This block only runs ONCE) ---
                        Log.d(TAG, "Waveform cache miss for track ID: " + cacheKey);
                        waveform = MusicAnalyser.generateWaveform(context, tag, 480, 0.6);

                        if (waveform != null) {
                            // --- 5. Put in cache (also thread-safe) ---
                            // If this is the 11th item, LruCache will
                            // automatically remove the oldest one.
                            memoryCache.put(cacheKey, waveform);
                            Log.d(TAG, "Waveform generated and cached for track ID: " + cacheKey);
                        } else {
                            Log.w(TAG, "Failed to generate waveform for track ID: " + cacheKey);
                        }
                    } else {
                        Log.d(TAG, "Waveform (double-check) cache hit for ID: " + cacheKey);
                    }
                }
            } else {
                Log.d(TAG, "Waveform cache hit for track ID: " + cacheKey);
            }

            return waveform;
        }

    /*
    @Nullable
    private float[] getWaveform(@NonNull MediaTrack tag) {
        String cacheKey = String.valueOf(tag.getId());
        float[] waveform = memoryCache.get(cacheKey);
        if (waveform == null) {
            Log.d(TAG, "Waveform cache miss for track ID: " + cacheKey);
            waveform = MusicAnalyser.generateWaveform(context, tag, 480, 0.6); // Consider adjusting parameters
            if (waveform != null) {
                memoryCache.put(cacheKey, waveform);
                Log.d(TAG, "Waveform generated and cached for track ID: " + cacheKey);
            } else {
                Log.w(TAG, "Failed to generate waveform for track ID: " + cacheKey);
            }
        } else {
            Log.d(TAG, "Waveform cache hit for track ID: " + cacheKey);
        }
        return waveform;
    } */


        /**
         * Gets the status information for the currently active playback target (renderer).
         *
         * @param player The currently active {@link PlaybackTarget}.
         * @return A Map representing the "playerStatus" response, or {@code null} if no player is active.
         */
        @Nullable
        public Map<String, Object> getPlaybackTarget(@Nullable PlaybackTarget player) {
            if (player != null) {
                return Map.of(
                        "type", "playerStatus",
                        "targetId", player.getTargetId(),
                        "name", player.getDisplayName()
                        // Add volume, shuffle, repeat mode if available from PlaybackTarget/PlaybackService
                );
            }
            return null;
        }

        /**
         * Retrieves the list of available playback targets (renderers) discovered on the network.
         *
         * @return A Map representing the "availableRenderers" response containing the list of renderers.
         */
        public Map<String, Object> getAvailableRenderers() {
            if (playbackService == null) return Map.of("type", "availableRenderers", "renderers", Collections.emptyList());

            List<Map<String, ?>> renderers = Collections.emptyList(); // Default to empty list
            List<PlaybackTarget> rendererList = playbackService.getAvailablePlaybackTargets();

            if (rendererList != null && !rendererList.isEmpty()) {
                renderers = rendererList.stream()
                        .map(device -> Map.<String, Object>of(
                                "name", device.getDisplayName(),
                                "targetId", device.getTargetId()
                        ))
                        .collect(Collectors.toList());
            }

            return Map.of("type", "availableRenderers", "renderers", renderers);
        }

        /**
         * Gets the status information for the currently active playback target.
         * Convenience method that retrieves the player from the PlaybackService.
         *
         * @return A Map representing the "playerStatus" response, or {@code null} if no player is active.
         */
        @Nullable
        public Map<String, Object> getPlaybackTarget() {
            if (playbackService != null) {
                return getPlaybackTarget(playbackService.getPlayer());
            }
            return null;
        }

        /**
         * Formats a given MediaTrack into the standard "nowPlaying" message structure.
         * This is a helper method used when the track object is already known and
         * needs to be prepared for sending via WebSocket.
         *
         * @param song The {@link MediaTrack} to format.
         * @return A Map representing the "nowPlaying" message, or {@code null} if the input song is null.
         */
        @Nullable // Add Nullable annotation
        public Map<String, Object> getNowPlaying(@Nullable MediaTrack song) { // Add Nullable annotation
            if (song != null) {
                Map<String, Object> track = getMap(song); // getMap already handles null check internally, but good practice
                return Map.of("type", "nowPlaying", "track", track);
            }
            return null;
        }

        /**
         * Helper method to create a standardized error response map.
         * @param message The error message string.
         * @return A Map representing an error response.
         */
        private Map<String, Object> createErrorResponse(String message) {
            return Map.of("type", "error", "message", message);
        }
    }
}
