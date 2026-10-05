package apincer.android.mmate.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbBitPerfectSessionTest {

    @Test
    fun testStatusFactoryMethods() {
        val disabled = UsbBitPerfectSession.Status.disabled()
        assertEquals(UsbBitPerfectSession.State.DISABLED, disabled.state)
        assertFalse(disabled.isRequested)
        assertEquals(-1, disabled.deviceId)

        val unavailable = UsbBitPerfectSession.Status.unavailable(
            reason = "No USB DAC connected",
            deviceId = 42,
            deviceName = "FiiO KA13"
        )
        assertEquals(UsbBitPerfectSession.State.UNAVAILABLE, unavailable.state)
        assertFalse(unavailable.isRequested)
        assertEquals(42, unavailable.deviceId)
        assertEquals("FiiO KA13", unavailable.deviceName)
        assertEquals("No USB DAC connected", unavailable.reason)

        val pending = UsbBitPerfectSession.Status.pending(
            deviceId = 10,
            deviceName = "HiBy FC4",
            sampleRate = 96000
        )
        assertEquals(UsbBitPerfectSession.State.PENDING, pending.state)
        assertFalse(pending.isRequested)
        assertEquals(10, pending.deviceId)
        assertEquals(96000, pending.sampleRate)

        val requested = UsbBitPerfectSession.Status.requested(
            deviceId = 12,
            deviceName = "AudioQuest Dragonfly",
            sampleRate = 192000,
            encoding = 4,
            reason = "Bit-perfect requested (192000Hz)"
        )
        assertEquals(UsbBitPerfectSession.State.REQUESTED, requested.state)
        assertTrue(requested.isRequested)
        assertEquals(12, requested.deviceId)
        assertEquals(192000, requested.sampleRate)
        assertEquals("Bit-perfect requested (192000Hz)", requested.reason)
    }
}
