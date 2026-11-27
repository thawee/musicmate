package apincer.android.mmate.audio.calibration

import android.media.audiofx.AudioEffect
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MicProcessingTest {
    @After fun tearDown() = unmockkAll()

    @Test
    fun everyEffectThePhoneOffersIsDisabledAndHeldForRelease() {
        mockkStatic(Log::class)
        val logged = slot<String>()
        every { Log.i(any(), capture(logged)) } returns 0
        val agc = mockk<AudioEffect>(relaxed = true) { every { setEnabled(false) } returns AudioEffect.SUCCESS }
        val sessions = mutableListOf<Int>()
        val held = disableMicEffects(42, listOf(
            MicEffect("AGC", { true }, { sessions += it; agc }),
            MicEffect("NS", { false }, { error("created although unavailable") }),
            MicEffect("AEC", { true }, { null }),
            MicEffect("Other", { true }, { throw UnsupportedOperationException("no effect engine") })
        ))
        assertEquals(listOf(agc), held)
        assertEquals(listOf(42), sessions)
        verify { agc.setEnabled(false) }
        assertEquals("mic processing: AGC disabled, NS not available, AEC not created, Other failed (no effect engine)", logged.captured)
    }

    @Test
    fun anEffectThatRefusesToTurnOffIsStillHeldForRelease() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        val ns = mockk<AudioEffect>(relaxed = true) { every { setEnabled(false) } throws IllegalStateException("released") }
        assertEquals(listOf(ns), disableMicEffects(7, listOf(MicEffect("NS", { true }, { ns }))))
    }
}
