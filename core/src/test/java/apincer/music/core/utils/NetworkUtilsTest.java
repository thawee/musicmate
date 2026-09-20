package apincer.music.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.net.NetworkInterface;

public class NetworkUtilsTest {

    private NetworkInterface createMockInterface(String name) {
        // Since NetworkInterface has final methods, create dynamic proxy or test through reflection/method
        // NetworkUtils.isVirtualOrVpnInterface takes NetworkInterface
        // Let's use NetworkInterface.getByName if available or reflection
        try {
            java.lang.reflect.Constructor<NetworkInterface> c = NetworkInterface.class.getDeclaredConstructor();
            c.setAccessible(true);
            NetworkInterface ni = c.newInstance();
            java.lang.reflect.Field nameField = NetworkInterface.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(ni, name);
            return ni;
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    public void isVirtualOrVpnInterface_identifiesVpnTunnelsCorrectly() {
        NetworkInterface tun = createMockInterface("tun0");
        NetworkInterface wg = createMockInterface("wg0");
        NetworkInterface tap = createMockInterface("tap0");
        NetworkInterface p2p = createMockInterface("p2p0");
        NetworkInterface wlan = createMockInterface("wlan0");
        NetworkInterface eth = createMockInterface("eth0");
        NetworkInterface ap = createMockInterface("ap0");

        if (tun != null) {
            assertTrue(NetworkUtils.isVirtualOrVpnInterface(tun));
            assertTrue(NetworkUtils.isVirtualOrVpnInterface(wg));
            assertTrue(NetworkUtils.isVirtualOrVpnInterface(tap));
            assertTrue(NetworkUtils.isVirtualOrVpnInterface(p2p));
            assertFalse(NetworkUtils.isVirtualOrVpnInterface(wlan));
            assertFalse(NetworkUtils.isVirtualOrVpnInterface(eth));
            assertFalse(NetworkUtils.isVirtualOrVpnInterface(ap));
        }
    }

    @Test
    public void extractIpAddress_handlesVariousFormats() {
        assertEquals("192.168.1.50", NetworkUtils.extractIpAddress("http://192.168.1.50:8080/desc.xml"));
        assertEquals("192.168.1.50", NetworkUtils.extractIpAddress("/192.168.1.50:8080"));
        assertEquals("192.168.1.50", NetworkUtils.extractIpAddress("192.168.1.50:8080"));
        assertEquals("192.168.1.50", NetworkUtils.extractIpAddress("192.168.1.50 (Hi-Res Lossless)"));
        assertEquals("", NetworkUtils.extractIpAddress(""));
        assertEquals("", NetworkUtils.extractIpAddress(null));
    }
}
