package apincer.android.mmate.core.repository;

import static apincer.android.mmate.core.utils.TagUtils.isMQA;
import static apincer.android.mmate.core.utils.StringUtils.isEmpty;

import android.util.Log;

import androidx.annotation.NonNull;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import apincer.android.mmate.core.server.IMediaServer;
import apincer.android.mmate.core.server.RendererDevice;
import apincer.android.mmate.core.codec.MusicAnalyser;
import apincer.android.mmate.core.model.TrackInfo;
import apincer.android.mmate.core.playback.NowPlaying;
import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.database.QueueItem;
import apincer.android.mmate.core.model.PlaylistEntry;
import apincer.android.mmate.core.utils.TagUtils;
import apincer.android.mmate.core.utils.StringUtils;

public class WebSocketContent {
    //private final Context context;
    private final TagRepository tagRepos;
    private final OrmLiteHelper ormLiteHelper;

    private static final String TAG = "WebSocketContent";
    private final MusicInfoService musicInfoService = new MusicInfoService();
    //private PlaybackService playbackService;
    private final IMediaServer mediaServer;

    public WebSocketContent(IMediaServer mediaServer,TagRepository tagRepos) {
       // this.context = context;
        this.ormLiteHelper = tagRepos.getOrmLiteHelper();
        this.tagRepos = tagRepos;
        this.mediaServer = mediaServer;
    }

    public Map<String, Object> handleCommand(String command, Map<String, Object> message) {
        // Use a switch statement for clarity
        switch (command) {
            case "browse":
                String path = message.getOrDefault("path", "Library").toString();
                return sendBrowseResult(path);
            case "getNowPlaying":
                return sendNowPlaying();
            case "getRenderers": // Corrected from "dlnaRenderers"
                return sendDlnaRenderers();
            case "setRenderer": // Corrected from "dlnaRenderers"
                setRenderer((String) message.get("udn"));
                return sendDlnaRenderers();
            case "getQueue": // Corrected from "updateQueue"
                return sendQueueUpdate(); // Send queue only to the requesting client
            case "addToQueue":
                handleAddToQueue((String) message.get("id")); // Use ID instead of name
                break;
            case "emptyQueue":
                handleEmptyQueue();
                break;
            case "play":
                // Add logic to start playing the song with this ID
                handlePlay((String) message.get("id"));
                break;
            case "playFromContext":
                handlePlayFormContext((String) message.get("id"), (String) message.get("path"));
                break;
            case "next":
                handlePlayNext();
                break;
            case "previous":
                handlePlayPrevious();
                break;
            case "getStats":
                return handleGetStats();
            case "getTrackDetails":
                return handleGetTrackDetails((String) message.get("id"));
            case "getTrackInfo":
                String artist = message.get("artist").toString();
                String album = message.get("album").toString();
                return handleGetTrackInfo(artist, album);
            case "setRepeatMode":
                String mode = message.get("mode").toString();
                handleSetRepeatMode(mode);
                break;
            case "setShuffle":
                boolean enabled = (boolean) message.get("enabled");
                handleSetShuffleMode( enabled);
                break;
            default:
                System.err.println("Unknown command received: " + command);
                break;
        }
        return null;
    }

    private Map<String, Object> handleGetStats() {
        List<MusicTag> list = tagRepos.getAllMusics();
        long songCount = list.size();
        long totalSize = 0;
        long totalDuration = 0;
        for(MusicTag tag: list) {
            totalSize += tag.getFileSize();
            totalDuration += (long) tag.getAudioDuration();
        }

        // 2. Create and send the response.
        Map<String, Object> stats = Map.of(
                "totalSize", StringUtils.formatStorageSize(totalSize),
                "totalDuration", StringUtils.formatDuration(totalDuration, true),
                "songs", songCount
        );
        return Map.of("type", "statsUpdate", "stats", stats);
    }

    private void handlePlay(String id) {
        if(mediaServer != null) {
            MusicTag tag = tagRepos.findById(Long.parseLong(id));
            mediaServer.getPlaybackService().play(tag);
        }
    }

    private void handleSetShuffleMode(boolean enabled) {
        if(mediaServer != null) {
            mediaServer.getPlaybackService().setShuffleMode(enabled);
        }
    }

