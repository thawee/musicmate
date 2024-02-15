package apincer.android.mmate.dlna;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService;

/**
 * Based on a class from WireMe and used under Apache 2 License.
 * See https://code.google.com/p/wireme/ for more details.
 */
public class MediaServer {

    private static final String DEVICE_TYPE = "MediaServer";
    public static String METADATA_MANUFACTURER = "Thawee P.";

    public static String METADATA_MODEL_NAME = "MusicMate";
    public static String METADATA_MODEL_DESCRIPTION = "MusicMate MediaServer";
    public static String METADATA_MODEL_NUMBER = "v1";

    private static final int VERSION = 1;
    private static final Logger LOG = LoggerFactory.getLogger(MediaServer.class);

    private final LocalDevice localDevice;

    public MediaServer(final String hostName) throws ValidationException {
        final DeviceType type = new UDADeviceType(DEVICE_TYPE, VERSION);
        final DeviceDetails details = new DeviceDetails(METADATA_MODEL_NAME + " (" + hostName + ")",
                new ManufacturerDetails(METADATA_MANUFACTURER),
                new ModelDetails(METADATA_MODEL_NAME, METADATA_MODEL_DESCRIPTION, METADATA_MODEL_NUMBER));

        final LocalService<ContentDirectoryService> contDirSrv = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
        contDirSrv.setManager(new DefaultServiceManager<ContentDirectoryService>(contDirSrv, ContentDirectoryService.class) {
            @Override
            protected ContentDirectoryService createServiceInstance () {
                return new ContentDirectoryService();
            }
        });

        final LocalService<ConnectionManagerService> connManSrv = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        connManSrv.setManager(new DefaultServiceManager<>(connManSrv, ConnectionManagerService.class));

        final UDN usi = UDN.uniqueSystemIdentifier("MusicMate-MediaServer");
        LOG.info("uniqueSystemIdentifier: {}", usi);
        this.localDevice = new LocalDevice(new DeviceIdentity(usi), type, details, new LocalService[] { contDirSrv, connManSrv });
    }

    public LocalDevice getDevice () {
        return this.localDevice;
    }
}