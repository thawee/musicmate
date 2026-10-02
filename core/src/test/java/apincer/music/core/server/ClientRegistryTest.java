package apincer.music.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientRegistryTest {

    @Test
    public void sony_fromXAvClientInfo() {
        // Sony sends its model in X-AV-Client-Info (UMS matches BRAVIA and (KD|FW)-NNXNN)
        assertEquals("Sony BRAVIA KD-55X85J", ClientRegistry.describe("UPnP/1.0",
                "av=5.0; cn=\"Sony Corporation\"; mn=\"BRAVIA KD-55X85J\"; mv=\"1.7\";"));
        assertEquals("Sony FW-65BZ35F", ClientRegistry.describe("UPnP/1.0",
                "av=5.0; cn=\"Sony Corporation\"; mn=\"FW-65BZ35F\";"));
    }

    @Test
    public void lg_fromUserAgent() {
        assertEquals("LG webOS TV", ClientRegistry.describe("Linux/4.4.84 UPnP/1.0 LG WebOSTV DLNADOC/1.50", null));
        assertEquals("LG webOS TV", ClientRegistry.describe("Linux/5.4 UPnP/1.0 LGE webOS TV/1.0", null));
    }

    @Test
    public void otherKnownClients() {
        assertEquals("Toshiba TV", ClientRegistry.describe("TOSHIBA-DTV/1.0 UPnP/1.0", null));
        assertEquals("Samsung TV", ClientRegistry.describe("DLNADOC/1.50 SEC_HHP_[TV] Samsung Q80/1.0 UPnP/1.0", null));
        assertEquals("Windows Media Player", ClientRegistry.describe("Windows-Media-Player/12.0.19041", null));
        assertEquals("BubbleUPnP", ClientRegistry.describe("BubbleUPnP UPnP/1.1", null));
        assertEquals("Kodi", ClientRegistry.describe("Kodi/21.0 (Linux) UPnP/1.0", null));
    }

    @Test
    public void unknown_usesTheFirstToken() {
        assertEquals("FooPlayer", ClientRegistry.describe("FooPlayer/2.3 Linux", null));
        assertEquals("Unknown client", ClientRegistry.describe(null, null));
    }

    @Test
    public void firstSighting_isReportedOnce_perAddressAndName() {
        ClientRegistry registry = new ClientRegistry();
        assertTrue(registry.record("192.168.1.20", "LG WebOSTV", null, 1_000));
        assertFalse(registry.record("192.168.1.20", "LG WebOSTV", null, 2_000));
        assertTrue(registry.record("192.168.1.21", "BubbleUPnP", null, 2_000));
        assertEquals("LG webOS TV (192.168.1.20), BubbleUPnP (192.168.1.21)", registry.recentSummary(3_000, 600_000));
        registry.record("192.168.1.21", "BubbleUPnP", null, 10_000);
        // the LG was last seen at 2 s: more than 10 minutes before 602.5 s, so it is left out
        assertEquals("BubbleUPnP (192.168.1.21)", registry.recentSummary(602_500, 600_000));
    }
}
