package apincer.android.mmate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainBackPolicyTest {

    @Test
    fun backPrecedenceIsDrawerOverlaySelectionSearchThenNavigation() {
        assertEquals(
            MainBackPolicy.Action.CLOSE_DRAWER,
            MainBackPolicy.resolve(true, true, true, true, false)
        )
        assertEquals(
            MainBackPolicy.Action.DISMISS_OVERLAY,
            MainBackPolicy.resolve(false, true, true, true, false)
        )
        assertEquals(
            MainBackPolicy.Action.FINISH_SELECTION,
            MainBackPolicy.resolve(false, false, true, true, false)
        )
        assertEquals(
            MainBackPolicy.Action.CLEAR_SEARCH,
            MainBackPolicy.resolve(false, false, false, true, false)
        )
        assertEquals(
            MainBackPolicy.Action.NAVIGATE_LIBRARY,
            MainBackPolicy.resolve(false, false, false, false, false)
        )
    }

    @Test
    fun rootWithoutTransientStateExits() {
        assertEquals(
            MainBackPolicy.Action.EXIT,
            MainBackPolicy.resolve(false, false, false, false, true)
        )
    }

}
