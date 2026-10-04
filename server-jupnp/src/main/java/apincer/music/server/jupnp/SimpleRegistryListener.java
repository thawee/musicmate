package apincer.music.server.jupnp;

import android.util.Log;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;

public class SimpleRegistryListener extends DefaultRegistryListener {
    private static final String TAG = "UPnP";
    private final Runnable onRegistryChanged;

    public SimpleRegistryListener(Runnable onRegistryChanged) {
        this.onRegistryChanged = onRegistryChanged;
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        String type = device.getType() != null ? device.getType().getType() : "Unknown";
        Log.d(TAG, "Remote device added: " + device.getDetails().getFriendlyName() + " [" + type + "]");
        notifyChanged();
    }

    @Override
    public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
        String type = device.getType() != null ? device.getType().getType() : "Unknown";
       // Log.d(TAG, "Remote device updated: " + device.getDetails().getFriendlyName() + " [" + type + "]");
        notifyChanged();
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        String type = device.getType() != null ? device.getType().getType() : "Unknown";
        Log.d(TAG, "Remote device removed: " + device.getDetails().getFriendlyName() + " [" + type + "]");
        notifyChanged();
    }

    @Override
    public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
        Log.w(TAG, "Remote device discovery failed: " + (device != null ? device.getDisplayString() : "unknown"), ex);
    }

    private void notifyChanged() {
        if (onRegistryChanged != null) {
            onRegistryChanged.run();
        }
    }
}