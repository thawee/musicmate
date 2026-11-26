package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAccessStateTest {

    @Test
    fun `missing storage is the highest priority drawer status`() {
        val state = SystemAccessState(
            hasStorageAccess = false
        )

        assertEquals(SystemAccessSummary.STORAGE_NEEDED, state.summary)
    }

    @Test
    fun `granted storage access is ready`() {
        val state = SystemAccessState(
            hasStorageAccess = true
        )

        assertEquals(SystemAccessSummary.READY, state.summary)
    }
}
