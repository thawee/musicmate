package apincer.music.server.jupnp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.gena.GENASubscription;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.state.StateVariableValue;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.RegistryListener;
import org.jupnp.support.avtransport.callback.GetPositionInfo;
import org.jupnp.support.avtransport.callback.Play;
import org.jupnp.support.avtransport.callback.SetAVTransportURI;
import org.jupnp.support.avtransport.callback.Stop;
import org.jupnp.support.model.PositionInfo;
import org.jupnp.support.model.ProtocolInfos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.Constants;
import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackCallback;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.StringUtils;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * Core implementation of the {@link MediaServerHub} providing UPnP/DLNA integration.
 * * <p>This implementation functions as a dual-mode engine:
 * <ul>
 * <li><b>Digital Media Server (DMS):</b> Advertises the local Android library to the network.</li>
 * <li><b>Control Point (CP):</b> Discovers and manages remote Digital Media Renderers (DMR).</li>
 * </ul>
 * * <p><b>Key Features:</b>
 * <ul>
 * <li><b>Persistence:</b> Manages Power, Wi-Fi, and Multicast locks for stable background streaming.</li>
 * <li><b>Network Awareness:</b> Automatically restarts on Wi-Fi handovers via {@link ConnectivityManager}.</li>
 * <li><b>Hybrid Sync:</b> Utilizes GENA event subscriptions for state changes and high-frequency
 * polling for playback position accuracy.</li>
 * <li><b>Metadata Engine:</b> Generates DIDL-Lite XML compliant with audiophile renderer standards.</li>
 * </ul>
 * * @author Thawee Prakaipetch
 * @version 2026.03.18
 */
public class MediaServerHubImpl implements MediaServerHub {

    private static final String TAG = "MediaServerHub";

    private static final UDAServiceType AV_TRANSPORT_TYPE = new UDAServiceType("AVTransport");
    public static final DeviceType MEDIA_RENDERER_DEVICE_TYPE = new UDADeviceType("MediaRenderer", 1);
    private static final Pattern STATE_PATTERN = Pattern.compile("TransportState\\s*val(?:ue)?\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern POS_PATTERN = Pattern.compile("RelativeTimePosition\\s*val(?:ue)?\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private final Context context;

    protected UpnpServiceConfiguration cfg;
    private final FileRepository fileRepos;
    private final TagRepository tagRepos;

    // UPnP core
    private volatile Network currentNetwork;
    private volatile boolean wifiAvailable = false;

    private UpnpService upnpService;
    private LocalDevice mediaServerDevice;
    private ControlPoint controlPoint;
    private Service currentAVTransport; // Add this line
    PlaybackCallback playbackCallback;

    private final BehaviorSubject<ServerStatus> serverStatus = BehaviorSubject.createDefault(ServerStatus.RUNNING);

    private final Map<String, PlaybackTarget> localTargets = new ConcurrentHashMap<>();
    private final Set<String> subscribedDevices = new HashSet<>();

    private org.jupnp.model.meta.RemoteDevice currentRenderer;
    private String currentRendererId;
    private org.jupnp.model.gena.GENASubscription activeSubscription;
    private org.jupnp.controlpoint.SubscriptionCallback subscriptionCallback;
    private boolean supportsGapless = true; // Default to true, then 'learn' otherwise
    private volatile long lastEventTime = 0;
    private static final long EVENT_TIMEOUT_MS = 4000; // 4 s
    private enum SyncMode {
        GENA,
        POLLING
    }

    private volatile SyncMode syncMode = SyncMode.GENA;

    // 5 minutes in milliseconds
    private static final long PAUSE_TIMEOUT_MS = 5 * 60 * 1000;
    private ScheduledFuture<?> pauseTimeoutTask;

    private long lastPosition = -1;
    private int stagnantCount = 0;

    // Thread model (IMPORTANT)
    private final ExecutorService upnpExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "UPnP-Worker");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

  private enum State {
      IDLE,
      STARTING,
      RUNNING,
      STOPPING
  }

    private final Object stateLock = new Object();
    private volatile State state = State.IDLE;

    // Network
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Locks
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;

    private long lastDiscoveryTime = 0;

