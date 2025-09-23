package apincer.android.mmate.dlna;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import apincer.android.mmate.Settings;

public class MediaServerManager {
    private static final String TAG = "MediaServerManager";
    private MediaServerService mediaServerService;
    private boolean isBound = false;

    private final MutableLiveData<MediaServerService.ServerStatus> serverStatusLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> serverAddressLiveData = new MutableLiveData<>();
    private final Context context;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
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

    public MediaServerManager(Context context) {
        this.context = context.getApplicationContext();
        IntentFilter filter = new IntentFilter(MediaServerService.ACTION_STATUS_CHANGED);
        LocalBroadcastManager.getInstance(this.context).registerReceiver(statusReceiver, filter);
        init();
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
        try {
            // For Android O and above, use startForegroundService
            // The service itself MUST call startForeground() within 5 seconds
            intent.putExtra("START_SERVER", "YES");

            context.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting MediaServerService", e);
            serverStatusLiveData.setValue(MediaServerService.ServerStatus.ERROR); // Reflect error immediately
        }
    }

    public void stopServer() {
        Log.d(TAG, "Requesting to stop MediaServerService");
        if (isBound) {
            mediaServerService.stopServers();
        }
    }

    public void requestServiceStatusUpdate() {
        if (isBound) {
            if (mediaServerService.isInitialized()) {
                mediaServerService.broadcastStatus(MediaServerService.ServerStatus.RUNNING, mediaServerService.getIpAddress() + ":" + MediaServerConfiguration.WEB_SERVER_PORT);
            } else {
                // If instance exists but not initialized, it might be in an error state or starting
                // The service's own logic should handle broadcasting the correct current state.
                // Or we can assume it's STOPPED if not initialized.
                mediaServerService.broadcastStatus(MediaServerService.ServerStatus.STOPPED, null);
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
        // Unbind from the service in onStop to prevent memory leaks
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    public void init() {
        // Bind to the service in onStart, which is a good place to handle resources that should be active when the fragment is visible
        Intent intent = new Intent(context, MediaServerService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaServerService.MediaServerServiceBinder binder = (MediaServerService.MediaServerServiceBinder) service;
            mediaServerService = binder.getService();
            isBound = true;
            if(Settings.isAutoStartMediaServer(context) && !mediaServerService.isInitialized()) {
                startServer();
            }
            requestServiceStatusUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };
}
