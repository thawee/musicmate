package apincer.music.server.jupnp;


import static apincer.music.core.server.BaseServer.CONTENT_SERVER_PORT;
import static apincer.music.core.server.BaseServer.CONTEXT_PATH_MUSIC;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
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
import java.util.concurrent.atomic.AtomicInteger;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackServiceBinder;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.StringUtils;
import apincer.music.server.jupnp.playback.DMCAPlayer;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * DLNA Media Server consists of
 *  - Basic UPnp Framework (addressing, device discovery, content directory service, SOAP, eventing, etc.)
 *  - UPnP Content Directory Service
 *     - DLNA Digital Content Decoder
 *     - DLNA Digital Content Profiler
 *  - UPnP Connection Manager Service
 *  - HTTP Streamer - for streaming digital content to client
 *  - UPnp AV Transport Server (Not Supported)
 *  Note:
 *   - netty smooth, cpu < 10%, memory < 256 MB, short peak to 512 MB
 *     have issue jupnp auto stopping/starting on wifi loss
 *   - httpcore smooth, better SQ than netty, cpu <10, memory < 256 mb, peak 380mb
 *     have bug to stop playing on client sometime
 *   - jetty12 is faster than jetty11 12% but cannot run on android 34
 *   <p>
 *   Optimized for compatibility with DLNA clients like mConnectHD and RoPieeeXL
 *  </p>
 */
public class MediaServerHubImpl implements MediaServerHub {
    private static final String TAG = "MediaServerImpl";
    private static final UDAServiceType AV_TRANSPORT_TYPE = new UDAServiceType("AVTransport");

    // Standard DLNA Device Types
    public static final DeviceType MEDIA_RENDERER_DEVICE_TYPE = new UDADeviceType("MediaRenderer", 1);

    // --- Injected Dependencies ---
    private final Context context;
    protected UpnpServiceConfiguration upnpServiceCfg;
    private final FileRepository fileRepos;
    private final TagRepository tagRepos;

    //private final List<RendererDevice> renderers = new CopyOnWriteArrayList<>();
    public BehaviorSubject<ServerStatus> getServerStatus() {
        return serverStatus;
    }

    // Service components
    protected UpnpService upnpService;
    protected LocalDevice mediaServerDevice;
    private boolean initialized;

    private final BehaviorSubject<ServerStatus> serverStatus = BehaviorSubject.createDefault(ServerStatus.STOPPED);

