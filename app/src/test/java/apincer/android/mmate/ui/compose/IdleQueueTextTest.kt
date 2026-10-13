package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleQueueTextTest {

    @Test
    fun idleQueueText_saysWhatPlayWillStart() {
        assertEquals("Select a song", idleQueueText(0))
        assertEquals("1 track queued · press Play", idleQueueText(1))
        assertEquals("12 tracks queued · press Play", idleQueueText(12))
    }
}