    private void handleSetRepeatMode(String mode) {
        if(mediaServer!=null) {
            mediaServer.getPlaybackService().setRepeatMode(mode);
        }
    }

    private Map<String, Object> handleGetTrackDetails(String id) {
        MusicTag tag = tagRepos.findById(Long.parseLong(id));
        if(tag != null) {
            Map<String, Object> track = new HashMap<>(Map.of(
                    "title", tag.getTitle(),
                    "artist", tag.getArtist(),
                    "album", tag.getAlbum(),
                    "duration", tag.getAudioDuration(),
                    "elapsed", 0,
                    "state", "playing",
                    "artUrl", "/coverart/"+tag.getAlbumCoverUniqueKey()+".png",
                    "bitDepth", tag.getAudioBitsDepth(),
                    "sampleRate", tag.getAudioSampleRate()
            ));

            // String mqaIndicator = tag.getQualityInd();
            boolean isMqaFile = isMQA(tag) || TagUtils.isMQAStudio(tag); //mqaIndicator != null && mqaIndicator.contains("MQA"));

            // Only add the 'isMQA' field if it is explicitly true.
            if (isMqaFile) {
                track.put("isMQA", true);
            }

            return Map.of("type", "trackDetailsResult", "track", track);
        }
        return null;
    }

    private void handleEmptyQueue() {
        try {
            Dao<QueueItem, Long> queueDao = tagRepos.getQueueItemDao();

            DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();

            deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.
        } catch (SQLException e) {
           // throw new RuntimeException(e);
        }
    }

