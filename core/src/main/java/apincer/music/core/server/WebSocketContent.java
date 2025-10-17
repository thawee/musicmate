package apincer.music.core.server;

import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.TagUtils.isMQA;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import apincer.music.core.codec.MusicAnalyser;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.QueueItem;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.TrackInfo;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.MusicInfoService;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;

public class WebSocketContent {
    private final Context context;
    private final TagRepository tagRepos;
    private final PlaybackService playbackService;

    private static final String TAG = "WebSocketContent";
    private final MusicInfoService musicInfoService = new MusicInfoService();
    private final LruCache<String, float[]> memoryCache;
    final int cacheSize = 10240; // 10 k

    protected WebSocketContent(Context context, PlaybackService playbackService, TagRepository tagRepos) {
        this.context = context;
        this.tagRepos = tagRepos;
        this.playbackService = playbackService;
        memoryCache = new LruCache<>(cacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull float[] waveform) {
                // The size of the float array in kilobytes
                return (waveform.length * 4) / 1024;
            }
        };
    }

    public Map<String, Object> handleCommand(String command, Map<String, Object> message) {
        // Use a switch statement for clarity
        switch (command) {
            case "browse":
                String path = String.valueOf(message.getOrDefault("path", "Library"));
                return sendBrowseResult(path);
            case "getNowPlaying":
                return sendNowPlaying();
            case "getRenderers": // Corrected from "dlnaRenderers"
                return sendDlnaRenderers();
            case "setRenderer": // Corrected from "dlnaRenderers"
                setRenderer(String.valueOf(message.get("udn")));
                return sendDlnaRenderers();
            case "getQueue": // Corrected from "updateQueue"
                return sendQueueUpdate(); // Send queue only to the requesting client
            case "addToQueue":
                handleAddToQueue(String.valueOf(message.get("id"))); // Use ID instead of name
                break;
            case "emptyQueue":
                handleEmptyQueue();
                break;
            case "play":
                // Add logic to start playing the song with this ID
                handlePlay(String.valueOf(message.get("id")));
                break;
            case "playFromContext":
                handlePlayFormContext(String.valueOf(message.get("id")), String.valueOf(message.get("path")));
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
                return handleGetTrackDetails(String.valueOf(message.get("id")));
            case "getTrackInfo":
                String artist = String.valueOf(message.get("artist"));
                String album = String.valueOf(message.get("album"));
                return handleGetTrackInfo(artist, album);
            case "setRepeatMode":
                String mode = String.valueOf(message.get("mode"));
                handleSetRepeatMode(mode);
                break;
            case "setShuffle":
                boolean enabled = (boolean) message.getOrDefault("enabled", false);
                handleSetShuffleMode( enabled);
                break;
            case "getWaveform":
                // 1. Get the track ID from the message
                double trackId = (double) message.get("id");
                return handleGetWaveform((long) trackId);
            default:
                System.err.println("Unknown command received: " + command);
                break;
        }
        return null;
    }

    private Map<String, Object> handleGetWaveform(long trackId) {
        // 2. Find the song (this is just an example)
        MusicTag song = tagRepos.findById(trackId); // Or findById, etc.

        if (song != null) {
            // 3. Get the waveform (assuming you have a way to do this)
            float[] waveform = getWaveform(song);

            // 4. Create a new Map to send back
            Map<String, Object> response = new HashMap<>();
            response.put("type", "trackWaveform"); // This matches the JS 'case'
            response.put("trackId", song.getId()); // Use the same ID
            response.put("waveform", waveform);

            return response;
        }
        return null;
    }

    public Map<String, Object> handleGetStats() {
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
        if(playbackService != null) {
            MusicTag tag = tagRepos.findById(Long.parseLong(id));
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
        tagRepos.emptyPlayingQueue();
    }

    private void handlePlayFormContext(String id, String path) {
        if(playbackService == null) return;

        // update playing queue
        try {
            // play song
           // MusicTag song = tagRepos.findById(Long.parseLong(id));
            playbackService.setShuffleMode(false); //  reset shuffle mode
            //playbackService.play(song);
            handlePlay(id);

            List<MusicTag> songsInContext = new ArrayList<>();
            if (path.equalsIgnoreCase("Library/All Songs")) {
                // Contents of the "All Songs"
                songsInContext = tagRepos.getAllMusics(); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if (path.equalsIgnoreCase("Library/Recently Added")) {
                // Contents of the "All Songs"
                songsInContext = tagRepos.findRecentlyAdded(0,0); // MusixMateApp.getInstance().getOrmLite().findMySongs();
            }else if(path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                songsInContext = tagRepos.findByGenre(name);
            }else if(path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                songsInContext = tagRepos.findByArtist(name,0,0);
            }else if(path.startsWith("Library/Groupings/")) {
                String name = path.substring("Library/Groupings/".length());
                songsInContext = tagRepos.findByGrouping(name,0,0);
            }else if(path.startsWith("Library/Playlists/")) {
                String name = path.substring("Library/Playlists/".length());
                List<MusicTag> songs = tagRepos.findMySongs();
                songsInContext = songs.stream()
                        .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                        .collect(Collectors.toList());
            }

            tagRepos.setPlayingQueue(songsInContext);

            //update current song index in playlist
            MusicTag song = tagRepos.findById(Long.parseLong(id));
            playbackService.loadPlayingQueue(song);
        } catch (Exception e) {
            //throw new RuntimeException(e);
            Log.e(TAG, "handlePlayFormContext", e);
        }
    }

    private void handlePlayPrevious() {
        if(playbackService != null) {
            playbackService.playPrevious();
        }
    }

    private void handlePlayNext() {
        if(playbackService != null) {
            playbackService.playNext();
        }
    }

    private void setRenderer(String udn) {
        if(playbackService != null) {
            playbackService.switchPlayer(udn);
        }
    }

    public Map<String, Object> sendDlnaRenderers() {
        if(playbackService ==null) return null;

        final String activeRendererUdn = playbackService.getPlayer()!=null?playbackService.getPlayer().getTargetId():"";
        List<Map<String, ?>> renderers = new ArrayList<>();
        // In a real app, this would come from your DLNA discovery service
        List<PlaybackTarget> rendererList = playbackService.getAvaiablePlaybackTargets();
        if(!rendererList.isEmpty()) {
            // to send a list of renderers
            renderers = rendererList.stream()
                    .map(device -> Map.of(
                            "name", device.getDisplayName(), // Assuming methods to get details
                            "udn", device.getTargetId(),
                            "active", activeRendererUdn.equals(device.getTargetId())
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
            tagRepos.addToPlayingQueue(song);
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

    public Map<String, Object> sendQueueUpdate() {
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

    public Map<String, Object> sendNowPlaying() {
        if(playbackService != null) {
            MediaTrack nowPlaying = playbackService.getNowPlayingSong();
            if (nowPlaying != null) {
                Map<String, Object> track = getMap(nowPlaying);

                return Map.of("type", "nowPlaying", "track", track);
            }
        }
        return null;
    }

    private Map<String, Object> sendBrowseResult (String path){
            // System.out.println("Browsing path: " + path);
            // In a real app, you would use the 'path' to query a database or filesystem.
            // Here, we'll return different mock data based on the path to simulate navigation.

            List<Map<String, ?>> items;
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
                List<MusicTag> songs = tagRepos.findByGenre(name);
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
                List<MusicTag> songs = tagRepos.findByArtist(name, 0, 0);
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
        private Map<String, Object> getMap(MediaTrack song) {
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
            if(!isEmpty(song.getYear())) {
                track.put("bitDepth", song.getYear());
            }

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

    public Map<String, Object> getPlaybackState(PlaybackState state) {
        long elapsed = state.currentPositionMs;
        MediaTrack tag = state.currentTrack;
        if(tag != null) {
            Map<String, Object> track = getMap(tag);
            track.put("elapsed", elapsed);
            track.put("state", state.currentState.name());

            //float[] waveform = getWaveform(tag);
            //track.put("waveform", waveform);

            return Map.of("type", "nowPlaying", "track", track);
        }
        return null;
    }

    private float[] getWaveform(MediaTrack tag) {
        float[] waveform = memoryCache.get(String.valueOf(tag.getId()));
        if(waveform == null) {
            waveform = MusicAnalyser.generateWaveform(context, tag, 480, 0.6);
            memoryCache.put(String.valueOf(tag.getId()), waveform);
        }
        return waveform;
    }
}
