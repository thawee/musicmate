package apincer.android.mmate.service;
import static apincer.music.core.server.BaseServer.CONTENT_SERVER_PORT;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.MediaServer;
import apincer.android.mmate.Settings;

public class MediaServerManager {
    private static final String TAG = "MediaServerManager";
    private MediaServer mediaServer;
    private MediaServerHostingService service;
    private boolean isBound = false;

    private final MutableLiveData<MediaServer.ServerStatus> serverStatusLiveData = new MutableLiveData<>();
    private final Context context;

    public MediaServerManager(Context context) {
        this.context = context;
        init();
    }

    public LiveData<MediaServer.ServerStatus> getServerStatus() {
        return serverStatusLiveData;
    }

    public void startServer() {
        Log.d(TAG, "Requesting to start MediaServerService");
        Intent intent = new Intent(context, MediaServerHostingService.class);
        try {
            // For Android O and above, use startForegroundService
            // The service itself MUST call startForeground() within 5 seconds
            intent.putExtra("START_SERVER", "YES");
            context.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting MediaServerService", e);
            serverStatusLiveData.setValue(MediaServer.ServerStatus.ERROR); // Reflect error immediately
        }
    }

    public void stopServer() {
        Log.d(TAG, "Requesting to stop MediaServerService");
        if (isBound) {
            mediaServer.stopServers();
        }
    }

    // Call this when the DLNAServerManager is no longer needed (e.g. in Application.onTerminate for a true singleton,
    // or if this manager's lifecycle is tied to something else).
    public void cleanup() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    public void init() {
        // Bind to the service in onStart, which is a good place to handle resources that should be active when the fragment is visible
        Intent intent = new Intent(context, MediaServerHostingService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
            MediaServerHostingService.MediaServerBinder binder = (MediaServerHostingService.MediaServerBinder) serviceBinder;
            service = binder.getService();
            mediaServer = binder.getMediaServer();
            isBound = true;
            if(Settings.isAutoStartMediaServer(context) && service!= null) {
                startServer();
            }
            mediaServer.getServerStatus().subscribe(serverStatusLiveData::postValue);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public String getServerLocation() {
        return "http://"+ BaseServer.getIpAddress()+":"+ CONTENT_SERVER_PORT;
    }
}
