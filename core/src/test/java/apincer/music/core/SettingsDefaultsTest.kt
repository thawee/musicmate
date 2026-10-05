package apincer.music.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `USB bit-perfect requires an explicit opt in`() {
        assertFalse(Settings.isUsbBitPerfectEnabled(null))
    }

    @Test
    fun `new installs default to listener mode`() {
        assertEquals(Constants.TAP_MODE_LISTEN, Settings.getTapActionMode(null))
    }
}
