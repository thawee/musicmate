package apincer.music.server.jupnp;
import android.util.Log;

import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.model.meta.RemoteDevice;

public class SimpleRegistryListener extends DefaultRegistryListener {

    @Override
    public void remoteDeviceAdded(org.jupnp.registry.Registry registry, RemoteDevice device) {
        if (device.getType() != null && "MediaRenderer".equalsIgnoreCase(device.getType().getType())) {
            Log.d("UPnP", "Renderer added: " + device.getDetails().getFriendlyName());
        } else {
            Log.d("UPnP", "Device added: " + device.getDetails().getFriendlyName() + " [" + device.getType().getType() + "]");
        }
    }

    @Override
    public void remoteDeviceRemoved(org.jupnp.registry.Registry registry, RemoteDevice device) {
        if (device.getType() != null && "MediaRenderer".equalsIgnoreCase(device.getType().getType())) {
            Log.d("UPnP", "Renderer removed: " + device.getDetails().getFriendlyName());
        } else {
            Log.d("UPnP", "Device removed: " + device.getDetails().getFriendlyName() + " [" + device.getType().getType() + "]");
        }
    }
}