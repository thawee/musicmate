package apincer.android.mmate.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceConfiguration;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.android.AndroidRouter;
import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.fourthline.cling.transport.Router;

public class MediaServerService extends Service implements AndroidUpnpService {
    private static final String TAG = "MediaServerService";
    public static final String START_SERVER = "START_SERVER";
    public static final String STOP_SERVER = "STOP_SERVER";
    protected IBinder binder = new MediaServerServiceBinder();

    private boolean initialized;
    protected UpnpService upnpService;
    protected UpnpServiceConfiguration upnpServiceConf;
    protected Registry registry;
    protected ControlPoint controlPoint;

    public class MediaServerServiceBinder extends Binder {
        public AndroidUpnpService getService() {
            return MediaServerService.this;
        }
    }

    public UpnpService get() {
        return upnpService;
    }

    public UpnpServiceConfiguration getConfiguration() {
        return upnpServiceConf;
    }

    public Registry getRegistry() {
        return registry;
    }

    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onDestroy() {
        this.upnpService.shutdown();
        super.onDestroy();
    }

    /**
     * Handle service start command
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null && intent.getStringExtra(START_SERVER) != null) {
            initializeServers();
        }else if(intent!=null && intent.getStringExtra(STOP_SERVER) != null) {
            stopServers();
        }
        return START_STICKY;
    }

    private void stopServers() {
        if(initialized) {
            initialized = false;
            this.upnpService.shutdown();
        }
    }

    private void initializeServers() {
        if(!initialized) {
            upnpServiceConf = this.createConfiguration();
            this.upnpService = new UpnpServiceImpl(upnpServiceConf, new RegistryListener[0]) {
                protected Router createRouter(ProtocolFactory protocolFactory, Registry registry) {
                    return MediaServerService.this.createRouter(this.getConfiguration(), protocolFactory, MediaServerService.this);
                }

                public synchronized void shutdown() {
                    ((AndroidRouter) this.getRouter()).unregisterBroadcastReceiver();
                    super.shutdown(true);
                }
            };
            initialized = true;
        }
    }

    protected UpnpServiceConfiguration createConfiguration() {
        return new MusicMateServiceConfiguration();
    }

    protected AndroidRouter createRouter(UpnpServiceConfiguration configuration, ProtocolFactory protocolFactory, Context context) {
        return new AndroidRouter(configuration, protocolFactory, context);
    }
}
