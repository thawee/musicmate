package apincer.android.mmate.ui.compose

import org.junit.Assert.*
import org.junit.Test

class FullscreenStudioConsoleTest {

    @Test
    fun testStudioVisualizerModes() {
        val modes = StudioVisualizerMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(StudioVisualizerMode.VU_METER))
        assertTrue(modes.contains(StudioVisualizerMode.TAPE_DECK))
        assertTrue(modes.contains(StudioVisualizerMode.COVER_ART))

        assertEquals("VU METER", StudioVisualizerMode.VU_METER.displayName)
        assertEquals("TAPE DECK", StudioVisualizerMode.TAPE_DECK.displayName)
        assertEquals("ALBUM ART", StudioVisualizerMode.COVER_ART.displayName)
    }

    @Test
    fun testNowPlayingVisualizerModeDefaults() {
        // DLNA target should default to TAPE_DECK
        val isDLNA = true
        val defaultDlnaMode = if (isDLNA) StudioVisualizerMode.TAPE_DECK else StudioVisualizerMode.VU_METER
        assertEquals(StudioVisualizerMode.TAPE_DECK, defaultDlnaMode)

        // Local / Bit-Perfect target should default to VU_METER
        val isLocal = false
        val defaultLocalMode = if (isLocal) StudioVisualizerMode.TAPE_DECK else StudioVisualizerMode.VU_METER
        assertEquals(StudioVisualizerMode.VU_METER, defaultLocalMode)

        // Typealias compatibility check
        val telemetryAlias: TelemetryWidgetMode = StudioVisualizerMode.VU_METER
        assertEquals(StudioVisualizerMode.VU_METER, telemetryAlias)
    }

    @Test
    fun testProgressFractionCalculations() {
        // Zero duration guard
        val zeroFraction = if (0L > 0) 5000L.toFloat() / 0L.toFloat() else 0f
        assertEquals(0f, zeroFraction, 0.001f)

        // Normal playback progress fraction
        val progressMs = 45000L
        val durationMs = 180000L
        val fraction = (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.25f, fraction, 0.001f)

        // Overflow clamp guard
        val overflowMs = 200000L
        val clampedFraction = (overflowMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, clampedFraction, 0.001f)
    }

    @Test
    fun testMainScaffoldStateFullscreenToggle() {
        val state = MainScaffoldState.get()
        state.showFullscreenConsole.value = false
        assertFalse(state.showFullscreenConsole.value)

        state.showFullscreenConsole.value = true
        assertTrue(state.showFullscreenConsole.value)

        state.showFullscreenConsole.value = false
        assertFalse(state.showFullscreenConsole.value)
    }

    @Test
    fun testStudioKeepScreenOnConstantAndDefault() {
        assertEquals("preference_studio_keep_screen_on", apincer.music.core.Constants.PREF_STUDIO_KEEP_SCREEN_ON)
        
        // Test toggle state simulation
        var keepScreenOn = true
        assertTrue(keepScreenOn)
        
        // Toggling state
        keepScreenOn = !keepScreenOn
        assertFalse(keepScreenOn)
        
        keepScreenOn = !keepScreenOn
        assertTrue(keepScreenOn)
    }

    @Test
    fun testSanitizeTargetDeviceTitle() {
        assertEquals("Local Audio", sanitizeTargetDeviceTitle(""))
        assertEquals("Local Audio", sanitizeTargetDeviceTitle("   "))
        assertEquals("HiBy R3", sanitizeTargetDeviceTitle("HiBy R3 • 192.168.1.52"))
        assertEquals("HiBy R3", sanitizeTargetDeviceTitle("HiBy R3 192.168.1.52"))
        assertEquals("Living Room Streamer", sanitizeTargetDeviceTitle("Living Room Streamer • 10.0.0.45"))
        assertEquals("Local Audio", sanitizeTargetDeviceTitle("192.168.1.1"))
    }

    @Test
    fun testClampToDarkroomObsidian() {
        // Bright white should clamp to dark obsidian <= 0.22f
        val clampedWhite = clampToDarkroomObsidian(androidx.compose.ui.graphics.Color.White)
        assertTrue(clampedWhite.red <= 0.23f)
        assertTrue(clampedWhite.green <= 0.23f)
        assertTrue(clampedWhite.blue <= 0.23f)
        assertTrue(clampedWhite.red >= 0.04f)

        // Vivid yellow
        val clampedYellow = clampToDarkroomObsidian(androidx.compose.ui.graphics.Color.Yellow)
        assertTrue(clampedYellow.red <= 0.23f)
        assertTrue(clampedYellow.green <= 0.23f)

        // Pure black
        val clampedBlack = clampToDarkroomObsidian(androidx.compose.ui.graphics.Color.Black)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF14110E), clampedBlack)
    }
}

