package apincer.android.mmate.dlna;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class DLNAServerManager {
    private static final String TAG = "DLNAServerManager";
    private static DLNAServerManager instance;

    // Use ServerStatus from MediaServerService
    private MutableLiveData<MediaServerService.ServerStatus> serverStatusLiveData = new MutableLiveData<>();
    private MutableLiveData<String> serverAddressLiveData = new MutableLiveData<>();
    private Context context;

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MediaServerService.ACTION_STATUS_CHANGED.equals(intent.getAction())) {
                String statusStr = intent.getStringExtra(MediaServerService.EXTRA_STATUS);
                if (statusStr != null) {
                    try {
                        MediaServerService.ServerStatus status = MediaServerService.ServerStatus.valueOf(statusStr);
                        serverStatusLiveData.setValue(status);
                        Log.d(TAG, "Received status update: " + status);

                        if (status == MediaServerService.ServerStatus.RUNNING) {
                            String address = intent.getStringExtra(MediaServerService.EXTRA_ADDRESS);
                            serverAddressLiveData.setValue(address);
                        } else if (status == MediaServerService.ServerStatus.STOPPED || status == MediaServerService.ServerStatus.ERROR) {
                            serverAddressLiveData.setValue(null);
                        }
                        // For STARTING/STOPPING, address might not change immediately or might be null
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Invalid status received: " + statusStr, e);
                    }
                }
            }
        }
    };

    private DLNAServerManager(Context context) {
        this.context = context.getApplicationContext();
        IntentFilter filter = new IntentFilter(MediaServerService.ACTION_STATUS_CHANGED);
        LocalBroadcastManager.getInstance(this.context).registerReceiver(statusReceiver, filter);

        // Initial check: Query service if it's already running (e.g., via a static method or a quick bind/unbind)
        // For simplicity, we'll assume it broadcasts its state on start if already running.
        // Or, we can proactively ask it.
        requestServiceStatusUpdate();

        // If MediaServerService.INSTANCE is available and initialized, use its state
        // This is a quick check but relies on service lifecycle. Broadcast is more robust.
        if (MediaServerService.INSTANCE != null && MediaServerService.INSTANCE.isInitialized()) {
            serverStatusLiveData.postValue(MediaServerService.ServerStatus.RUNNING);
            serverAddressLiveData.postValue(MediaServerService.INSTANCE.getIpAddress() + ":" + MediaServerConfiguration.UPNP_SERVER_PORT);
        } else {
            // Default to stopped if no instance or not initialized
            // This will be quickly overridden if the service is running and broadcasts its status.
            serverStatusLiveData.postValue(MediaServerService.ServerStatus.STOPPED);
        }
    }

    public static synchronized DLNAServerManager getInstance(Context context) {
        if (instance == null) {
            instance = new DLNAServerManager(context);
        }
        return instance;
    }

    public LiveData<MediaServerService.ServerStatus> getServerStatus() {
        return serverStatusLiveData;
    }

    public LiveData<String> getServerAddress() {
        return serverAddressLiveData;
    }

    public void startServer() {
        Log.d(TAG, "Requesting to start MediaServerService");
        Intent intent = new Intent(context, MediaServerService.class);
        // serverStatusLiveData.setValue(MediaServerService.ServerStatus.STARTING); // Service will broadcast this
        try {
            // For Android O and above, use startForegroundService
            // The service itself MUST call startForeground() within 5 seconds
            context.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting MediaServerService", e);
            serverStatusLiveData.setValue(MediaServerService.ServerStatus.ERROR); // Reflect error immediately
        }
    }

    public void stopServer() {
        Log.d(TAG, "Requesting to stop MediaServerService");
        Intent intent = new Intent(context, MediaServerService.class);
        // serverStatusLiveData.setValue(MediaServerService.ServerStatus.STOPPING); // Service will broadcast this
        context.stopService(intent);
    }

    // Optional: Method to explicitly ask the service for its current status
    // This could be an intent that the service handles and broadcasts back.
    public void requestServiceStatusUpdate() {
        // This might be useful if the manager is created after the service is already running
        // and missed the initial broadcast.
        // For now, MediaServerService.onStartCommand broadcasts status if already initialized.
        // If MediaServerService.INSTANCE is not null, it means it was created.
        if (MediaServerService.INSTANCE != null) {
            if (MediaServerService.INSTANCE.isInitialized()) {
                MediaServerService.INSTANCE.broadcastStatus(MediaServerService.ServerStatus.RUNNING, MediaServerService.INSTANCE.getIpAddress() + ":" + MediaServerConfiguration.UPNP_SERVER_PORT);
            } else {
                // If instance exists but not initialized, it might be in an error state or starting
                // The service's own logic should handle broadcasting the correct current state.
                // Or we can assume it's STOPPED if not initialized.
                MediaServerService.INSTANCE.broadcastStatus(MediaServerService.ServerStatus.STOPPED, null);
            }
        } else {
            // If instance is null, service is definitely not running.
            // Post STOPPED to LiveData if current value isn't already reflecting this.
            if (serverStatusLiveData.getValue() != MediaServerService.ServerStatus.STOPPED) {
                serverStatusLiveData.postValue(MediaServerService.ServerStatus.STOPPED);
            }
        }
    }


    // Call this when the DLNAServerManager is no longer needed (e.g. in Application.onTerminate for a true singleton,
    // or if this manager's lifecycle is tied to something else).
    public void cleanup() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(statusReceiver);
        instance = null; // Allow it to be GC'd and re-created if needed
    }
}
