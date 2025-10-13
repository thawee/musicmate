package apincer.android.mmate.dlna;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class MediaServerManager {
    private static final String TAG = "MediaServerManager";
    private MediaServerService mediaServerService;
    private boolean isBound = false;

    private final MutableLiveData<MediaServerService.ServerStatus> serverStatusLiveData = new MutableLiveData<>();
    private final Context context;

    public MediaServerManager(Context context) {
        this.context = context;
        init();
    }

    public LiveData<MediaServerService.ServerStatus> getServerStatus() {
        return serverStatusLiveData;
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

    // Call this when the DLNAServerManager is no longer needed (e.g. in Application.onTerminate for a true singleton,
    // or if this manager's lifecycle is tied to something else).
    public void cleanup() {
        //LocalBroadcastManager.getInstance(context).unregisterReceiver(statusReceiver);
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
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaServerService.MediaServerServiceBinder binder = (MediaServerService.MediaServerServiceBinder) service;
            mediaServerService = binder.getService();
            isBound = true;
            //if(Settings.isAutoStartMediaServer(context) && !mediaServerService.isInitialized()) {
            //    startServer();
           // }
            mediaServerService.getServerStatus().subscribe(serverStatusLiveData::postValue);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public String getServerLocation() {
        return "http://"+mediaServerService.getIpAddress()+":"+MediaServerConfiguration.WEB_SERVER_PORT;
    }
}
