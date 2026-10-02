package apincer.music.server.jupnp;

import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.types.ServiceType;
import org.jupnp.support.xmicrosoft.AbstractMediaReceiverRegistrarService;

/**
 * Microsoft X_MS_MediaReceiverRegistrar:1. Windows Media Player and Xbox only list a media
 * server that offers this service. jUPnP's base class answers IsAuthorized and IsValidated with 1
 * (every device is allowed, as everywhere else on this server) and accepts RegisterDevice.
 */
public class MediaReceiverRegistrarService extends AbstractMediaReceiverRegistrarService {

    /** The service type Windows and Xbox look for. */
    static final ServiceType TYPE = new ServiceType("schemas-microsoft-com", "X_MS_MediaReceiverRegistrar", 1);

    /**
     * Binds the service with the Microsoft service type. jUPnP's annotations declare
     * "urn:microsoft.com:service:X_MS_MediaReceiverRegistrar:1", which Windows does not recognise;
     * the service id ("urn:microsoft.com:serviceId:X_MS_MediaReceiverRegistrar") is already right.
     */
    @SuppressWarnings("unchecked")
    static LocalService<MediaReceiverRegistrarService> bind() {
        AnnotationLocalServiceBinder binder = new AnnotationLocalServiceBinder();
        LocalService<MediaReceiverRegistrarService> annotated = binder.read(MediaReceiverRegistrarService.class);
        return binder.read(MediaReceiverRegistrarService.class, annotated.getServiceId(), TYPE, true, new Class[0]);
    }
}
