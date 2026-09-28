package apincer.android.mmate.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLayoutPolicyTest {

    @Test
    fun `expanded navigation starts at 840 dp`() {
        assertFalse(UiLayoutPolicy.useExpandedNavigation(windowWidthDp = 839))
        assertTrue(UiLayoutPolicy.useExpandedNavigation(windowWidthDp = 840))
    }

    @Test
    fun `settings use two columns from 840 dp`() {
        assertFalse(UiLayoutPolicy.useTwoColumnSettings(windowWidthDp = 839))
        assertTrue(UiLayoutPolicy.useTwoColumnSettings(windowWidthDp = 840))
    }

    @Test
    fun `music center uses a supporting pane from 840 dp`() {
        assertFalse(UiLayoutPolicy.useMusicCenterSupportingPane(windowWidthDp = 839))
        assertTrue(UiLayoutPolicy.useMusicCenterSupportingPane(windowWidthDp = 840))
    }

    @Test
    fun `compact queue header hides duration below 400 dp`() {
        assertFalse(UiLayoutPolicy.showQueueDuration(windowWidthDp = 399))
        assertTrue(UiLayoutPolicy.showQueueDuration(windowWidthDp = 400))
    }

    @Test
    fun `choice controls stack for large text`() {
        assertFalse(UiLayoutPolicy.stackChoiceControls(windowWidthDp = 432, fontScale = 1.0f))
        assertTrue(UiLayoutPolicy.stackChoiceControls(windowWidthDp = 432, fontScale = 1.3f))
    }

    @Test
    fun `choice controls stack on narrow windows`() {
        assertTrue(UiLayoutPolicy.stackChoiceControls(windowWidthDp = 359, fontScale = 1.0f))
        assertFalse(UiLayoutPolicy.stackChoiceControls(windowWidthDp = 360, fontScale = 1.0f))
    }

    @Test
    fun `transport controls require a selected track`() {
        assertFalse(UiLayoutPolicy.transportControlsEnabled(hasTrack = false))
        assertTrue(UiLayoutPolicy.transportControlsEnabled(hasTrack = true))
    }

    @Test
    fun `floating dock requires both visibility request and selected track`() {
        assertFalse(UiLayoutPolicy.showFloatingDock(isRequested = false, hasTrack = false))
        assertFalse(UiLayoutPolicy.showFloatingDock(isRequested = true, hasTrack = false))
        assertFalse(UiLayoutPolicy.showFloatingDock(isRequested = false, hasTrack = true))
        assertTrue(UiLayoutPolicy.showFloatingDock(isRequested = true, hasTrack = true))
    }
}
