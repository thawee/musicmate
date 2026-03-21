package apincer.music.server.jupnp;
import android.util.Log;

import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.model.meta.RemoteDevice;

public class SimpleRegistryListener extends DefaultRegistryListener {

    @Override
    public void remoteDeviceAdded(org.jupnp.registry.Registry registry, RemoteDevice device) {
        Log.d("UPnP", "Renderer added: " +
                device.getDetails().getFriendlyName());
    }

    @Override
    public void remoteDeviceRemoved(org.jupnp.registry.Registry registry, RemoteDevice device) {
        Log.d("UPnP", "Renderer removed: " +
                device.getDetails().getFriendlyName());
    }
}