    // Scheduler for polling tasks
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> positionInfoHandle;
    private ScheduledFuture<?> transportInfoHandle;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            PlaybackServiceBinder binder = (PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    public MediaServerHubImpl(Context context, UpnpServiceConfiguration upnpServiceCfg, FileRepository fileRepos, TagRepository tagRepos) {
        this.context = context;
        this.upnpServiceCfg = upnpServiceCfg;
        this.fileRepos = fileRepos;
        this.tagRepos = tagRepos;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("apincer.android.mmate", "apincer.android.mmate.service.PlaybackServiceImpl"));
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void stopServers() {
        Thread shutdownThread = new Thread(this::shutdown);
        shutdownThread.start();
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /*
    @Override
    public TagRepository getTagReRepository() {
        return tagRepos;
    }

    @Override
    public FileRepository getFileRepository() {
        return fileRepos;
    }

    @Override
    public PlaybackService getPlaybackService() {
        return playbackService;
    }
    */

    @Override
    public void startServers() {
        if(!isInitialized()) {
            long start = System.currentTimeMillis();
            Thread initializationThread = new Thread(() -> {
                initialize(); // Your existing initialize method
                if (isInitialized()) {
                    serverStatus.onNext(ServerStatus.RUNNING);
                    try {
                        // Re-register and announce
                        upnpService.getRegistry().addDevice(mediaServerDevice);
                        upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                        Log.i(TAG, "MediaServer announced after started");
                    } catch (Exception e) {
                        Log.e(TAG, "Error announcing MediaServer", e);
                    }
                } else {
                    serverStatus.onNext(ServerStatus.ERROR);
                }
            });
            initializationThread.setName("MediaServer-Init");
            initializationThread.start();

            Log.d(TAG, "MediaServer startup initiated: " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * Initialize the DLNA server
     */
    private synchronized void initialize() {
        if(!initialized) {
            shutdown(); // clean up before start

            try {
                //Log.d(TAG, "Initializing DLNA media server");

                // Set system properties BEFORE any jUPnP code is executed
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
                System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");

                // Create server configuration
               // upnpServiceCfg = new MediaServerConfiguration(context, this);
                upnpService = new UpnpServiceImpl(upnpServiceCfg) {
                    @Override
                    public synchronized void shutdown() {
                        // Now we can concurrently run the Cling shutdown code, without occupying the
                        // Android main UI thread. This will complete probably after the main UI thread
                        // is done.
                        super.shutdown(true);
                    }
                };
                // Start UPnP service
                upnpService.startup();

                //
                RegistryListener registryListener = new MyRegistryListener();

                // Create and register media server device
                mediaServerDevice = MediaServerDevice.createMediaServerDevice(context, tagRepos);
                upnpService.getRegistry().addDevice(mediaServerDevice);

                this.upnpService.getRegistry().addListener(registryListener);

                // Send alive notification
                upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                // Initial search for all devices
                this.upnpService.getControlPoint().search(MEDIA_RENDERER_DEVICE_TYPE.getVersion());

                // Mark as initialized
                this.initialized = true;
                Log.i(TAG, "MediaServer initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing MediaServer", e);
                shutdown();
            }
        }
    }

    private void shutdown() {
        try {

            // Send byebye notification before shutting down
            if (mediaServerDevice != null && upnpService != null) {
                upnpService.getProtocolFactory().createSendingNotificationByebye(mediaServerDevice).run();
            }
            if(upnpService != null) {
                upnpService.getRegistry().removeAllRemoteDevices(); // Optional: clear devices before shutdown
                upnpService.shutdown();
                upnpService = null;
            }
            serverStatus.onNext(ServerStatus.STOPPED);

        } catch (Exception e) {
            Log.e(TAG, "Error shutting down UPnP service", e);
        }
        initialized = false;
    }

    public class MyRegistryListener extends DefaultRegistryListener {

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "Discovery started for: " + device.getDisplayString());
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            //Log.e(TAG, "Discovery failed for: " + device.getDisplayString() + " => " + ex.getMessage(), ex);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            Log.i(TAG, "Remote device available: " + device.getDisplayString() + " (" + device.getType().getType() + ")");

            if (device.getType().equals(MEDIA_RENDERER_DEVICE_TYPE)) {
                if(playbackService!= null) {
                    PlaybackTarget player = DMCAPlayer.Factory.create(context, MediaServerHubImpl.this, device);
                    playbackService.getAvaiablePlaybackTargets().add(player);
                }
            }
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            super.remoteDeviceUpdated(registry, device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            if(playbackService!= null) {
                PlaybackTarget player = DMCAPlayer.Factory.create(context, MediaServerHubImpl.this, device);
                playbackService.getAvaiablePlaybackTargets().remove(player);
            }
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
           // Log.i(TAG, "Local device added: " + device.getDisplayString());
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
           // Log.i(TAG, "Local device removed: " + device.getDisplayString());
        }

        @Override
        public void beforeShutdown(Registry registry) {
           // Log.i(TAG, "Registry about to shut down.");
        }

        @Override
        public void afterShutdown() {
           // Log.i(TAG, "Registry has shut down.");
        }
    }

    /**
     * Pauses playback on the specified renderer.
     * @param rendererUdn The UDN of the target renderer.
     */
    @Override
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
    @Override
    public void play(String rendererUdn) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            System.err.println("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            // System.err.println("Renderer does not have an AVTransport service.");
            return;
        }

        ControlPoint controlPoint = upnpService.getControlPoint();
        // The "Play" action requires a speed parameter, "1" is normal playback.
        controlPoint.execute(new Play(avTransportService) {
            @Override
            public void success(ActionInvocation invocation) {
                // System.out.println("Play command successful.");
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                // System.err.println("Play command failed: " + defaultMsg);
            }
        });
    }

    @Override
    public void playSong(String rendererUdn, MediaTrack song) {
        // Step 1: Find the target device (renderer) by its UDN
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            System.err.println("Renderer with UDN " + rendererUdn + " not found.");
            return;
        }

        // Step 2: Get the AVTransport service from the device
        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
        if (avTransportService == null) {
            System.err.println("Renderer does not have an AVTransport service.");
            return;
        }

        // --- Create the URL for the song ---
        // This URL must point to your app's internal HTTP server.
        String songUrl = "http://"+ BaseServer.getIpAddress()+":"+  CONTENT_SERVER_PORT+CONTEXT_PATH_MUSIC + song.getId() + "/file." + song.getFileType();

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
                        // System.out.println("Play command successful.");
                        //TODO start polling
                        startPolling(rendererUdn);
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        // System.err.println("Play command failed: " + defaultMsg);
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                //  System.err.println("SetAVTransportURI failed: " + defaultMsg);
            }
        });
    }

    /**
     * Finds a service recursively within a device and its embedded devices.
     */
    private Service findServiceRecursively(Device device, UDAServiceType serviceType) {
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

    @Override
    public void fetchPlayerState(String rendererUdn) {
        Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
        if (device == null) {
            return;
        }

        Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
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

                String title = songItem.getTitle();
                String artist = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class).getName();
                String album = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class).toString();
                MusicTag song = tagRepos.findMediaItem(title, artist,album);
                playbackService.notifyNewTrackPlaying(song);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            }
        });
    }

    /**
     * Starts polling the renderer for status and position updates.
     * @param rendererUdn The UDN of the renderer to poll.
     */
    private void startPolling(String rendererUdn) {
        int maxFails = 5;
        if (isPolling.compareAndSet(false, true)) {
            scheduler = Executors.newScheduledThreadPool(2); // One thread for each task
            final AtomicInteger count = new AtomicInteger();
            // Task to get the current time position, polling every 3 seconds
            final Runnable positionInfoPoller = () -> getPositionInfo(rendererUdn, new PositionInfoCallback() {
                @Override
                public void onReceived(PositionInfo positionInfo) {
                    count.set(0);
                    playbackService.notifyPlaybackStateElapsedTime(parseHmsToSeconds(positionInfo.getRelTime()));
                }

                @Override
                public void onFailure(String message) {
                    System.err.println("Polling position info failed: " + message);
                    int cnt = count.incrementAndGet();
                    if(cnt > maxFails) {
                        stopPolling();
                        System.err.println("Maximum polling failed: stop polling.");
                    }
                }
            });

            positionInfoHandle = scheduler.scheduleWithFixedDelay(positionInfoPoller, 0, 2, TimeUnit.SECONDS);

            // Task to get the current transport status, polling every 1 minute
            final Runnable transportInfoPoller = () -> getTransportInfo(rendererUdn, new TransportInfoCallback() {
                @Override
                public void onReceived(TransportInfo transportInfo) {

                   // getPlaybackService().setNowPlayingState(transportInfo.getCurrentSpeed(), transportInfo.getCurrentTransportState().getValue());
                    // System.out.println("Transport State: " + transportInfo.getCurrentTransportState().getValue());
                  /*  BehaviorSubject<NowPlaying> nowPlayingSubject = playbackService.getNowPlayingSubject();
                    NowPlaying nowPlaying = nowPlayingSubject.getValue();
                    if (nowPlaying != null) {
                        nowPlaying.setPlayingSpeed(transportInfo.getCurrentSpeed());
                        nowPlaying.setPlayingState(transportInfo.getCurrentTransportState().getValue());
                        nowPlayingSubject.onNext(nowPlaying);
                    } */
                }

                @Override
                public void onFailure(String message) {
                    System.err.println("Polling transport info failed: " + message);
                }
            });

           // transportInfoHandle = scheduler.scheduleWithFixedDelay(transportInfoPoller, 0, 30, TimeUnit.SECONDS);
            // System.out.println("Started polling renderer: " + rendererUdn);
        } else {
            System.out.println("Polling is already active.");
        }
    }

    /**
     * Stops all polling tasks and shuts down the scheduler.
     */
    private void stopPolling() {
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
            //  System.out.println("Stopped polling.");
        }
    }

    /**
     * Creates a rich DIDL-Lite XML metadata string for a given song.
     *
     * @param song The MusicTag object for the song.
     * @param songUrl The URL where the song can be streamed from.
     * @return A well-formed DIDL-Lite XML string.
     */
    private String createDidlLiteMetadata(MediaTrack song, String songUrl) {
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

    /**
     * Gets the current transport information (e.g., status like PLAYING, PAUSED_PLAYBACK).
     * @param rendererUdn The UDN of the target renderer.
     * @param callback    The callback to handle the response.
     */
    private void getTransportInfo(String rendererUdn, TransportInfoCallback callback) {
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
    private void getPositionInfo(String rendererUdn, PositionInfoCallback callback) {
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

    @Override
    public void onDestroy() {
        if(playbackService != null) {
            context.unbindService(serviceConnection);
            playbackService = null;
            isPlaybackServiceBound = false;
        }
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
}
