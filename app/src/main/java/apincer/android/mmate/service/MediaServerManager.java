package apincer.android.mmate.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import javax.inject.Inject;
import javax.inject.Singleton;

import apincer.music.core.Constants;
import apincer.music.core.server.spi.MediaServerHub;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * A Singleton manager class that acts as the central point of control and information
 * for the MediaServerHubService. It handles starting, stopping, and binding to the service,
 * and exposes the service's status to the rest of the application via LiveData.
 * This class is designed to be injected by Hilt into ViewModels.
 */
@Singleton
public class MediaServerManager {
    private static final String TAG = "MediaServerManager";

    // Actions for service communication, ensures consistency
    public static final String ACTION_START_SERVER = "apincer.android.mmate.action.START_SERVER";
    public static final String ACTION_STOP_SERVER = "apincer.android.mmate.action.STOP_SERVER";

    private final Context context;
    private MediaServerHubService mediaServerService;
    private boolean isBound = false;

    // LiveData to report the server's status to the UI
    private final MutableLiveData<MediaServerHub.ServerStatus> serverStatusLiveData = new MutableLiveData<>(MediaServerHub.ServerStatus.STOPPED);

    // These are used to observe the LiveData coming directly from the Service.
    private LiveData<MediaServerHub.ServerStatus> serviceStatusLiveData;
    private final Observer<MediaServerHub.ServerStatus> statusObserver = status -> {
        if (status != null) {
            serverStatusLiveData.postValue(status);
        }
    };

    @Inject
    public MediaServerManager(@ApplicationContext Context context) {
        this.context = context;
    }

    /**
     * The callback interface that receives results from the bindService call.
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        /**
         * Called by the Android system when the connection to the service has been established.
         * @param name The component name of the service that has been connected.
         * @param service The IBinder of the service, which we can use to get the service instance.
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            // We've bound to MediaServerHubService, cast the IBinder and get the instance.
            MediaServerHubService.MediaServerHubBinder binder = (MediaServerHubService.MediaServerHubBinder) service;
            mediaServerService = binder.getService();
            isBound = true;

            // CRITICAL: Once connected, start observing the LiveData from the service.
            // This ensures the manager always reflects the service's true state.
            serviceStatusLiveData = mediaServerService.getStatusLiveData();
            serviceStatusLiveData.observeForever(statusObserver);
        }

        /**
         * Called when the connection with the service has been unexpectedly disconnected
         * (i.e., its process crashed). This is NOT called when the client unbinds.
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Service disconnected unexpectedly");
            mediaServerService = null;
            isBound = false;
            serverStatusLiveData.postValue(MediaServerHub.ServerStatus.STOPPED);
        }
    };

    /**
     * Initiates the binding process to the MediaServerHubService.
     * This allows the manager to call methods directly on the service.
     */
    public void doBindService() {
        // Prevent multiple binding attempts
        if (isBound) {
            return;
        }

        Log.d(TAG, "Binding to MediaServerService...");
        Intent intent = new Intent(context, MediaServerHubService.class);
        // BIND_AUTO_CREATE will create the service if it's not already running.
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds from the MediaServerHubService.
     * This should be called when the UI is no longer visible to prevent memory leaks.
     */
    public void doUnbindService() {
        if (isBound) {
            Log.d(TAG, "Unbinding from MediaServerService");
            // CRITICAL: Always remove the observer when unbinding to prevent leaks.
            if (serviceStatusLiveData != null) {
                serviceStatusLiveData.removeObserver(statusObserver);
            }
            context.unbindService(serviceConnection);
            isBound = false;
            mediaServerService = null;
        }
    }

    // --- Service Control Methods ---

    public void startServer() {
        Log.d(TAG, "Requesting to start MediaServerService");
        serverStatusLiveData.setValue(MediaServerHub.ServerStatus.STARTING);
        Intent intent = new Intent(context, MediaServerHubService.class);
        intent.setAction(ACTION_START_SERVER);
        context.startForegroundService(intent);
    }

    public void stopServer() {
        Log.d(TAG, "Requesting to stop MediaServerService");
        serverStatusLiveData.setValue(MediaServerHub.ServerStatus.STOPPED);
        Intent intent = new Intent(context, MediaServerHubService.class);
        intent.setAction(ACTION_STOP_SERVER);
        context.startService(intent);
    }

    // --- Data Accessor Methods for the ViewModel ---

    public LiveData<MediaServerHub.ServerStatus> getServerStatus() {
        // A better implementation might involve the service itself pushing status updates
        // to this manager, but for now, this reflects the start/stop commands.
        return serverStatusLiveData;
    }

    public String getLibraryName() {
        if (isBound && mediaServerService != null) {
            return mediaServerService.getLibraryName();
        }
        return " - "; // Default
    }

    public String getServerLocationUrl() {
        return Constants.getPresentationUrl();
    }
}