    /**
     * Initializes the hub with required repositories and network configuration.
     * * @param context The application context for system services.
     * @param upnpServiceCfg Configuration for the jUPnP stack.
     * @param fileRepos Repository for physical file access.
     * @param tagRepos Repository for track metadata and analysis results.
     */
    public MediaServerHubImpl(Context context, UpnpServiceConfiguration upnpServiceCfg, FileRepository fileRepos, TagRepository tagRepos) {
        this.context = context;
        this.cfg = upnpServiceCfg;
        this.fileRepos = fileRepos;
        this.tagRepos = tagRepos;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // =========================================================
    // THREAD HELPER
    // =========================================================

    private void runOnUpnpThread(Runnable task) {
        upnpExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Log.e(TAG, "UPnP task failed", e);
            }
        });
    }

    // =========================================================
    // START
    // =========================================================

    /**
     * Starts the UPnP service stack, acquires system locks, and begins device discovery.
     * This operation is thread-safe and asynchronous.
     */
    @Override
    public void start() {
        synchronized (stateLock) {
            if (state != State.IDLE) {
                Log.d(TAG, "Start ignored, state=" + state);
                return;
            }
            state = State.STARTING;
        }

        runOnUpnpThread(() -> {
            try {
                acquireLocks();
                initUpnp();

                startNetworkMonitoring();
                startPeriodicDiscovery();

                synchronized (stateLock) {
                    state = State.RUNNING;
                }

                Log.i(TAG, "UPnP started");

            } catch (Exception e) {
                Log.e(TAG, "Start failed", e);
                synchronized (stateLock) {
                    state = State.IDLE;
                }
            }
        });
    }

    private void initUpnp() throws Exception {
        upnpService = new UpnpServiceImpl(cfg);
        upnpService.startup();

        controlPoint = upnpService.getControlPoint();

        RegistryListener listener = new SimpleRegistryListener();
        upnpService.getRegistry().addListener(listener);

        mediaServerDevice = MediaServerDeviceFactory.create(context, tagRepos);
        upnpService.getRegistry().addDevice(mediaServerDevice);

        sendAlive();
        triggerDiscovery();
    }

    // =========================================================
    // STOP
    // =========================================================

    /**
     * Gracefully shuts down the UPnP stack, releases all system locks, and notifies
     * the network of the device's departure (SSDP Bye-Bye).
     */
    @Override
    public void stop() {
        synchronized (stateLock) {
            if (state != State.RUNNING) {
                Log.d(TAG, "Stop ignored, state=" + state);
                return;
            }
            state = State.STOPPING;
        }

        runOnUpnpThread(() -> {
            try {
                stopPeriodicDiscovery();
                stopNetworkMonitoring();

                if (upnpService != null) {
                    try { sendByebye(); } catch (Exception ignored) {}
                    try { upnpService.shutdown(); } catch (Exception ignored) {}
                    upnpService = null;
                }

                currentRenderer = null;
                currentRendererId = null;
                currentAVTransport = null;

                Log.i(TAG, "UPnP stopped");

            } finally {
                releaseLocks();

                synchronized (stateLock) {
                    state = State.IDLE;
                }
            }
        });
    }

    public BehaviorSubject<ServerStatus> getStatus() {
        return serverStatus;
    }

    @Override
    public String getLibraryNames() {
        // read from libraries.info
        String info = null;
        try {
            info = ApplicationUtils.readFileOnAndroidFilesDir(context, Constants.LIBRARIES_INFO_FILE);
        } catch (Exception ignored) {

        }
        return StringUtils.trim(info, " - ");
    }

    // =========================================================
    // DISCOVERY
    // =========================================================

    private ScheduledFuture<?> discoveryTask;

    private void startPeriodicDiscovery() {
        discoveryTask = scheduler.scheduleWithFixedDelay(
                this::triggerDiscovery,
                5,
                30,
                TimeUnit.SECONDS
        );
    }

    private void stopPeriodicDiscovery() {
        if (discoveryTask != null) {
            discoveryTask.cancel(true);
        }
    }

    private void triggerDiscovery( ) {
      //  boolean aggressive = true;
        runOnUpnpThread(() -> {
            //  if (!aggressive && now - lastDiscoveryTime < 5000) return;

            lastDiscoveryTime = System.currentTimeMillis();

            if (controlPoint == null) return;

            try {
                // 🔥 normal search
                controlPoint.search();

            } catch (Exception e) {
                Log.w(TAG, "Discovery failed", e);
            }
        });
    }

    // =========================================================
    // SSDP NOTIFY
    // =========================================================

    private void sendAlive() {
        runOnUpnpThread(() -> {
            if (upnpService != null && mediaServerDevice != null) {
                upnpService.getProtocolFactory()
                        .createSendingNotificationAlive(mediaServerDevice)
                        .run();
            }
        });
    }

    private void sendByebye() {
        runOnUpnpThread(() -> {
            if (upnpService != null && mediaServerDevice != null) {
                upnpService.getProtocolFactory()
                        .createSendingNotificationByebye(mediaServerDevice)
                        .run();
            }
        });
    }

    // =========================================================
    // NETWORK MONITOR
    // =========================================================

    @SuppressLint("MissingPermission")
    private void startNetworkMonitoring() {
        if (networkCallback != null) return; // ✅ prevent duplicate

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "WiFi available");
                if (network.equals(currentNetwork)) {
                    Log.d(TAG, "Same network → ignore");
                    return;
                }

                currentNetwork = network;
                wifiAvailable = true;

                scheduler.schedule(() -> evaluateNetworkState(), 1, TimeUnit.SECONDS);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "WiFi lost");

                if (!network.equals(currentNetwork)) return;

                wifiAvailable = false;
                currentNetwork = null;

                evaluateNetworkState();
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void evaluateNetworkState() {
        synchronized (stateLock) {
            Log.d(TAG, "Evaluate → state=" + state + ", wifi=" + wifiAvailable);

            if (wifiAvailable) {
                if (state == State.IDLE) {
                    Log.d(TAG, "Network OK → starting");
                    start();
                }
            } else {
                if (state == State.RUNNING) {
                    Log.d(TAG, "Network lost → stopping");
                    stop();
                }
            }
        }
    }

    private void stopNetworkMonitoring() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    // =========================================================
    // LOCKS
    // =========================================================

    private void acquireLocks() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (pm != null && wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MM:Wake");
            wakeLock.acquire(10*60*1000L /*10 minutes*/);
        }

        if (wm != null && wifiLock == null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MM:Wifi");
            wifiLock.acquire();
        }

        if (wm != null && multicastLock == null) {
            multicastLock = wm.createMulticastLock("MM:Multicast");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    private void releaseLocks() {
        try { if (wakeLock != null) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null) wifiLock.release(); } catch (Exception ignored) {}
        try { if (multicastLock != null) multicastLock.release(); } catch (Exception ignored) {}

        wakeLock = null;
        wifiLock = null;
        multicastLock = null;
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void release() {
        stop();
        upnpExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    private RemoteDevice resolveRenderer(String rendererId) {
        if (upnpService == null) return null;

        Device device = upnpService.getRegistry().getDevice(new UDN(rendererId), false);
        if (device instanceof RemoteDevice) {
            return (RemoteDevice) device;
        }
        return null;
    }

    @Override
    public void playerActivate(String rendererId, PlaybackCallback callback) {
        this.playbackCallback = callback;

        runOnUpnpThread(() -> {
            // prevent re-activation
            if (rendererId.equals(currentRendererId) && activeSubscription != null) {
                Log.d(TAG, "Already activated: " + rendererId);
                return;
            }

            currentRenderer = resolveRenderer(rendererId);
            currentRendererId = rendererId;
            stagnantCount = -1;
            lastPosition = -1;

            if (currentRenderer != null) {
                // Save the service to the class-level variable here!
                currentAVTransport = findServiceRecursively(currentRenderer, AV_TRANSPORT_TYPE);

                if (currentAVTransport != null) {
                    subscribeToRenderer(currentAVTransport);
                }
            }
            Log.d(TAG, "Activated & Subscribed: " + rendererId);
        });
    }

    @Override
    public void playerDeactivate(String udn) {
        runOnUpnpThread(() -> {
            unsubscribeIfNeeded();   // real unsubscribe
            stopPolling();
            cancelPauseTimeout();
            currentAVTransport = null;
            currentRenderer = null;
            currentRendererId = null;
        });
    }

    @Override
    public List<PlaybackTarget> getPlaybackTargets() {
        List<PlaybackTarget> targets = new ArrayList<>(localTargets.values());
        List<RemoteDevice> devices = getMediaRenderers();
        for(RemoteDevice device: devices) {
            String udn = device.getIdentity().getUdn().getIdentifierString();
            String  displayName = device.getDetails().getFriendlyName();
            String  location = device.getIdentity().getDescriptorURL().getHost();
            PlaybackTarget player = DMRPlayer.Factory.create(udn, displayName, location);
           // checkHighResSupport(device, (DMRPlayer) player);
            targets.add(player);
        }
        return targets;
    }

    @Override
    public void addLocalPlaybackTarget(PlaybackTarget playbackTarget, boolean purgeExisting) {
        if(purgeExisting) {
            localTargets.clear();
        }
        if(playbackTarget != null) {
            localTargets.put(playbackTarget.getTargetId(), playbackTarget);
        }
    }

    public List<RemoteDevice> getMediaRenderers() {
        if (upnpService == null) return Collections.emptyList();

        List<RemoteDevice> result = new ArrayList<>();

        try {
            List<Device> devices = new ArrayList<>(upnpService.getRegistry().getDevices());

            for (Device device : devices) {
                if (device instanceof RemoteDevice &&
                        MEDIA_RENDERER_DEVICE_TYPE.equals(device.getType())) {

                    result.add((RemoteDevice) device);
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    /**
     * Commands a remote renderer to play a specific track.
     * Performs a multi-step sequence: Resolves Renderer -> Sets Transport URI -> Sends Play Command.
     * @param song The {@link Track} containing metadata and stream information.
     */
    @Override
    public void playerPlaySong(Track song) {
        runOnUpnpThread(() -> {
            if(currentRendererId != null) {
                internalPlaySong(currentRendererId, song);
            }
        });
    }

    /**
     * Commands a remote renderer to play a specific track.
     * Performs a multi-step sequence: Resolves Renderer -> Sets Transport URI -> Sends Play Command.
     * * @param udn The Unique Device Name of the target renderer.
     * @param song The {@link Track} containing metadata and stream information.
     */
    @Override
    public void playerPlaySong(String udn, Track song) {
        runOnUpnpThread(() -> {
            internalPlaySong(udn, song);
        });
    }

    private void internalPlaySong(String udn, Track song) {
        if (upnpService == null) {
            Log.w(TAG, "UPnP not initialized");
            return;
        }

        //playerActivate(udn, null);
        RemoteDevice renderer = resolveRenderer(udn);
        if (renderer == null) {
            Log.w(TAG, "Renderer not found: " + udn);
            return;
        }

        // Step 2: Get the AVTransport service from the device
        currentAVTransport = findServiceRecursively(renderer, AV_TRANSPORT_TYPE);
        if (currentAVTransport == null) {
            Log.i(TAG, TAG+" - Renderer does not have an AVTransport service.");
            return;
        }

        // --- Create the URL for the song ---
        // This URL must point to your app's internal HTTP server.
        // String songUrl = "http://"+ NetworkUtils.getIpAddress()+":"+  CONTENT_SERVER_PORT+CONTEXT_PATH_MUSIC + song.getId() + "/file." + song.getFileType();
        String songUrl = BaseServer.getMusicUrl(song);

        // Create a simple metadata string for the renderer (optional but recommended)
        String metadata = createDidlLiteMetadata(song, songUrl);

        // Step 3: Set the song URL on the renderer
        // This is an asynchronous action, so we use a callback.
        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new SetAVTransportURI(currentAVTransport, songUrl, metadata) {
            @Override
            public void success(ActionInvocation invocation) {
                // System.out.println("Successfully set URI. Now playing...");

                // Step 4: After the URI is set successfully, send the Play command
                controlPoint.execute(new Play(currentAVTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        // Force the UI to reflect "Playing" immediately
                        serverStatus.onNext(ServerStatus.CAST);
                        startPolling(currentAVTransport);

                        // Get the next song from your repository/queue
                       /* MediaTrack nextSong = queueManager.getNextTrack();
                        if (nextSong != null) {
                            setNextTrack(nextSong);
                        } */
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        Log.i(TAG, TAG+" - Play command failed: " + defaultMsg);
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                Log.e(TAG, "SetAVTransportURI failed: " + defaultMsg);
                stopPolling(); // Kill the loop here!
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
     * Translates a {@link Track} into a standard-compliant DIDL-Lite XML string.
     * Includes technical flags for bitrate, sample frequency, and bit depth.
     * * @param song The track metadata.
     * @param songUrl The internal streaming URL.
     * @return A well-formed XML string for UPnP renderers.
     */
    private String createDidlLiteMetadata(Track song, String songUrl) {
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
               // "xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\""+ // fail ropieeexl http 500
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
     * pause (or stop) playback on the specified renderer.
     * @param rendererUdn The UDN of the target renderer.
     */
    @Override
    public void playerStop(String rendererUdn) {
        runOnUpnpThread(() -> {
            if (upnpService == null) return;

            Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
            if (device == null) {
                Log.i(TAG, "Renderer not found: " + rendererUdn);
                return;
            }

            Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
            if (avTransportService == null) return;

            if (controlPoint == null) return;

            controlPoint.execute(new Stop(avTransportService) {
                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Stop failed: " + defaultMsg);
                }
            });
        });
    }

    @Override
    public void setNextTrack(Track nextSong) {
        if (controlPoint == null || nextSong == null) return;

        runOnUpnpThread(() -> {
            if(currentAVTransport == null && currentRenderer != null) {
                currentAVTransport = findServiceRecursively(currentRenderer, AV_TRANSPORT_TYPE);
            }
            if (currentAVTransport == null) return;

            String nextUrl = BaseServer.getMusicUrl(nextSong);
            String nextMetadata = createDidlLiteMetadata(nextSong, nextUrl);

            org.jupnp.model.meta.Action action =
                    currentAVTransport.getAction("SetNextAVTransportURI");

            if (action == null) {
                Log.w(TAG, "Gapless not supported: SetNextAVTransportURI missing");
                supportsGapless = false;
                return;
            }

            ActionInvocation invocation = new ActionInvocation(action);
            invocation.setInput("InstanceID", "0");
            invocation.setInput("NextURI", nextUrl);
            invocation.setInput("NextURIMetaData", nextMetadata);

            controlPoint.execute(new org.jupnp.controlpoint.ActionCallback(invocation) {
                @Override
                public void success(ActionInvocation invocation) {
                    Log.i(TAG, "Gapless: Next track queued successfully: " + nextSong.getTitle());
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Gapless: Renderer rejected NextURI (might not support gapless): " + defaultMsg);
                    supportsGapless = false;
                }
            });
        });
    }

    private void playNextManual() {
        runOnUpnpThread(() -> {
            // 1. Find what was just playing
           // MediaTrack currentSong = tagRepos.getCurrentPlaying();
           // if (currentSong == null) return;

            // 2. Get the next one
          //  MediaTrack nextSong = tagRepos.getNextSongInQueue(currentSong);
           // if (nextSong != null) {
             //   Log.i(TAG, "Manual Handover: Pushing next track: " + nextSong.getTitle());
             //   internalPlaySong(currentRendererId, nextSong);
            //}
        });
    }

    private ScheduledFuture<?> pollingTask;
    private AtomicInteger pollGen = new AtomicInteger(0);

    /**
     * Periodically queries the renderer for the current playback position.
     * This is used to sync the UI seek bar when the renderer does not support
     * position events via GENA.
     * * @param avTransport The AVTransport service to poll.
     */
    private void startPolling(Service avTransport) {
        stopPolling();

        if (controlPoint == null || avTransport == null) return;

        // immediately request once (no wait 1s)
        getAvTransportPosition(avTransport);

        int gen = pollGen.incrementAndGet();
        scheduleNextPoll(avTransport, gen);
    }

    private void scheduleNextPoll(Service avTransport, int gen) {
        long delay = getPollingInterval();
        pollingTask = scheduler.schedule(() -> {
            if (gen != pollGen.get()) return; // kill old chain

            getAvTransportPosition(avTransport);
            scheduleNextPoll(avTransport, gen);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void checkHighResSupport(RemoteDevice device, DMRPlayer player) {
        // 1. Find the ConnectionManager service
        Service connectionManager = device.findService(new UDAServiceType("ConnectionManager"));
        if (connectionManager == null) return;

        // 2. Execute 'GetProtocolInfo' to see what the speaker can 'Sink' (receive)
        controlPoint.execute(new org.jupnp.support.connectionmanager.callback.GetProtocolInfo(connectionManager) {
            @Override
            public void received(ActionInvocation invocation,
                                 ProtocolInfos sinkProtocolInfos,
                                 ProtocolInfos sourceProtocolInfos) {

                String allProtocols = sinkProtocolInfos.toString();

                // 3. Search for the "High-Res" signature
                boolean supportsFlac = allProtocols.contains("audio/flac");
                boolean supportsHighBitrate = allProtocols.contains("MAX_BITRATE");

                // Audiophile renderers like WiiM or KEF often list specific sample rates
                boolean supports24Bit = allProtocols.contains("bitsPerSample=24") ||
                        allProtocols.contains("DLNA.ORG_PN=FLAC");

                player.setSupports24Bit(supports24Bit);
                player.setSupportsFlac(supportsFlac);
               // player.setSupportsSeek(su);
               // Log.i(TAG, String.format("Device: %s | FLAC: %b | 24-bit: %b",
                //        device.getDetails().getFriendlyName(), supportsFlac, supports24Bit));

                // You can store this in your PlaybackTarget object for the UI
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                Log.w(TAG, "Could not fetch protocol info: " + defaultMsg);
            }
        });
    }

    private void getAvTransportPosition(Service avTransport) {
        try {
            controlPoint.execute(
                    new GetPositionInfo(avTransport) {
                        @Override
                        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                            Log.d(TAG, "Polling position: failed - " + defaultMsg);
                        }

                        @Override
                        public void received(ActionInvocation invocation, PositionInfo positionInfo) {

                            long position = positionInfo.getTrackElapsedSeconds();
                            if (position == lastPosition) {
                                stagnantCount++;
                            } else {
                                stagnantCount = 0;
                            }

                            lastPosition = position;

                            if (playbackCallback != null) {
                                playbackCallback.onPlaybackStateTimeElapsedSeconds(position);
                            }

                            if (stagnantCount >= 8) {
                                Log.w(TAG, " Playback stuck detected, serverStatus: "+serverStatus.getValue());
                                stopPolling();
                                if( serverStatus.getValue() == ServerStatus.CAST) {
                                    // optional recovery
                                    attemptRecovery();
                                }
                            }
                           // Log.d(TAG, "Polling position: " + position +", stagnantCount: "+stagnantCount +", serverStatus: "+serverStatus.getValue());
                        }
                    }
            );
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private void attemptRecovery() {
        if (currentAVTransport == null) return;

        Log.i(TAG, "Trying recovery: sending Play again");

        controlPoint.execute(new Play(currentAVTransport) {
            @Override
            public void success(ActionInvocation invocation) {
                Log.i(TAG, "Recovery success");
                startPolling(currentAVTransport);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {

            }
        });
    }

    private void stopPolling() {
        if(pollingTask==null) return;

        if (!pollingTask.isCancelled()) {
            pollingTask.cancel(true);
            stagnantCount = -1;
            Log.d(TAG, "Polling task killed");
        }
        pollingTask = null;
    }

    private void schedulePauseTimeout() {
        cancelPauseTimeout(); // Clear any existing timer first

        pauseTimeoutTask = scheduler.schedule(() -> {
            Log.i(TAG, "Pause timeout reached. Killing polling and subscription.");
            stopPolling();
            // Optional: you could also unsubscribe here to be even more aggressive
            // unsubscribeFromRenderer();
        }, PAUSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelPauseTimeout() {
        if (pauseTimeoutTask != null) {
            pauseTimeoutTask.cancel(false);
            pauseTimeoutTask = null;
        }
    }

    /**
     * Creates a subscription to the Renderer's LastChange event variable.
     * Allows the app to receive "Push" updates for TransportState (PLAYING/PAUSED).
     * * @param avTransport The AVTransport service of the remote device.
     */
    private void subscribeToRenderer(Service avTransport) {
        if (controlPoint == null || avTransport == null) return;

        if (subscriptionCallback != null) {
            Log.d(TAG, "Already subscribed, skipping");
            return;
        }

        /*
        controlPoint.execute(new org.jupnp.controlpoint.SubscriptionCallback(avTransport, 600) { // 600s duration
            @Override
            public void established(org.jupnp.model.gena.GENASubscription sub) {
                activeSubscription = sub; // Store the subscription

                startFallbackMonitor(avTransport);
                Log.d(TAG, "Event subscription established");
            }

            @Override
            public void eventReceived(org.jupnp.model.gena.GENASubscription sub) {
                Map<String, StateVariableValue> values = sub.getCurrentValues();
                if (values.containsKey("LastChange")) {
                    String lastChangeXml = values.get("LastChange").toString();
                    // Renderer sends state as XML, we need to parse it
                    parseLastChange(lastChangeXml);
                    lastEventTime = System.currentTimeMillis();
                }
            }

            @Override
            public void ended(org.jupnp.model.gena.GENASubscription sub, org.jupnp.model.gena.CancelReason reason, UpnpResponse response) {
                Log.w(TAG, "Subscription ended: " + (reason != null ? reason : "Normal"));

                stopFallbackMonitor();

                // Immediately fallback
                startPolling(avTransport);
            }

            @Override
            public void failed(org.jupnp.model.gena.GENASubscription sub, UpnpResponse response, Exception e, String msg) {
                Log.e(TAG, "Subscription failed: " + msg);
            }

            @Override
            public void eventsMissed(org.jupnp.model.gena.GENASubscription sub, int numberOfMissedEvents) {
                Log.w(TAG, "Missed " + numberOfMissedEvents + " events");
            }
        }); */

        subscriptionCallback = new org.jupnp.controlpoint.SubscriptionCallback(avTransport, 600) {

            @Override
            protected void failed(GENASubscription subscription, UpnpResponse responseStatus, Exception exception, String defaultMsg) {

            }

            @Override
            public void established(org.jupnp.model.gena.GENASubscription sub) {
                activeSubscription = sub;
                startFallbackMonitor(avTransport);
                Log.d(TAG, "Event subscription established");
            }

            @Override
            public void eventReceived(org.jupnp.model.gena.GENASubscription sub) {
                Map<String, StateVariableValue> values = sub.getCurrentValues();
                if (values.containsKey("LastChange")) {
                    String xml = values.get("LastChange").toString();
                    parseLastChange(xml);
                    lastEventTime = System.currentTimeMillis();
                }
            }

            @Override
            protected void eventsMissed(GENASubscription subscription, int numberOfMissedEvents) {

            }

            @Override
            public void ended(org.jupnp.model.gena.GENASubscription sub,
                              org.jupnp.model.gena.CancelReason reason,
                              UpnpResponse response) {

                Log.w(TAG, "Subscription ended: " + (reason != null ? reason : "Normal"));

                activeSubscription = null;
                subscriptionCallback = null;

                stopFallbackMonitor();
                startPolling(avTransport);
            }
        };

        controlPoint.execute(subscriptionCallback);
    }

    private void unsubscribeIfNeeded() {
        if (subscriptionCallback != null && controlPoint != null) {
            try {
                subscriptionCallback.end();   // ✅ THIS is correct
                Log.d(TAG, "Unsubscribed from renderer");
            } catch (Exception e) {
                Log.w(TAG, "Unsubscribe failed", e);
            }
        }

        subscriptionCallback = null;
        activeSubscription = null;
    }

    private ScheduledFuture<?> fallbackTask;

    private void startFallbackMonitor(Service avTransport) {
        stopFallbackMonitor();

        fallbackTask = scheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            long delta = now - lastEventTime;

            boolean genaAlive = delta < EVENT_TIMEOUT_MS;

            if (!genaAlive && syncMode != SyncMode.POLLING) {
                Log.w(TAG, "⚠️ GENA lost → switch to POLLING");

                syncMode = SyncMode.POLLING;
                startPolling(avTransport);

            } else if (genaAlive && syncMode != SyncMode.GENA) {
                Log.d(TAG, "✅ GENA recovered");
                // Don't stop polling here because GENA typically doesn't send periodic position updates.
                // parseLastChange will optimize polling if it detects position in GENA events.
                syncMode = SyncMode.GENA;
            }

        }, 2, 2, TimeUnit.SECONDS);
    }

    private void stopFallbackMonitor() {
        if (fallbackTask != null) {
            fallbackTask.cancel(true);
            fallbackTask = null;
        }
    }

    private int parseTimeToSeconds(String time) {
        if (time == null || time.isEmpty() || time.equals("NOT_IMPLEMENTED")) return 0;

        String[] parts = time.split(":");
        if (parts.length != 3) return 0;

        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int s = Integer.parseInt(parts[2]);
            return h * 3600 + m * 60 + s;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getPollingInterval() {
        long delta = System.currentTimeMillis() - lastEventTime;

        if (delta < 2000) return 3000;   // good events → slow polling
        if (delta < 5000) return 2000;   // medium
        return 1000; // 500;                      // bad → aggressive
    }

    private void parseLastChange(String xml) {
        try {
                boolean hasPosition = false;
               /* String posStr = extractValue(xml, "RelativeTimePosition");

                if (posStr == null) {
                    posStr = extractValue(xml, "AbsoluteTimePosition");
                } */
            // Use the pre-compiled POS_PATTERN instead of extractValue
            Matcher posMatcher = POS_PATTERN.matcher(xml);
            String posStr = posMatcher.find() ? posMatcher.group(1) : null;

                if (posStr != null) {
                    int currentPositionSec = parseTimeToSeconds(posStr);
                    if(playbackCallback != null) {
                        playbackCallback.onPlaybackStateTimeElapsedSeconds(currentPositionSec);
                    }
                    stopPolling(); // reduce traffic
                    hasPosition = true;
                }

            Matcher m = STATE_PATTERN.matcher(xml);
            String state = m.find() ? m.group(1) : null;
            if ("PLAYING".equalsIgnoreCase(state)) {
                serverStatus.onNext(ServerStatus.CAST);

                // 1. Music is back! Cancel the "kill" timer
                cancelPauseTimeout();

                // 2. Resume tracking position
                if (currentAVTransport != null && pollingTask == null) {
                    startPolling(currentAVTransport);
                }
            }
            else if ("STOPPED".equalsIgnoreCase(state) || "PAUSED".equalsIgnoreCase(state)) {
                //else if (xml.contains("value=\"STOPPED\"") || xml.contains("value=\"PAUSED\"")) {
                stopPolling();
                serverStatus.onNext(ServerStatus.RUNNING);

                // If the speaker stopped, and it can't handle gapless transitions itself,
                // we manually trigger the next song.
                if (!supportsGapless) {
                    playNextManual();
                } else {
                    schedulePauseTimeout();
                }
                //}else if (hasPosition && !xml.contains("value=\"STOPPED\"")) {
            }else if (hasPosition && !"STOPPED".equalsIgnoreCase(state)) {
                serverStatus.onNext(ServerStatus.CAST);
            }

            // Check if the URI changed (Meaning the renderer jumped to the next track)
            if (xml.contains("CurrentTrackMetaData")) {
                // 1. Extract the URI or Title from the XML
               // String newMetadata = extractMetadata(xml);

                // 2. Compare with what the QueueManager thinks is playing
               /* if (!isCurrentTrack(newMetadata)) {
                    Log.i(TAG, "Smart Queue: Renderer transitioned to next track.");
                    queueManager.moveToNext();

                    // 3. Prime the NEW 'next' track
                    MediaTrack nextUp = queueManager.getNextTrack();
                    if (nextUp != null) setNextTrack(nextUp);

                    // 4. Update the UI
                    updateNowPlayingUI(queueManager.getCurrentTrack());
                } */
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse LastChange", e);
        }
    }
}