package apincer.music.server.nio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpnpRequestCheckTest {

    @Test
    public void upnpMethods_areAccepted() {
        for (String method : new String[]{"GET", "POST", "SUBSCRIBE", "UNSUBSCRIBE", "NOTIFY"}) {
            assertEquals(method, 0, NioUPnpServerImpl.rejectStatus(method, "/dms/dev/x/desc"));
        }
    }

    @Test
    public void otherMethods_get405() {
        // HEAD used to fail inside jUPnP and come back as 500 with the exception text
        assertEquals(405, NioUPnpServerImpl.rejectStatus("HEAD", "/"));
        assertEquals(405, NioUPnpServerImpl.rejectStatus("PUT", "/dms/dev/x/desc"));
        assertEquals(405, NioUPnpServerImpl.rejectStatus("OPTIONS", "*"));
    }

    @Test
    public void malformedPath_gets400() {
        assertEquals(400, NioUPnpServerImpl.rejectStatus("GET", "/dms/a b"));
        assertEquals(400, NioUPnpServerImpl.rejectStatus("GET", "/%zz"));
    }
}
