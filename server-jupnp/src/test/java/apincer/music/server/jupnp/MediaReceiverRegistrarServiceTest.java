package apincer.music.server.jupnp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jupnp.model.meta.LocalService;

import org.junit.Test;

public class MediaReceiverRegistrarServiceTest {

    @Test
    public void bindsAsTheMicrosoftRegistrar_withItsThreeActions() {
        LocalService<MediaReceiverRegistrarService> service = MediaReceiverRegistrarService.bind();
        // Windows Media Player and Xbox look for exactly this type
        assertEquals("urn:schemas-microsoft-com:service:X_MS_MediaReceiverRegistrar:1",
                service.getServiceType().toString());
        assertEquals("urn:microsoft.com:serviceId:X_MS_MediaReceiverRegistrar",
                service.getServiceId().toString());
        assertNotNull(service.getAction("IsAuthorized"));
        assertNotNull(service.getAction("IsValidated"));
        assertNotNull(service.getAction("RegisterDevice"));
    }

    @Test
    public void everyDeviceIsAuthorizedAndValidated() {
        MediaReceiverRegistrarService registrar = new MediaReceiverRegistrarService();
        assertEquals(1, registrar.isAuthorized("any-device"));
        assertEquals(1, registrar.isValidated("any-device"));
    }
}
