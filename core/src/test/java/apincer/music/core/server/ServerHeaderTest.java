package apincer.music.core.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class ServerHeaderTest {

    @Test
    public void followsUpnpDeviceArchitecture_withEngineAsExtraToken() {
        // UPnP DA 1.0: "OS/version UPnP/1.0 product/version"; some TVs and Xbox check the UPnP token
        assertEquals("Android/16 UPnP/1.0 MusicMate/3.20.2-261002 SonicNIO/2.2",
                BaseServer.serverHeader("16", "3.20.2-261002", List.of("SonicNIO/2.2")));
    }

    @Test
    public void noLibraries_noTrailingSeparator() {
        // was "UpnpServer MusicMate/3.20.2 (Android/16; )"
        assertEquals("Android/16 UPnP/1.0 MusicMate/3.20.2",
                BaseServer.serverHeader("16", "3.20.2", Collections.emptyList()));
    }
}
