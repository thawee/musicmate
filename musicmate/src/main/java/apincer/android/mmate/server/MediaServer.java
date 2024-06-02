package apincer.android.mmate.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.Icon;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.support.connectionmanager.ConnectionManagerService;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.server.media.ContentTree;
import apincer.android.mmate.utils.ApplicationUtils;

/**
 * Based on a class from WireMe and used under Apache 2 License.
 * See https://code.google.com/p/wireme/ for more details.
 */
public class MediaServer {

    private static final String DEVICE_TYPE = "MediaServer";
    private static final int VERSION = 1;

    private final LocalDevice localDevice;

    public MediaServer (final DLNASystemId systemId, final ContentTree contentTree, final NodeConverter nodeConverter, final String hostName, final boolean printAccessLog, final URI presentationUri) throws ValidationException, IOException {
        final DeviceType type = new UDADeviceType(DEVICE_TYPE, VERSION);
        final DeviceDetails details = new DeviceDetails(
                Constants.METADATA_MODEL_NAME + " (" + hostName + ")",
                new ManufacturerDetails(Constants.METADATA_MANUFACTURER),
                new ModelDetails(Constants.METADATA_MODEL_NAME, Constants.METADATA_MODEL_DESCRIPTION, Constants.METADATA_MODEL_NUMBER),
                presentationUri);
        final Icon icon = createDeviceIcon();

        final LocalService<ContentDirectoryService> contDirSrv = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
        contDirSrv.setManager(new DefaultServiceManager(contDirSrv, ContentDirectoryService.class) {
            @Override
            protected ContentDirectoryService createServiceInstance () {
                return new ContentDirectoryService(contentTree, nodeConverter, printAccessLog);
            }
        });

        final LocalService<ConnectionManagerService> connManSrv = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        connManSrv.setManager(new DefaultServiceManager<>(connManSrv, ConnectionManagerService.class));

        this.localDevice = new LocalDevice(new DeviceIdentity(systemId.getUsi(), Constants.MIN_ADVERTISEMENT_AGE_SECONDS), type, details, icon, new LocalService[] { contDirSrv, connManSrv });
    }

    public LocalDevice getDevice () {
        return this.localDevice;
    }

    public static Icon createDeviceIcon () throws IOException {
        final InputStream res = ApplicationUtils.getAssetsAsStream(MusixMateApp.getInstance().getApplicationContext(),"icon.png");
       // final InputStream res = MediaServer.class.getResourceAsStream("/icon.png");
        if (res == null) throw new IllegalStateException("Icon not found.");
        try {
            final Icon icon = new Icon("image/png", 48, 48, 8, "icon.png", res);
            icon.validate();
            return icon;
        }
        finally {
            res.close();
        }
    }
}