    private void handlePlayFormContext(String id, String path) {
        if(mediaServer == null) return;

        // update playing queue
        try {
            // play song
            MusicTag song = tagRepos.findById(Long.parseLong(id));
            mediaServer.getPlaybackService().setShuffleMode(false); //  reset shuffle mode
            mediaServer.getPlaybackService().play(song);

            // --- 1. Get references to our Data Access Objects (DAOs) ---
            Dao<QueueItem, Long> queueDao = tagRepos.getQueueItemDao();

            List<MusicTag> songsInContext = new ArrayList<>();
            if (path.equalsIgnoreCase("Library/All Songs")) {
                // Contents of the "All Songs"
                songsInContext = tagRepos.getAllMusics(); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if (path.equalsIgnoreCase("Library/Recently Added")) {
                // Contents of the "All Songs"
                songsInContext = tagRepos.findRecentlyAdded(0,0); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if(path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                songsInContext = ormLiteHelper.findByGenre(name);
            }else if(path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                songsInContext = ormLiteHelper.findByArtist(name,0,0);
            }else if(path.startsWith("Library/Groupings/")) {
                String name = path.substring("Library/Groupings/".length());
                songsInContext = ormLiteHelper.findByGrouping(name,0,0);
            }else if(path.startsWith("Library/Playlists/")) {
                String name = path.substring("Library/Playlists/".length());
                List<MusicTag> songs = ormLiteHelper.findMySongs();
                songsInContext = songs.stream()
                        .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                        .collect(Collectors.toList());
            }

            if(!songsInContext.isEmpty()) {
                // --- 2. Clear the entire existing playing queue ---
                DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();
                deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.

                // Populate the database with the new queue items.
                int queueIndex = 1;
                for (MusicTag tag : songsInContext) {
                    QueueItem newItem = new QueueItem(tag, queueIndex++);
                    queueDao.create(newItem);
                }
            }

            //update current song index in playlist
            mediaServer.getPlaybackService().loadPlayingQueue(song);
        } catch (SQLException e) {
            //throw new RuntimeException(e);
            Log.e(TAG, "handlePlayFormContext", e);
        }
    }

    private void handlePlayPrevious() {
        if(mediaServer != null) {
            mediaServer.getPlaybackService().playPrevious();
        }
    }

    private void handlePlayNext() {
        if(mediaServer != null) {
            mediaServer.getPlaybackService().playNext();
        }
    }

    private void setRenderer(String udn) {
        if(mediaServer != null) {
            mediaServer.getPlaybackService().setActiveDlnaPlayer(udn);
        }
    }

    private Map<String, Object> sendDlnaRenderers() {
        if(mediaServer ==null) return null;

        String activeRendererUdn;
        if(mediaServer.getPlaybackService().getActivePlayer() != null) {
            activeRendererUdn = mediaServer.getPlaybackService().getActivePlayer().getId();
        } else {
            activeRendererUdn = null;
        }
        List<Map<String, ?>> renderers = new ArrayList<>();
        // In a real app, this would come from your DLNA discovery service
        List<RendererDevice> rendererList = mediaServer.getRenderers();
        if(!rendererList.isEmpty()) {
            // to send a list of renderers
            renderers = rendererList.stream()
                    .map(device -> Map.of(
                            "name", device.getFriendlyName(), // Assuming methods to get details
                            "udn", device.getUdn(),
                            "active", device.getUdn().equals(activeRendererUdn)
                    ))
                    .collect(Collectors.toList());
        }

        return Map.of("type", "dlnaRenderers", "renderers", renderers);
    }

    private void handleAddToQueue(String songId) {
        if (songId == null || songId.isBlank()) return;

        // Find the song by ID and add it to the queue
        MusicTag song = tagRepos.findById(Long.parseLong(songId));
        if(song != null) {
            try {
                Dao<QueueItem, Long> queueDao = tagRepos.getQueueItemDao();
                // Get the current size of the queue to determine the next position.
                // If the queue has 5 items (positions 0-4), countOf() returns 5, which is the correct next position.
                long nextPosition = queueDao.countOf();
                QueueItem item = new QueueItem(song, nextPosition);
                queueDao.create(item);
               // return broadcastQueueUpdate();
            } catch (Exception ignored) {

            }
        }
    }

    private Map<String, Object> handleGetTrackInfo(String artist, String album) {
      //  new Thread(() -> {
            TrackInfo info = null;
            try {
                info = musicInfoService.getFullTrackInfo(artist, album);
            } catch (Exception ex) {
                Log.e(TAG, "handleGetTrackInfo", ex);
               // ex.printStackTrace();
            }
            // Now you have the info, create your response payload
            return Map.of(
                    "type", "trackInfoResult",
                    "info", info != null ? info : Map.of() // Send info or an empty map
            );
      //  }).start();
    }

    private Map<String, Object> sendQueueUpdate() {
        // Sends the current queue state to a single client (e.g., on initial connect)
        try {
            List<QueueItem> playingQueue = tagRepos.getQueueItemDao().queryForAll();
            List<Map<String, ?>> queueAsMaps = playingQueue.stream()
                    .filter(song -> song.getTrack() != null)
                    .map(song -> getMap(song.getTrack()))
                    .collect(Collectors.toList());
            return Map.of("type", "updateQueue", "path", "Playing Queue", "queue", queueAsMaps);
        } catch (SQLException e) {
           // throw new RuntimeException(e);
        }
        return null;
    }

    private Map<String, Object> sendNowPlaying() {
        if(mediaServer != null) {
            NowPlaying nowPlaying = mediaServer.getPlaybackService().getNowPlaying();
            if (nowPlaying != null && nowPlaying.getSong() != null) {
                MusicTag tag = nowPlaying.getSong();
                Map<String, Object> track = getMap(tag);

                return Map.of("type", "nowPlaying", "track", track);
            }
        }
        return null;
    }

    private Map<String, Object> sendBrowseResult (String path){
            // System.out.println("Browsing path: " + path);
            // In a real app, you would use the 'path' to query a database or filesystem.
            // Here, we'll return different mock data based on the path to simulate navigation.

            List<Map<String, ?>> items = Collections.emptyList();
            /*
             Library
             ├── All Songs
             ├── Genres
             ├── Groupings
             └── Playlists
             */
            if (path.equalsIgnoreCase("Library/All Songs")) {
                // Contents of the "All Songs"
                List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
                items = songs.stream()
                        .map(this::getMap)
                        .collect(Collectors.toList());
            }else if (path.equalsIgnoreCase("Library/Recently Added")) {
                    // Contents of the "Recently Added"
                    List<MusicTag> songs = tagRepos.findRecentlyAdded(0, 0);
                    items = songs.stream()
                            .map(this::getMap)
                            .collect(Collectors.toList());
            } else if (path.equalsIgnoreCase("Library/Genres")) {
                // Contents of the "Genres"
                List<String> genres = tagRepos.getActualGenreList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Genres/" + entry
                        ))
                        .collect(Collectors.toList());
            } else if (path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                List<MusicTag> songs = ormLiteHelper.findByGenre(name);
                // Convert the List<MusicTag> to a List<Map<String, ?>>
                items = songs.stream()
                        .map(this::getMap)
                        .collect(Collectors.toList());
            } else if (path.equalsIgnoreCase("Library/Artists")) {
                // Contents of the "Genres"
                List<String> genres = tagRepos.getArtistList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Artists/" + entry
                        ))
                        .collect(Collectors.toList());
            } else if (path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                List<MusicTag> songs = ormLiteHelper.findByArtist(name, 0, 0);
                items = songs.stream()
                        .map(this::getMap)
                        .collect(Collectors.toList());
            } else if (path.equalsIgnoreCase("Library/Playlists")) {
                List<PlaylistEntry> list = PlaylistRepository.getPlaylists();
                items = list.stream()
                        .sorted(Comparator.comparing(PlaylistEntry::getName))
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry.getName(),
                                "path", "Library/Playlists/" + entry.getName()
                        ))
                        .collect(Collectors.toList());
            } else if (path.startsWith("Library/Playlists/")) {
                String name = path.substring("Library/Playlists/".length());
                List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
                items = songs.stream()
                        .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                        .map(this::getMap)
                        .collect(Collectors.toList());
            } else { // Default top-level view
                items = List.of(
                        // Folders
                        Map.of("type", "folder", "name", "Recently Added", "path", "Library/Recently Added"),
                        Map.of("type", "folder", "name", "All Songs", "path", "Library/All Songs"),
                        Map.of("type", "folder", "name", "Artists", "path", "Library/Artists"),
                        Map.of("type", "folder", "name", "Genres", "path", "Library/Genres"),
                        // Map.of("type", "folder", "name", "Groupings", "path", "Library/Groupings"),
                        // A single song at the top level for demonstration
                        Map.of("type", "folder", "name", "Playlists", "path", "Library/Playlists")
                );
            }

            // Also return the current path in the response so the UI can update the breadcrumb
        return Map.of("type", "browseResult", "items", items, "path", path);
    }

        @NonNull
        private Map<String, Object> getMap(MusicTag song) {
            if(song == null) return new HashMap<>();

            Map<String, Object> track = new HashMap<>(Map.of(
                    "type", "song",
                    "id", song.getId(),
                    "title", song.getTitle(),
                    "artist", song.getArtist(),
                    "album", song.getAlbum(),
                    "duration", song.getAudioDuration(),
                    "elapsed", 0,
                    "state", "playing",
                    "artUrl", "/coverart/"+song.getAlbumCoverUniqueKey()+".png"
            ));

            track.put("bitDepth", song.getAudioBitsDepth());
            track.put("sampleRate", song.getAudioSampleRate());
            //}

            String qualityIndicator = song.getQualityInd(); //MusicTagUtils.getQualityIndicator(song);
            if(!isEmpty(qualityIndicator)) {
                if(isMQA(song)) {
                    track.put("quality", "MQA");
                }else {
                    track.put("quality", qualityIndicator);
                }
            }

            return track;
        }

  /*  public void setPlaybackService(PlaybackService playbackService) {
        this.playbackService = playbackService;
    } */

    public Map<String, Object> getPlayingQueue(List<MusicTag> playingQueue) {
        List<Map<String, ?>> queueAsMaps = playingQueue.stream()
                .map(this::getMap)
                .collect(Collectors.toList());
        return Map.of("type", "updateQueue", "path", "Playing Queue","queue", queueAsMaps);

    }

    public Map<String, Object> getNowPlaying(NowPlaying nowPlaying) {
        long elapsed = nowPlaying.getElapsed();
        MusicTag tag = nowPlaying.getSong();
        if(tag != null) {
            Map<String, Object> track = getMap(tag);
            track.put("elapsed", elapsed);
            track.put("state", nowPlaying.getPlayingState());

            float[] cleanBlockWaveform = tag.getWaveformData();
            if (cleanBlockWaveform == null) {
                cleanBlockWaveform = MusicAnalyser.generateDynamicSongData(640);
                // should create from file and save to db
            }

            track.put("waveform", cleanBlockWaveform);

            return Map.of("type", "nowPlaying", "track", track);
        }
        return null;
    }
}
