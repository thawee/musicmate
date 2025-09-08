package apincer.android.mmate.dlna;

import static apincer.android.mmate.dlna.MediaServerConfiguration.CONTENT_SERVER_PORT;

import org.jupnp.UpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.support.avtransport.callback.GetMediaInfo;
import org.jupnp.support.avtransport.callback.GetPositionInfo;
import org.jupnp.support.avtransport.callback.GetTransportInfo;
import org.jupnp.support.avtransport.callback.Pause;
import org.jupnp.support.avtransport.callback.Play;
import org.jupnp.support.avtransport.callback.SetAVTransportURI;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.MediaInfo;
import org.jupnp.support.model.PositionInfo;
import org.jupnp.support.model.TransportInfo;
import org.jupnp.support.model.item.Item;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.mmate.utils.StringUtils;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class RendererController {
    // Scheduler for polling tasks
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> positionInfoHandle;
    private ScheduledFuture<?> transportInfoHandle;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private PlaybackService playbackService;

    public RendererController(UpnpService upnpService) {
        this.upnpService = upnpService;
    }

    // Assuming upnpService is initialized and available
    private final org.jupnp.UpnpService upnpService;
    public static final UDAServiceType AV_TRANSPORT_TYPE = new UDAServiceType("AVTransport");

    public void setupRender(String rendererUdn) {
        stopPolling();
        getCurrentSong(rendererUdn);
        startPolling(rendererUdn);
    }

    public void setPlaybackService(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    // --- Interfaces for Callbacks ---
    public interface TransportInfoCallback {
        void onReceived(TransportInfo transportInfo);
        void onFailure(String message);
    }

    public interface PositionInfoCallback {
        void onReceived(PositionInfo positionInfo);
        void onFailure(String message);
    }

    /**
     * Finds a service recursively within a device and its embedded devices.
     */
    public Service findServiceRecursively(Device device, UDAServiceType serviceType) {
        if (device == null) return null;

        Service service = device.findService(serviceType);
        if (service != null) {
            return service;
        }

        for (Device embeddedDevice : device.getEmbeddedDevices()) {
            service = findServiceRecursively(embeddedDevice, serviceType);
            if (service != null) {
                return service;
            }
        }
        return null;
    }

    // ===================================================================================
    // ORIGINAL FUNCTION
    // ===================================================================================

    /**
     * Creates a rich DIDL-Lite XML metadata string for a given song.
     *
     * @param song The MusicTag object for the song.
     * @param songUrl The URL where the song can be streamed from.
     * @return A well-formed DIDL-Lite XML string.
     */
    private String createDidlLiteMetadata(MusicTag song, String songUrl) {
        String objectClass = "object.item.audioItem.musicTrack";
        String duration = formatDurationForDidl((long) song.getAudioDuration());
        String bitrate = String.valueOf(song.getAudioBitRate() * 1024 / 8); // bps to Bps
        String sampleRate = String.valueOf(song.getAudioSampleRate());
        String bitsPerSample = String.valueOf(song.getAudioBitsDepth());
        String mimeType = MimeTypeUtils.getMimeTypeFromPath(song.getPath());

        // DIDL-Lite is an XML format, so escape any special characters in titles, etc.
        String title = StringUtils.escapeXml(song.getTitle());
        String artist = StringUtils.escapeXml(song.getArtist());
        String album = StringUtils.escapeXml(song.getAlbum());

        // Example DIDL-Lite structure

        return "<DIDL-Lite " +
                "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"" + song.getId() + "\" parentID=\"0\" restricted=\"1\">" +
                "<dc:title>" + title + "</dc:title>" +
                "<upnp:artist>" + artist + "</upnp:artist>" +
                "<upnp:album>" + album + "</upnp:album>" +
                "<upnp:class>" + objectClass + "</upnp:class>" +
                "<res protocolInfo=\"http-get:*:" + mimeType + ":*\"" +
                " duration=\"" + duration + "\"" +
                " bitrate=\"" + bitrate + "\"" +
                " sampleFrequency=\"" + sampleRate + "\"" +
                " bitsPerSample=\"" + bitsPerSample + "\">" +
                songUrl +
                "</res>" +
                "</item>" +
                "</DIDL-Lite>";
    }

    /**
     * Formats duration from milliseconds into H:MM:SS.ms format for DIDL-Lite.
     * @param durationInMillis The duration in milliseconds.
     * @return A formatted string e.g., "0:04:33.000"
     */
    private String formatDurationForDidl(long durationInMillis) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMillis) % 60;
        long millis = durationInMillis % 1000;
        return String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    // ===================================================================================
    // NEW FUNCTIONS
    // ===================================================================================

    /**
     * Pauses playback on the specified renderer.
     * @param rendererUdn The UDN of the target renderer.
     */
    public void pause(String rendererUdn) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            System.err.println("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            System.err.println("Renderer does not have an AVTransport service.");
            return;
        }

        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new Pause(avTransportService) {
            @Override
            public void success(ActionInvocation invocation) {
                System.out.println("Pause command successful.");
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                System.err.println("Pause command failed: " + defaultMsg);
            }
        });
    }

    /**
     * Resumes (or starts) playback on the specified renderer.
     * @param rendererUdn The UDN of the target renderer.
     */
    public void play(String rendererUdn) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            System.err.println("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            System.err.println("Renderer does not have an AVTransport service.");
            return;
        }

        ControlPoint controlPoint = upnpService.getControlPoint();
        // The "Play" action requires a speed parameter, "1" is normal playback.
        controlPoint.execute(new Play(avTransportService) {
            @Override
            public void success(ActionInvocation invocation) {
                System.out.println("Play command successful.");
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                System.err.println("Play command failed: " + defaultMsg);
            }
        });
    }

    public void playSong(String rendererUdn, MusicTag song) {
        // Step 1: Find the target device (renderer) by its UDN
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        // Device device = MusixMateApp.getInstance().getRendererByUDN(rendererUdn);
        if (device == null) {
            System.err.println("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        // Step 2: Get the AVTransport service from the device
        org.jupnp.model.meta.Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            System.err.println("Renderer does not have an AVTransport service.");
            return;
        }

        // --- Create the URL for the song ---
        // This URL must point to your app's internal HTTP server.
        String songUrl = "http://"+ StreamServerImpl.streamServerHost+":"+  CONTENT_SERVER_PORT+"/res/" + song.getId() + "/file." + song.getFileType();

        // Create a simple metadata string for the renderer (optional but recommended)
        String metadata = createDidlLiteMetadata(song, songUrl);

        // Step 3: Set the song URL on the renderer
        // This is an asynchronous action, so we use a callback.
        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new SetAVTransportURI(avTransportService, songUrl, metadata) {
            @Override
            public void success(ActionInvocation invocation) {
               // System.out.println("Successfully set URI. Now playing...");

                // Step 4: After the URI is set successfully, send the Play command
                controlPoint.execute(new Play(avTransportService) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        System.out.println("Play command successful.");
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        System.err.println("Play command failed: " + defaultMsg);
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                System.err.println("SetAVTransportURI failed: " + defaultMsg);
            }
        });
    }

    /**
     * Gets the current transport information (e.g., status like PLAYING, PAUSED_PLAYBACK).
     * @param rendererUdn The UDN of the target renderer.
     * @param callback    The callback to handle the response.
     */
    public void getTransportInfo(String rendererUdn, TransportInfoCallback callback) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            callback.onFailure("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            callback.onFailure("Renderer does not have an AVTransport service.");
            return;
        }

        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new GetTransportInfo(avTransportService) {
            @Override
            public void received(ActionInvocation invocation, TransportInfo transportInfo) {
                callback.onReceived(transportInfo);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                callback.onFailure("GetTransportInfo failed: " + defaultMsg);
            }
        });
    }

    /**
     * Gets position information (e.g., elapsed time, track duration).
     * @param rendererUdn The UDN of the target renderer.
     * @param callback    The callback to handle the response.
     */
    public void getPositionInfo(String rendererUdn, PositionInfoCallback callback) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            callback.onFailure("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            callback.onFailure("Renderer does not have an AVTransport service.");
            return;
        }

        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new GetPositionInfo(avTransportService) {
            @Override
            public void received(ActionInvocation invocation, PositionInfo positionInfo) {
                callback.onReceived(positionInfo);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                callback.onFailure("GetPositionInfo failed: " + defaultMsg);
            }
        });
    }

    /**
     * Example of how to use the new methods.
     */
    public void exampleUsage(String rendererUdn) {
        // Get the current status
        getTransportInfo(rendererUdn, new TransportInfoCallback() {
            @Override
            public void onReceived(TransportInfo transportInfo) {
                System.out.println("Current Status: " + transportInfo.getCurrentTransportState().getValue());
                System.out.println("Current Speed: " + transportInfo.getCurrentSpeed());
            }

            @Override
            public void onFailure(String message) {
                System.err.println(message);
            }
        });

        // Get the current time position
        getPositionInfo(rendererUdn, new PositionInfoCallback() {
            @Override
            public void onReceived(PositionInfo positionInfo) {
                System.out.println("Elapsed Time: " + positionInfo.getRelTime());
                System.out.println("Track Duration: " + positionInfo.getTrackDuration());
            }

            @Override
            public void onFailure(String message) {
                System.err.println(message);
            }
        });
    }

    /**
     * Starts polling the renderer for status and position updates.
     * @param rendererUdn The UDN of the renderer to poll.
     */
    public void startPolling(String rendererUdn) {
        if (isPolling.compareAndSet(false, true)) {
            scheduler = Executors.newScheduledThreadPool(2); // One thread for each task

            // Task to get the current time position, polling every 3 seconds
            final Runnable positionInfoPoller = () -> getPositionInfo(rendererUdn, new RendererController.PositionInfoCallback() {
                @Override
                public void onReceived(PositionInfo positionInfo) {

                    BehaviorSubject<NowPlaying> nowPlayingSubject = playbackService.getNowPlayingSubject();
                    NowPlaying nowPlaying = nowPlayingSubject.getValue();
                    if (nowPlaying != null) {
                        nowPlaying.setElapsed(parseHmsToSeconds(positionInfo.getRelTime()));
                        nowPlayingSubject.onNext(nowPlaying);
                     //   playbackService.onNewTrackPlaying(nowPlaying.getPlayer(), nowPlaying.getSong(), nowPlaying.getElapsed());
                    }
                }

                @Override
                public void onFailure(String message) {
                    System.err.println("Polling position info failed: " + message);
                }
            });

            positionInfoHandle = scheduler.scheduleWithFixedDelay(positionInfoPoller, 0, 2, TimeUnit.SECONDS);

            // Task to get the current transport status, polling every 1 minute
            final Runnable transportInfoPoller = () -> getTransportInfo(rendererUdn, new RendererController.TransportInfoCallback() {
                @Override
                public void onReceived(TransportInfo transportInfo) {
                    // System.out.println("Transport State: " + transportInfo.getCurrentTransportState().getValue());
                    BehaviorSubject<NowPlaying> nowPlayingSubject = playbackService.getNowPlayingSubject();
                    NowPlaying nowPlaying = nowPlayingSubject.getValue();
                    if (nowPlaying != null) {
                        nowPlaying.setPlayingSpeed(transportInfo.getCurrentSpeed());
                        nowPlaying.setPlayingState(transportInfo.getCurrentTransportState().getValue());
                        nowPlayingSubject.onNext(nowPlaying);
                    }
                }

                @Override
                public void onFailure(String message) {
                    System.err.println("Polling transport info failed: " + message);
                }
            });

            transportInfoHandle = scheduler.scheduleWithFixedDelay(transportInfoPoller, 0, 30, TimeUnit.SECONDS);
            System.out.println("Started polling renderer: " + rendererUdn);
        } else {
            System.out.println("Polling is already active.");
        }
    }

    /**
     * Parses a time string in "HH:MM:SS" format to total seconds.
     * @param hms The time string.
     * @return The total number of seconds as a long.
     */
    private long parseHmsToSeconds(String hms) {
        if (hms == null || hms.isEmpty() || hms.equals("NOT_IMPLEMENTED")) {
            return 0;
        }
        String[] parts = hms.split(":");
        if (parts.length != 3) {
            return 0; // Or throw an exception
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            // The seconds part might have fractional values, e.g., "SS.ms"
            double secondsDouble = Double.parseDouble(parts[2]);
            long seconds = (long) secondsDouble;

            return (hours * 3600) + (minutes * 60) + seconds;
        } catch (NumberFormatException e) {
            System.err.println("Could not parse time string: " + hms);
            return 0;
        }
    }

    private void getCurrentSong(String rendererUdn) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            return;
        }

        org.jupnp.model.meta.Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            return;
        }
        upnpService.getControlPoint().execute(new GetMediaInfo(avTransportService) {
            @Override
            public void received(ActionInvocation invocation, MediaInfo mediaInfo) {
                // The metadata is an XML string. We need to parse it.
                String metadata = mediaInfo.getCurrentURIMetaData();
                Item songItem = null;

                if (metadata != null && !metadata.isEmpty()) {
                    try {
                        DIDLParser parser = new DIDLParser();
                        DIDLContent didl = parser.parse(metadata);
                        if (!didl.getItems().isEmpty()) {
                            // The first item in the list is the current track
                            songItem = didl.getItems().get(0);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing DIDL metadata: " + e.getMessage());
                    }
                }
                BehaviorSubject<NowPlaying> nowPlayingSubject = playbackService.getNowPlayingSubject();
                NowPlaying nowPlaying = nowPlayingSubject.getValue();
                if(nowPlaying != null) {
                    String title = songItem.getTitle();
                    String artist = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class).getName();
                    String album = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class).toString();
                    MusicTag currentSong = TagRepository.findMediaItem(title, artist,album);
                    nowPlaying.setSong(currentSong);
                    nowPlayingSubject.onNext(nowPlaying);
                }
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            }
        });
    }

    /**
     * Stops all polling tasks and shuts down the scheduler.
     */
    public void stopPolling() {
        if (isPolling.compareAndSet(true, false)) {
            if (positionInfoHandle != null) {
                positionInfoHandle.cancel(true);
            }
            if (transportInfoHandle != null) {
                transportInfoHandle.cancel(true);
            }
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                }
            }
            System.out.println("Stopped polling.");
        }
    }
}