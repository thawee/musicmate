package apincer.android.mmate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputHelperTest {
    @Test
    fun cleanDeviceNameStripsDriverPrefixesAndVendorTypos() {
        assertEquals("SNOWSKY TINY B", AudioOutputHelper.cleanDeviceName("USB-Audio - SNOWSKY TINY B"))
        assertEquals("SNOWSKY TINY B", AudioOutputHelper.cleanDeviceName("ASB-Audio - SNOWSKY TNIY B USB AUDIO"))
        assertEquals("FiiO KA13", AudioOutputHelper.cleanDeviceName("USB-Audio - FiiO KA13 USB Audio"))
        assertEquals("DragonFly Red", AudioOutputHelper.cleanDeviceName("USB-Audio - DragonFly Red USB DAC"))
        assertEquals("Phone Speaker", AudioOutputHelper.cleanDeviceName("Phone Speaker"))
    }
}
