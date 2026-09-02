package apincer.music.server.jupnp;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import org.jupnp.support.avtransport.callback.GetMediaInfo;
import org.jupnp.support.avtransport.callback.GetPositionInfo;
import org.jupnp.support.avtransport.callback.GetTransportInfo;
import org.jupnp.support.avtransport.callback.Pause;
import org.jupnp.support.avtransport.callback.Play;
import org.jupnp.support.avtransport.callback.Seek;
import org.jupnp.support.avtransport.callback.SetAVTransportURI;
import org.jupnp.support.avtransport.callback.Stop;
import org.jupnp.support.renderingcontrol.callback.SetVolume;
import org.jupnp.support.model.MediaInfo;
import org.jupnp.support.model.PositionInfo;
import org.jupnp.support.model.ProtocolInfos;
import org.jupnp.support.model.TransportInfo;
import org.jupnp.support.model.TransportState;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.DIDLObject;
import apincer.music.core.model.AudioTag;

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
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

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
    private static final UDAServiceType RENDERING_CONTROL_TYPE = new UDAServiceType("RenderingControl");
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

    private final MutableStateFlow<ServerStatus> serverStatus = StateFlowKt.MutableStateFlow(ServerStatus.RUNNING);

    private final Map<String, PlaybackTarget> localTargets = new ConcurrentHashMap<>();
    private final Set<String> subscribedDevices = new HashSet<>();

    private org.jupnp.model.meta.RemoteDevice currentRenderer;
    private String currentRendererId;
    private org.jupnp.model.gena.GENASubscription activeSubscription;
    private org.jupnp.controlpoint.SubscriptionCallback subscriptionCallback;
    private boolean supportsGapless = false; // Default to false (safe-by-default for generic DLNA), opt-in only for verified gapless streamers
    private volatile boolean isUserInitiatedStop = false;
    private volatile Track preloadedNextTrack;
    private volatile String preloadedNextUrl;
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
    private int consecutivePollFailures = 0;

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

    // Network — WiFi / Ethernet client
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Network — Hotspot / AP mode
    private BroadcastReceiver hotspotReceiver;
    private volatile boolean hotspotAvailable = false;

    // Locks
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;

    private volatile String lastBoundIp = null;

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
     *
     * <p>The {@link ConnectivityManager.NetworkCallback} is registered here (not inside the
     * UPnP thread) so that it survives stop/start cycles and can trigger auto-restart when
     * WiFi is restored after a loss.
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

        // Register both network watchers BEFORE starting the UPnP thread so they survive
        // the stop() → IDLE transition and can fire evaluateNetworkState() on recovery.
        startNetworkMonitoring();
        startHotspotMonitoring();

        runOnUpnpThread(() -> {
            try {
                acquireLocks();
                initUpnp();

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

        RegistryListener listener = new SimpleRegistryListener(this::notifyRenderersChanged);
        upnpService.getRegistry().addListener(listener);

        mediaServerDevice = MediaServerDeviceFactory.create(context, tagRepos);
        upnpService.getRegistry().addDevice(mediaServerDevice);

        lastBoundIp = apincer.music.core.utils.NetworkUtils.getIpAddress();
        Log.i(TAG, "UPnP initialized and bound to IP: " + lastBoundIp);

        sendAlive();
        triggerDiscovery();
    }

    // =========================================================
    // STOP
    // =========================================================

    /**
     * Gracefully shuts down the UPnP stack, releases all system locks, and notifies
     * the network of the device's departure (SSDP Bye-Bye).
     *
     * <p>The {@link ConnectivityManager.NetworkCallback} is intentionally <b>not</b> unregistered
     * here. It must remain active so that {@link #evaluateNetworkState()} can detect when WiFi
     * is restored and automatically restart the server. The callback is only torn down in
     * {@link #release()}.
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
                // ✅ Do NOT call stopNetworkMonitoring() here — the callback must stay
                //    alive across stop/start cycles to enable WiFi-loss auto-recovery.

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

    /**
     * Seamlessly restarts the UPnP stack and Web Server when the network IP or interface changes.
     */
    public void restart() {
        synchronized (stateLock) {
            if (state == State.STARTING || state == State.STOPPING) {
                Log.d(TAG, "Restart ignored/deferred, state=" + state);
                return;
            }
            state = State.STARTING;
        }

        runOnUpnpThread(() -> {
            Log.i(TAG, "Restarting UPnP Server stack for updated network interface...");
            try {
                stopPeriodicDiscovery();
                if (upnpService != null) {
                    try { sendByebye(); } catch (Exception ignored) {}
                    try { upnpService.shutdown(); } catch (Exception ignored) {}
                    upnpService = null;
                }
                currentRenderer = null;
                currentRendererId = null;
                currentAVTransport = null;
                releaseLocks();

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}

                acquireLocks();
                initUpnp();
                startPeriodicDiscovery();

                synchronized (stateLock) {
                    state = State.RUNNING;
                }
                Log.i(TAG, "UPnP restart completed successfully on IP: " + lastBoundIp);
            } catch (Exception e) {
                Log.e(TAG, "UPnP restart failed", e);
                synchronized (stateLock) {
                    state = State.IDLE;
                }
            }
        });
    }

    public StateFlow<ServerStatus> getStatus() {
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

    private volatile java.util.function.Consumer<List<PlaybackTarget>> onRenderersChangedListener;
    private ScheduledFuture<?> notifyTask;

    @Override
    public void setOnRenderersChangedListener(java.util.function.Consumer<List<PlaybackTarget>> listener) {
        this.onRenderersChangedListener = listener;
    }

    private void notifyRenderersChanged() {
        if (onRenderersChangedListener == null) return;
        synchronized (this) {
            if (notifyTask != null && !notifyTask.isDone()) {
                notifyTask.cancel(false);
            }
            notifyTask = scheduler.schedule(() -> {
                try {
                    List<PlaybackTarget> targets = getPlaybackTargets();
                    if (onRenderersChangedListener != null) {
                        onRenderersChangedListener.accept(targets);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to notify renderers changed", e);
                }
            }, 300, TimeUnit.MILLISECONDS);
        }
    }

    private void triggerMultiSearch(int mxSeconds) {
        if (controlPoint == null) return;
        try {
            // 1. Broad query for all UPnP root devices and services
            controlPoint.search(new org.jupnp.model.message.header.STAllHeader(), mxSeconds);
            // 2. Targeted query for MediaRenderer devices (WiiM, KEF, Eversolo, Sony, Pioneer, etc.)
            controlPoint.search(new org.jupnp.model.message.header.UDADeviceTypeHeader(new org.jupnp.model.types.UDADeviceType("MediaRenderer")), mxSeconds);
            // 3. Targeted query for AVTransport services
            controlPoint.search(new org.jupnp.model.message.header.UDAServiceTypeHeader(new org.jupnp.model.types.UDAServiceType("AVTransport")), mxSeconds);
        } catch (Exception e) {
            Log.w(TAG, "Multi-target discovery search failed", e);
        }
    }

    private void triggerDiscovery() {
        runOnUpnpThread(() -> {
            lastDiscoveryTime = System.currentTimeMillis();
            triggerMultiSearch(3);
        });
    }

    @Override
    public void refreshDiscovery() {
        Log.d(TAG, "Manual refresh discovery triggered with multi-target SSDP query");
        runOnUpnpThread(() -> {
            lastDiscoveryTime = System.currentTimeMillis();
            // Pulse 1: Immediate query with MX=4
            triggerMultiSearch(4);
            // Pulse 2: Second query at 1.5s to compensate for packet drops on noisy Wi-Fi networks
            scheduler.schedule(() -> runOnUpnpThread(() -> triggerMultiSearch(3)), 1500, TimeUnit.MILLISECONDS);
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

        // Include TRANSPORT_ETHERNET so the server also responds to wired network changes.
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available: " + network);
                currentNetwork = network;
                wifiAvailable = true;

                // 1-second delay avoids racing DHCP/IP assignment on reconnect.
                scheduler.schedule(() -> evaluateNetworkState(), 1, TimeUnit.SECONDS);
            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
                Log.d(TAG, "Network link properties changed: " + network);
                scheduler.schedule(() -> evaluateNetworkState(), 1, TimeUnit.SECONDS);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "Network lost: " + network);

                if (!network.equals(currentNetwork)) return;

                wifiAvailable = false;
                currentNetwork = null;
                lastBoundIp = null;

                evaluateNetworkState();
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
        Log.d(TAG, "Network monitoring started");
    }

    private void evaluateNetworkState() {
        synchronized (stateLock) {
            boolean networkUp = wifiAvailable || hotspotAvailable || apincer.music.core.utils.NetworkUtils.isServerNetworkAvailable(context);
            String currentIp = apincer.music.core.utils.NetworkUtils.getIpAddress();
            Log.d(TAG, "Evaluate → state=" + state + ", wifi=" + wifiAvailable + ", hotspot=" + hotspotAvailable
                    + ", networkUp=" + networkUp + ", lastBoundIp=" + lastBoundIp + ", currentIp=" + currentIp);

            if (networkUp && currentIp != null && !currentIp.isEmpty() && !"127.0.0.1".equals(currentIp)) {
                if (state == State.IDLE) {
                    Log.d(TAG, "Network OK (IP " + currentIp + ") → starting UPnP");
                    start();
                } else if (state == State.RUNNING) {
                    if (lastBoundIp == null || !lastBoundIp.equals(currentIp)) {
                        Log.i(TAG, "Network IP changed (" + lastBoundIp + " -> " + currentIp + ") while running → auto-restarting UPnP");
                        restart();
                    } else {
                        // Same IP, ensure locks and SSDP discovery are active
                        acquireLocks();
                        refreshDiscovery();
                    }
                }
            } else {
                if (state == State.RUNNING) {
                    Log.d(TAG, "Network lost or invalid IP → stopping UPnP");
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

    private void startHotspotMonitoring() {
        if (hotspotReceiver != null) return; // already registered

        hotspotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                // isHotspotActive() does the real detection by scanning network interfaces.
                boolean nowActive = apincer.music.core.utils.NetworkUtils.isHotspotActive(context);
                if (nowActive != hotspotAvailable) {
                    hotspotAvailable = nowActive;
                    Log.d(TAG, "Hotspot " + (nowActive ? "started" : "stopped"));
                    if (nowActive) {
                        // 2-second delay so the AP interface is fully up before UPnP binds.
                        scheduler.schedule(() -> evaluateNetworkState(), 2, TimeUnit.SECONDS);
                    } else {
                        evaluateNetworkState();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        context.registerReceiver(hotspotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Hotspot monitoring started");

        // Evaluate immediately in case hotspot was already on when we registered.
        hotspotAvailable = apincer.music.core.utils.NetworkUtils.isHotspotActive(context);
        if (hotspotAvailable) {
            Log.d(TAG, "Hotspot already active at startup");
        }
    }

    private void stopHotspotMonitoring() {
        if (hotspotReceiver != null) {
            try {
                context.unregisterReceiver(hotspotReceiver);
            } catch (Exception ignored) {}
            hotspotReceiver = null;
            hotspotAvailable = false;
        }
    }

    // =========================================================
    // LOCKS
    // =========================================================

    private void acquireLocks() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (pm != null) {
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MM:Wake");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(4 * 60 * 60 * 1000L);
            }
        }

        if (wm != null) {
            if (wifiLock == null) {
                // Use WIFI_MODE_FULL_HIGH_PERF instead of WIFI_MODE_FULL_LOW_LATENCY
                // LOW_LATENCY keeps the radio at maximum power continuously
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MM:Wifi");
                wifiLock.setReferenceCounted(false);
            }
            if (!wifiLock.isHeld()) {
                wifiLock.acquire();
            }

            if (multicastLock == null) {
                multicastLock = wm.createMulticastLock("MM:Multicast");
                multicastLock.setReferenceCounted(false);
            }
            if (!multicastLock.isHeld()) {
                multicastLock.acquire();
            }
        }
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}

        wakeLock = null;
        wifiLock = null;
        multicastLock = null;
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void release() {
        // Unregister all network callbacks only on full hub teardown.
        stopNetworkMonitoring();
        stopHotspotMonitoring();
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
        playerActivateWithHandoff(rendererId, callback, null, 0);
    }

    @Override
    public void playerActivateWithHandoff(String rendererId, PlaybackCallback callback, Track handoffTrack, long initialPositionMs) {
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
                    queryCurrentPlaybackInfoWithHandoff(currentAVTransport, handoffTrack, initialPositionMs);
                }
            }
            Log.d(TAG, "Activated & Subscribed: " + rendererId);
        });
    }

    private void queryCurrentPlaybackInfo(Service avTransport) {
        queryCurrentPlaybackInfoWithHandoff(avTransport, null, 0);
    }

    private void queryCurrentPlaybackInfoWithHandoff(Service avTransport, Track handoffTrack, long initialPositionMs) {
        if (controlPoint == null || avTransport == null) return;

        // 1. Query Transport State (PLAYING, PAUSED, STOPPED)
        controlPoint.execute(new GetTransportInfo(avTransport) {
            @Override
            public void received(ActionInvocation invocation, TransportInfo transportInfo) {
                if (transportInfo == null) return;
                TransportState state = transportInfo.getCurrentTransportState();
                if (state == TransportState.PLAYING) {
                    // Renderer is ALREADY playing: adopt live session without resetting track
                    serverStatus.setValue(ServerStatus.CAST);
                    startPolling(avTransport);
                    queryMediaInfoInternal(avTransport);
                } else if (state == TransportState.PAUSED_PLAYBACK) {
                    serverStatus.setValue(ServerStatus.RUNNING);
                    queryMediaInfoInternal(avTransport);
                } else {
                    // Renderer is idle/stopped: perform seamless handoff if user was playing on previous player
                    if (handoffTrack != null && currentRendererId != null) {
                        Log.i(TAG, "Handoff: Starting active track " + handoffTrack.getTitle() + " at " + initialPositionMs + "ms on idle renderer");
                        internalPlaySong(currentRendererId, handoffTrack, initialPositionMs);
                        if (playbackCallback != null) {
                            playbackCallback.onMediaTrackChanged(handoffTrack);
                        }
                    }
                }
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                Log.d(TAG, "GetTransportInfo query: " + defaultMsg);
                if (handoffTrack != null && currentRendererId != null) {
                    internalPlaySong(currentRendererId, handoffTrack, initialPositionMs);
                }
            }
        });

        // Query initial position
        getAvTransportPosition(avTransport);
    }

    private void queryMediaInfoInternal(Service avTransport) {
        if (controlPoint == null || avTransport == null) return;

        // Query Media Info (Current Track metadata & URI)
        controlPoint.execute(new GetMediaInfo(avTransport) {
            @Override
            public void received(ActionInvocation invocation, MediaInfo mediaInfo) {
                if (mediaInfo == null) return;

                String currentUri = mediaInfo.getCurrentURI();
                String metadataXml = mediaInfo.getCurrentURIMetaData();

                Track song = resolveTrackFromMediaInfo(currentUri, metadataXml);
                if (song != null && playbackCallback != null) {
                    playbackCallback.onMediaTrackChanged(song);
                }
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                Log.d(TAG, "GetMediaInfo query: " + defaultMsg);
            }
        });
    }

    private Track resolveTrackFromMediaInfo(String currentUri, String metadataXml) {
        if (currentUri != null && currentUri.contains("/music/")) {
            try {
                // e.g. http://192.168.1.10:8080/music/12345/file.flac
                String afterMusic = currentUri.substring(currentUri.indexOf("/music/") + 7);
                int slashIdx = afterMusic.indexOf('/');
                String idStr = (slashIdx > 0) ? afterMusic.substring(0, slashIdx) : afterMusic;
                long trackId = Long.parseLong(idStr);
                Track tag = tagRepos.findById(trackId);
                if (tag != null) return tag;
            } catch (Exception ignore) {
            }
        }

        if (metadataXml != null && !metadataXml.isEmpty()) {
            try {
                DIDLParser parser = new DIDLParser();
                DIDLContent didl = parser.parse(metadataXml);
                if (didl != null && !didl.getItems().isEmpty()) {
                    Item songItem = didl.getItems().get(0);
                    String title = songItem.getTitle();
                    String artist = "";
                    String album = "";

                    if (songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class) != null) {
                        artist = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class).getName();
                    }
                    if (songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class) != null) {
                        album = songItem.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class);
                    }

                    if (!StringUtils.isEmpty(title)) {
                        Track match = tagRepos.findMusic(title, artist, album);
                        if (match != null) return match;

                        AudioTag adHoc = new AudioTag();
                        adHoc.setTitle(title);
                        adHoc.setArtist(artist);
                        adHoc.setAlbum(album);
                        return adHoc;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Error parsing DIDL metadata on player activate: " + e.getMessage());
            }
        }
        return null;
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
            java.util.Collection<RemoteDevice> devices = upnpService.getRegistry().getRemoteDevices();
            if (devices != null) {
                for (RemoteDevice device : devices) {
                    findRenderersRecursively(device, result);
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    private void findRenderersRecursively(RemoteDevice device, List<RemoteDevice> result) {
        if (device == null) return;
        
        boolean isRenderer = (device.getType() != null && "MediaRenderer".equalsIgnoreCase(device.getType().getType()))
                || findServiceRecursively(device, AV_TRANSPORT_TYPE) != null;
        
        if (isRenderer && isDeviceValidAndReachable(device)) {
            // Deduplicate by UDN so identical root/embedded device instances are not listed twice
            String udn = device.getIdentity() != null && device.getIdentity().getUdn() != null
                    ? device.getIdentity().getUdn().getIdentifierString() : null;
            boolean alreadyPresent = false;
            if (udn != null) {
                for (RemoteDevice existing : result) {
                    if (existing.getIdentity() != null && existing.getIdentity().getUdn() != null
                            && udn.equalsIgnoreCase(existing.getIdentity().getUdn().getIdentifierString())) {
                        alreadyPresent = true;
                        break;
                    }
                }
            }
            if (!alreadyPresent) {
                result.add(device);
            }
        }
        
        if (device.hasEmbeddedDevices()) {
            for (RemoteDevice embedded : device.getEmbeddedDevices()) {
                findRenderersRecursively(embedded, result);
            }
        }
    }

    private boolean isDeviceValidAndReachable(RemoteDevice device) {
        if (device == null || device.getIdentity() == null || device.getIdentity().getDescriptorURL() == null) {
            return false;
        }
        String host = device.getIdentity().getDescriptorURL().getHost();
        if (host == null || host.isEmpty() || "127.0.0.1".equals(host) || "0.0.0.0".equals(host)) {
            return false;
        }
        // Subnet sanity check: If we have lastBoundIp (e.g. 192.168.1.5), verify host starts with same /24 or /16
        if (lastBoundIp != null && !lastBoundIp.isEmpty()) {
            String mySubnet24 = getSubnet24(lastBoundIp);
            String devSubnet24 = getSubnet24(host);
            if (!mySubnet24.isEmpty() && !devSubnet24.isEmpty() && !mySubnet24.equals(devSubnet24)) {
                // If subnets don't match on class C /24, check if /16 matches (e.g. 172.16.x or 10.x mesh networks)
                if (!getSubnet16(lastBoundIp).equals(getSubnet16(host))) {
                    Log.d(TAG, "Filtering out stale device on disparate subnet: " + host + " (current IP: " + lastBoundIp + ")");
                    return false;
                }
            }
        }
        return true;
    }

    private String getSubnet24(String ip) {
        if (ip == null) return "";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : "";
    }

    private String getSubnet16(String ip) {
        if (ip == null) return "";
        int firstDot = ip.indexOf('.');
        if (firstDot > 0) {
            int secondDot = ip.indexOf('.', firstDot + 1);
            if (secondDot > 0) {
                return ip.substring(0, secondDot);
            }
        }
        return "";
    }

    /**
     * Commands a remote renderer to play a specific track.
     * Performs a multi-step sequence: Resolves Renderer -> Sets Transport URI -> Sends Play Command.
     * @param song The {@link Track} containing metadata and stream information.
     */
    @Override
    public void playerPlaySong(Track song) {
        playerPlaySong(song, 0);
    }

    public void playerPlaySong(Track song, long initialPositionMs) {
        runOnUpnpThread(() -> {
            if(currentRendererId != null) {
                internalPlaySong(currentRendererId, song, initialPositionMs);
            }
        });
    }

    /**
     * Commands a remote renderer to play a specific track.
     * Performs a multi-step sequence: Resolves Renderer -> Sets Transport URI -> Sends Play Command.
     * @param udn The Unique Device Name of the target renderer.
     * @param song The {@link Track} containing metadata and stream information.
     */
    @Override
    public void playerPlaySong(String udn, Track song) {
        playerPlaySong(udn, song, 0);
    }

    @Override
    public void playerPlaySong(String udn, Track song, long initialPositionMs) {
        runOnUpnpThread(() -> {
            internalPlaySong(udn, song, initialPositionMs);
        });
    }

    private void internalPlaySong(String udn, Track song) {
        internalPlaySong(udn, song, 0);
    }

    private void internalPlaySong(String udn, Track song, long initialPositionMs) {
        isUserInitiatedStop = false;
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
        String songUrl = BaseServer.getMusicUrl(song);

        // Create a simple metadata string for the renderer (optional but recommended)
        String metadata = createDidlLiteMetadata(song, songUrl);

        // Step 3: Stop current playback and allow 100ms DAC buffer flush before starting next track
        ControlPoint controlPoint = upnpService.getControlPoint();
        controlPoint.execute(new Stop(currentAVTransport) {
            @Override
            public void success(ActionInvocation invocation) {
                // 100ms buffer flush grace period to prevent pop/click audio artifacts on external DACs
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.schedule(() -> executeSetUriAndPlay(controlPoint, currentAVTransport, songUrl, metadata, initialPositionMs), 100, TimeUnit.MILLISECONDS);
                } else {
                    executeSetUriAndPlay(controlPoint, currentAVTransport, songUrl, metadata, initialPositionMs);
                }
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.schedule(() -> executeSetUriAndPlay(controlPoint, currentAVTransport, songUrl, metadata, initialPositionMs), 100, TimeUnit.MILLISECONDS);
                } else {
                    executeSetUriAndPlay(controlPoint, currentAVTransport, songUrl, metadata, initialPositionMs);
                }
            }
        });
    }

    private void executeSetUriAndPlay(ControlPoint controlPoint, Service avTransport, String songUrl, String metadata, long initialPositionMs) {
        controlPoint.execute(new SetAVTransportURI(avTransport, songUrl, metadata) {
            @Override
            public void success(ActionInvocation invocation) {
                controlPoint.execute(new Play(avTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        serverStatus.setValue(ServerStatus.CAST);
                        startPolling(avTransport);

                        // Seamless position handoff: seek to initial position
                        if (initialPositionMs > 1000) {
                            String seekTarget = formatSeekTime(initialPositionMs);
                            if (scheduler != null && !scheduler.isShutdown()) {
                                scheduler.schedule(() -> {
                                    controlPoint.execute(new Seek(avTransport, seekTarget) {
                                        @Override
                                        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                                            Log.w(TAG, "Handoff seek failed: " + defaultMsg);
                                        }
                                    });
                                }, 300, TimeUnit.MILLISECONDS);
                            }
                        }
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
                stopPolling();
            }
        });
    }

    /**
     * Finds a service recursively within a device and its embedded devices.
     */
    private Service findServiceRecursively(Device device, UDAServiceType serviceType) {
        if (device == null || serviceType == null) return null;

        // Find service ignoring version
        if (device.getServices() != null) {
            for (Service s : device.getServices()) {
                if (s.getServiceType() != null && 
                    serviceType.getType().equalsIgnoreCase(s.getServiceType().getType())) {
                    return s;
                }
            }
        }

        if (device.hasEmbeddedDevices()) {
            for (Device embeddedDevice : device.getEmbeddedDevices()) {
                Service service = findServiceRecursively(embeddedDevice, serviceType);
                if (service != null) {
                    return service;
                }
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
        isUserInitiatedStop = true;
        runOnUpnpThread(() -> {
            if (upnpService == null) return;

            RemoteDevice device = resolveRenderer(rendererUdn);
            if (device == null) {
                Device d = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
                if (d instanceof RemoteDevice) device = (RemoteDevice) d;
            }
            if (device == null) {
                Log.i(TAG, "Renderer not found: " + rendererUdn);
                return;
            }

            Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
            if (avTransportService == null || controlPoint == null) return;

            controlPoint.execute(new Stop(avTransportService) {
                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Stop failed: " + defaultMsg);
                }
            });
        });
    }

    @Override
    public void playerPause(String rendererUdn) {
        isUserInitiatedStop = true;
        runOnUpnpThread(() -> {
            if (upnpService == null) return;

            RemoteDevice device = resolveRenderer(rendererUdn);
            if (device == null) {
                Device d = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
                if (d instanceof RemoteDevice) device = (RemoteDevice) d;
            }
            if (device == null) return;

            Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
            if (avTransportService == null || controlPoint == null) return;

            controlPoint.execute(new Pause(avTransportService) {
                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Pause failed: " + defaultMsg);
                }
            });
        });
    }

    private String formatSeekTime(long durationInMillis) {
        long totalSeconds = Math.max(0, durationInMillis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void playerSeek(String rendererUdn, long positionMs) {
        runOnUpnpThread(() -> {
            if (upnpService == null) return;

            RemoteDevice device = resolveRenderer(rendererUdn);
            if (device == null) {
                Device d = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
                if (d instanceof RemoteDevice) device = (RemoteDevice) d;
            }
            if (device == null) {
                Log.w(TAG, "playerSeek: Renderer not found for UDN: " + rendererUdn);
                return;
            }

            Service avTransportService = findServiceRecursively(device, AV_TRANSPORT_TYPE);
            if (avTransportService == null || controlPoint == null) return;

            String seekTarget = formatSeekTime(positionMs);
            Log.d(TAG, "playerSeek: Seeking to " + seekTarget + " (" + positionMs + "ms)");
            controlPoint.execute(new Seek(avTransportService, seekTarget) {
                @Override
                public void success(ActionInvocation invocation) {
                    Log.i(TAG, "Seek succeeded to " + seekTarget);
                    // Force an immediate position query to update UI seekbar
                    getAvTransportPosition(avTransportService);
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Seek failed to " + seekTarget + ": " + defaultMsg);
                }
            });
        });
    }

    @Override
    public void playerSetVolume(String rendererUdn, int volume) {
        runOnUpnpThread(() -> {
            if (upnpService == null) return;

            Device device = upnpService.getRegistry().getDevice(new UDN(rendererUdn), false);
            if (device == null) return;

            Service renderingControlService = findServiceRecursively(device, RENDERING_CONTROL_TYPE);
            if (renderingControlService == null || controlPoint == null) return;

            controlPoint.execute(new SetVolume(renderingControlService, Math.max(0, Math.min(100, volume))) {
                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "SetVolume failed: " + defaultMsg);
                }
            });
        });
    }

    @Override
    public void setNextTrack(Track nextSong) {
        if (controlPoint == null || nextSong == null) return;
        // SAFE-BY-DEFAULT: Only dispatch SetNextAVTransportURI for verified gapless streamers (WiiM, Eversolo, Linn, Auralic).
        // Generic DLNA renderers and DAPs use discrete handover with host RAM pre-caching.
        if (!isCurrentRendererVerifiedGapless()) {
            Log.d(TAG, "setNextTrack: Renderer not in verified gapless allowlist; skipping SetNextAVTransportURI.");
            return;
        }

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
                    preloadedNextTrack = nextSong;
                    preloadedNextUrl = nextUrl;
                    supportsGapless = true;
                    Log.i(TAG, "Gapless: Next track queued successfully: " + nextSong.getTitle());
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    Log.w(TAG, "Gapless: Renderer rejected NextURI (might not support gapless): " + defaultMsg);
                    supportsGapless = false;
                    preloadedNextTrack = null;
                    preloadedNextUrl = null;
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

        lastPosition = -1;
        stagnantCount = 0;
        consecutivePollFailures = 0;

        int gen = pollGen.incrementAndGet();
        scheduleNextPoll(avTransport, gen, getPollingInterval());
    }

    private void scheduleNextPoll(Service avTransport, int gen, long delay) {
        if (gen != pollGen.get() || avTransport == null || controlPoint == null) return;
        if (serverStatus.getValue() != ServerStatus.CAST) {
            stopPolling();
            return;
        }

        pollingTask = scheduler.schedule(() -> {
            if (gen != pollGen.get()) return; // kill old chain
            if (serverStatus.getValue() != ServerStatus.CAST) {
                stopPolling();
                return;
            }

            getAvTransportPosition(avTransport, gen);
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
        getAvTransportPosition(avTransport, -1);
    }

    private void getAvTransportPosition(Service avTransport, int gen) {
        if (avTransport == null || controlPoint == null) return;
        if (gen != -1 && gen != pollGen.get()) return;

        try {
            controlPoint.execute(
                    new GetPositionInfo(avTransport) {
                        @Override
                        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                            if (gen == -1) {
                                Log.d(TAG, "Initial position query failed: " + defaultMsg);
                                return;
                            }
                            if (gen != pollGen.get()) return;

                            consecutivePollFailures++;
                            if (consecutivePollFailures <= 3) {
                                if (consecutivePollFailures == 1) {
                                    Log.d(TAG, "Polling position: failed (" + consecutivePollFailures + "/3) - " + defaultMsg);
                                }
                                // Backoff and retry with 2.5s delay
                                scheduleNextPoll(avTransport, gen, 2500);
                            } else {
                                Log.i(TAG, "Renderer left playback state or stopped responding (" + defaultMsg + "). Halting polling loop.");
                                stopPolling();
                                if (serverStatus.getValue() == ServerStatus.CAST) {
                                    serverStatus.setValue(ServerStatus.RUNNING);
                                }
                            }
                        }

                        @Override
                        public void received(ActionInvocation invocation, PositionInfo positionInfo) {
                            if (gen != -1 && gen != pollGen.get()) return;

                            if (gen != -1) {
                                consecutivePollFailures = 0; // reset on success
                            }

                            long position = positionInfo.getTrackElapsedSeconds();
                            long duration = positionInfo.getTrackDurationSeconds();
                            if (position > 0 && position == lastPosition) {
                                stagnantCount++;
                            } else {
                                stagnantCount = 0;
                            }

                            lastPosition = position;

                            if (playbackCallback != null && position >= 0) {
                                playbackCallback.onPlaybackStateTimeElapsedSeconds(position);
                            }

                            // Gapless transition check
                            String currentURI = positionInfo.getTrackURI();
                            if (preloadedNextTrack != null && currentURI != null &&
                                    (currentURI.equals(preloadedNextUrl) || currentURI.contains("/music/" + preloadedNextTrack.getId() + "/"))) {
                                Track nextTrack = preloadedNextTrack;
                                preloadedNextTrack = null;
                                preloadedNextUrl = null;
                                Log.i(TAG, "Gapless: Renderer seamlessly transitioned to track → " + nextTrack.getTitle());
                                if (playbackCallback != null) {
                                    playbackCallback.onMediaTrackChanged(nextTrack);
                                }
                            } else if (duration > 0 && position >= duration && position > 5) {
                                Log.i(TAG, "Polling: Track duration complete (" + position + "s / " + duration + "s)");
                                if (!isUserInitiatedStop && playbackCallback != null) {
                                    playbackCallback.onPlaybackCompleted();
                                }
                            }

                            // Only trigger recovery if position was established (> 5s) and has been stuck for 15+ consecutive polls
                            if (position > 5 && stagnantCount >= 15) {
                                Log.w(TAG, " Playback stuck detected, serverStatus: "+serverStatus.getValue());
                                stopPolling();
                                if( serverStatus.getValue() == ServerStatus.CAST) {
                                    // optional recovery
                                    attemptRecovery();
                                }
                                return;
                            }

                            // Cleanly schedule next poll after receiving response if active recurring poll
                            if (gen != -1) {
                                scheduleNextPoll(avTransport, gen, getPollingInterval());
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.w(TAG, "getAvTransportPosition failed", e);
            if (gen != -1) {
                consecutivePollFailures++;
                if (consecutivePollFailures <= 3) {
                    scheduleNextPoll(avTransport, gen, 2500);
                } else {
                    stopPolling();
                }
            }
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
        stagnantCount = 0;
        consecutivePollFailures = 0;
        pollGen.incrementAndGet(); // Invalidate any pending poll tasks
        if (pollingTask != null) {
            if (!pollingTask.isCancelled()) {
                pollingTask.cancel(true);
                Log.d(TAG, "Polling task killed");
            }
            pollingTask = null;
        }
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
                Log.e(TAG, "Event subscription failed: " + defaultMsg, exception);
                activeSubscription = null;
                subscriptionCallback = null;
                startPolling(avTransport);
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
        if (xml == null || xml.isEmpty()) return;
        try {
            boolean hasPosition = false;

            // Extract position using pre-compiled POS_PATTERN
            Matcher posMatcher = POS_PATTERN.matcher(xml);
            String posStr = posMatcher.find() ? posMatcher.group(1) : null;

            if (posStr != null) {
                int currentPositionSec = parseTimeToSeconds(posStr);
                if (playbackCallback != null && currentPositionSec >= 0) {
                    playbackCallback.onPlaybackStateTimeElapsedSeconds(currentPositionSec);
                }
                hasPosition = true;
            }

            Matcher m = STATE_PATTERN.matcher(xml);
            String state = m.find() ? m.group(1) : null;
            if ("PLAYING".equalsIgnoreCase(state)) {
                serverStatus.setValue(ServerStatus.CAST);

                // Cancel the pause/kill timeout
                cancelPauseTimeout();

                // Resume tracking position
                if (currentAVTransport != null && pollingTask == null) {
                    startPolling(currentAVTransport);
                }
            } else if ("STOPPED".equalsIgnoreCase(state)) {
                stopPolling();
                serverStatus.setValue(ServerStatus.RUNNING);

                if (!isUserInitiatedStop && playbackCallback != null) {
                    Log.i(TAG, "DLNA renderer stopped naturally at track end → triggering onPlaybackCompleted()");
                    playbackCallback.onPlaybackCompleted();
                } else {
                    schedulePauseTimeout();
                }
            } else if ("PAUSED".equalsIgnoreCase(state) || "PAUSED_PLAYBACK".equalsIgnoreCase(state)) {
                stopPolling();
                serverStatus.setValue(ServerStatus.RUNNING);
                schedulePauseTimeout();
            } else if (hasPosition && !"STOPPED".equalsIgnoreCase(state)) {
                serverStatus.setValue(ServerStatus.CAST);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse LastChange", e);
        }
    }

    @Override
    public boolean isCurrentRendererHiBy() {
        if (currentRenderer != null && currentRenderer.getDetails() != null) {
            String name = currentRenderer.getDetails().getFriendlyName();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.contains("hiby") || lower.contains("r3") || lower.startsWith("r3")) {
                    return true;
                }
            }
            if (currentRenderer.getDetails().getManufacturerDetails() != null) {
                String mfg = currentRenderer.getDetails().getManufacturerDetails().getManufacturer();
                if (mfg != null && mfg.toLowerCase().contains("hiby")) {
                    return true;
                }
            }
            if (currentRenderer.getDetails().getModelDetails() != null) {
                String model = currentRenderer.getDetails().getModelDetails().getModelName();
                if (model != null && (model.toLowerCase().contains("hiby") || model.toLowerCase().contains("r3"))) {
                    return true;
                }
            }
        }
        if (currentRendererId != null) {
            String lower = currentRendererId.toLowerCase();
            if (lower.contains("hiby") || lower.contains("r3")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCurrentRendererVerifiedGapless() {
        if (currentRenderer != null && currentRenderer.getDetails() != null) {
            String name = currentRenderer.getDetails().getFriendlyName();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.contains("wiim") || lower.contains("linkplay") || lower.contains("eversolo") || lower.contains("zidoo") || lower.contains("linn") || lower.contains("auralic") || lower.contains("audiopro")) {
                    return true;
                }
            }
            if (currentRenderer.getDetails().getManufacturerDetails() != null) {
                String mfg = currentRenderer.getDetails().getManufacturerDetails().getManufacturer();
                if (mfg != null) {
                    String lower = mfg.toLowerCase();
                    if (lower.contains("linkplay") || lower.contains("eversolo") || lower.contains("zidoo") || lower.contains("linn") || lower.contains("auralic")) {
                        return true;
                    }
                }
            }
        }
        if (currentRendererId != null) {
            String lower = currentRendererId.toLowerCase();
            if (lower.contains("wiim") || lower.contains("linkplay") || lower.contains("eversolo") || lower.contains("zidoo") || lower.contains("linn") || lower.contains("auralic")) {
                return true;
            }
        }
        return false;
    }
}