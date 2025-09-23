package apincer.android.mmate.dlna.content;

import static apincer.android.mmate.utils.MusicTagUtils.isMQA;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import androidx.annotation.NonNull;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;

import org.jupnp.model.meta.RemoteDevice;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.codec.MusicAnalyser;
import apincer.android.mmate.info.MusicInfoService;
import apincer.android.mmate.info.TrackInfo;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.database.QueueItem;
import apincer.android.mmate.repository.model.PlaylistEntry;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;

public class WebSocketContent {
    private final MusicInfoService musicInfoService = new MusicInfoService();
    private PlaybackService playbackService;

    public WebSocketContent() {
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
                // System.out.println("setRenderer: "+ message.get("udn"));
                setRenderer((String) message.get("udn"));
                return sendDlnaRenderers();
            case "getQueue": // Corrected from "updateQueue"
                return sendQueueUpdate(); // Send queue only to the requesting client
            case "addToQueue":
                handleAddToQueue((String) message.get("id")); // Use ID instead of name
                break;
            case "emptyQueue":
                handleEmptyQueue();
               // sendQueueUpdate(ctx);
                break;
            case "play":
                // Add logic to start playing the song with this ID
                handlePlay((String) message.get("id"));
               // sendNowPlaying(ctx);
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
        List<MusicTag> list = TagRepository.getAllMusics();
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
        if(playbackService != null) {
            MusicTag tag = TagRepository.findById(Long.parseLong(id));
            playbackService.play(tag);
        }
    }

    private void handleSetShuffleMode(boolean enabled) {
        if(playbackService != null) {
            playbackService.setShuffleMode(enabled);
        }
    }

    private void handleSetRepeatMode(String mode) {
        if(playbackService!=null) {
            playbackService.setRepeatMode(mode);
        }
    }

    private Map<String, Object> handleGetTrackDetails(String id) {
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(id));
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
            boolean isMqaFile = isMQA(tag) || MusicTagUtils.isMQAStudio(tag); //mqaIndicator != null && mqaIndicator.contains("MQA"));

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
            Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();

            DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();

            deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.
        } catch (SQLException e) {
           // throw new RuntimeException(e);
        }
    }

    private void handlePlayFormContext(String id, String path) {
        if(playbackService == null) return;

        // update playing queue
        try {
            // --- 1. Get references to our Data Access Objects (DAOs) ---
            Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();

            List<MusicTag> songsInContext = new ArrayList<>();
            if (path.equalsIgnoreCase("Library/All Songs")) {
                // Contents of the "All Songs"
                songsInContext = TagRepository.getAllMusics(); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if (path.equalsIgnoreCase("Library/Recently Added")) {
                // Contents of the "All Songs"
                songsInContext = TagRepository.findRecentlyAdded(0,0); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if(path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                songsInContext = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
            }else if(path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                songsInContext = MusixMateApp.getInstance().getOrmLite().findByArtist(name,0,0);
            }else if(path.startsWith("Library/Groupings/")) {
                String name = path.substring("Library/Groupings/".length());
                songsInContext = MusixMateApp.getInstance().getOrmLite().findByGrouping(name,0,0);
            }else if(path.startsWith("Library/Playlists/")) {
                String name = path.substring("Library/Playlists/".length());
                List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findMySongs();
                songsInContext = songs.stream()
                        .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                        .collect(Collectors.toList());
            }

            MusicTag song = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(id));
            int songIndex = 0;
            if(!songsInContext.isEmpty()) {
                // --- 2. Clear the entire existing playing queue ---
                DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();
                deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.

                playbackService.getPlayingQueueSubject().getValue().clear();
                playbackService.getPlayingQueueSubject().getValue().addAll(songsInContext);

                // Populate the database with the new queue items.
                int queueIndex = 1;
                for (MusicTag tag : songsInContext) {
                    QueueItem newItem = new QueueItem(tag, queueIndex++);
                    queueDao.create(newItem);
                    if(tag.equals(song)) {
                        songIndex = queueIndex;
                    }
                }
            }

            // play song
            playbackService.setPlayingQueueIndex(songIndex);
            playbackService.play(song);
        } catch (SQLException e) {
            //throw new RuntimeException(e);
        }
    }

    private void handlePlayPrevious() {
        if(playbackService != null) {
            playbackService.previous();
        }
    }

    private void handlePlayNext() {
        if(playbackService != null) {
            playbackService.next();
        }
    }

    private void setRenderer(String udn) {
        if(playbackService != null) {
            playbackService.setDlnaPlayer(udn);
        }
    }

    private Map<String, Object> sendDlnaRenderers() {
        if(playbackService ==null) return null;

        String activeRendererUdn;
        if(playbackService.getActivePlayer() != null) {
            activeRendererUdn = playbackService.getActivePlayer().getId();
        } else {
            activeRendererUdn = null;
        }
        List<Map<String, ?>> renderers = new ArrayList<>();
        // In a real app, this would come from your DLNA discovery service
        List<RemoteDevice> rendererList = playbackService.getRenderers();
        if(!rendererList.isEmpty()) {
            // to send a list of renderers
            renderers = rendererList.stream()
                    .map(device -> Map.of(
                            "name", device.getDetails().getFriendlyName(), // Assuming methods to get details
                            "udn", device.getIdentity().getUdn().getIdentifierString(),
                            "active", device.getIdentity().getUdn().getIdentifierString().equals(activeRendererUdn)
                    ))
                    .collect(Collectors.toList());
        }

        return Map.of("type", "dlnaRenderers", "renderers", renderers);
    }

    private void handleAddToQueue(String songId) {
        if (songId == null || songId.isBlank()) return;

        // Find the song by ID and add it to the queue
        MusicTag song = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(songId));
        if(song != null) {
            try {
                Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();
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
                ex.printStackTrace();
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
            List<QueueItem> playingQueue = MusixMateApp.getInstance().getOrmLite().getQueueItemDao().queryForAll();
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
        if(playbackService != null) {
            NowPlaying nowPlaying = playbackService.getNowPlayingSubject().getValue();
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
                List<MusicTag> songs = TagRepository.getAllMusicsForPlaylist();
                items = songs.stream()
                        .map(this::getMap)
                        .collect(Collectors.toList());
            }else if (path.equalsIgnoreCase("Library/Recently Added")) {
                    // Contents of the "Recently Added"
                    List<MusicTag> songs = TagRepository.findRecentlyAdded(0, 0);
                    items = songs.stream()
                            .map(this::getMap)
                            .collect(Collectors.toList());
            } else if (path.equalsIgnoreCase("Library/Genres")) {
                // Contents of the "Genres"
                List<String> genres = TagRepository.getActualGenreList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Genres/" + entry
                        ))
                        .collect(Collectors.toList());
            } else if (path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
                // Convert the List<MusicTag> to a List<Map<String, ?>>
                items = songs.stream()
                        .map(this::getMap)
                        .collect(Collectors.toList());
            } else if (path.equalsIgnoreCase("Library/Artists")) {
                // Contents of the "Genres"
                List<String> genres = TagRepository.getArtistList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Artists/" + entry
                        ))
                        .collect(Collectors.toList());
            } else if (path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findByArtist(name, 0, 0);
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
                List<MusicTag> songs = TagRepository.getAllMusicsForPlaylist();
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

    public void setPlaybackService(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

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
                // should create from file and save to db
                cleanBlockWaveform = MusicAnalyser.generateWaveform(tag);
                tag.setWaveformData(cleanBlockWaveform);
                TagRepository.saveTag(tag);
            }

            track.put("waveform", cleanBlockWaveform);

            return Map.of("type", "nowPlaying", "track", track);
        }
        return null;
    }
}
