package apincer.android.mmate.ui.compose

import android.view.Window
import android.view.WindowManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MusicCenterWindowTest {

    @Test
    fun `music center dialog delegates dimming to the Compose scrim`() {
        val window = mockk<Window>(relaxed = true)

        disablePlatformWindowDimming(window)

        verify { window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        verify { window.setDimAmount(0f) }
    }
}
