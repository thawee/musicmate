package apincer.music.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `new installs default to listener mode`() {
        assertEquals(Constants.TAP_MODE_LISTEN, Settings.getTapActionMode(null))
    }
}
