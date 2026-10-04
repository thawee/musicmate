package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAccessStateTest {

    @Test
    fun `missing storage is the highest priority drawer status`() {
        val state = SystemAccessState(
            hasFullStorageAccess = false,
            hasExternalPlayerAccess = true
        )

        assertEquals(SystemAccessSummary.STORAGE_NEEDED, state.summary)
    }

    @Test
    fun `optional access is reported after required storage is granted`() {
        val state = SystemAccessState(
            hasFullStorageAccess = true,
            hasExternalPlayerAccess = false
        )

        assertEquals(SystemAccessSummary.OPTIONAL_ACCESS_OFF, state.summary)
    }

    @Test
    fun `all granted access is ready`() {
        val state = SystemAccessState(
            hasFullStorageAccess = true,
            hasExternalPlayerAccess = true
        )

        assertEquals(SystemAccessSummary.READY, state.summary)
    }

    @Test
    fun `listener registration follows access transitions idempotently`() {
        assertEquals(
            ExternalPlayerListenerAction.REGISTER,
            ExternalPlayerAccessPolicy.nextAction(isRegistered = false, hasAccess = true)
        )
        assertEquals(
            ExternalPlayerListenerAction.NONE,
            ExternalPlayerAccessPolicy.nextAction(isRegistered = true, hasAccess = true)
        )
        assertEquals(
            ExternalPlayerListenerAction.UNREGISTER,
            ExternalPlayerAccessPolicy.nextAction(isRegistered = true, hasAccess = false)
        )
        assertEquals(
            ExternalPlayerListenerAction.NONE,
            ExternalPlayerAccessPolicy.nextAction(isRegistered = false, hasAccess = false)
        )
    }
}
