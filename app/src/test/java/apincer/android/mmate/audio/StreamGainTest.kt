package apincer.android.mmate.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamGainTest {
    private val first = TrackGain("/music/first.flac", 0.5)
    private val second = TrackGain("/music/second.flac", 0.25)

    @Test
    fun aQueuedStreamTakesOverOnlyWhenItIsOutput() {
        val streamGain = StreamGain()
        streamGain.streamQueued(0, first)
        streamGain.outputStreamChanged(0)
        assertEquals(0.5, streamGain.current, 0.0)

        streamGain.streamQueued(1_000_000, second)
        assertEquals(0.5, streamGain.current, 0.0)
        streamGain.outputStreamChanged(1_000_000)
        assertEquals(0.25, streamGain.current, 0.0)
    }

    @Test
    fun aRewrittenGainIsTakenUpOnRefresh() {
        val streamGain = StreamGain()
        streamGain.streamQueued(0, first)
        streamGain.outputStreamChanged(0)
        first.gain = 0.8
        assertEquals(0.5, streamGain.current, 0.0)
        streamGain.refresh()
        assertEquals(0.8, streamGain.current, 0.0)
    }

    @Test
    fun aStreamWithoutLevelingAndADisabledRendererPlayAtUnity() {
        val streamGain = StreamGain()
        streamGain.streamQueued(0, first)
        streamGain.outputStreamChanged(0)
        streamGain.streamQueued(1_000_000, null)
        streamGain.outputStreamChanged(1_000_000)
        assertEquals(1.0, streamGain.current, 0.0)

        streamGain.streamQueued(2_000_000, second)
        streamGain.outputStreamChanged(2_000_000)
        streamGain.clear()
        assertEquals(1.0, streamGain.current, 0.0)
        streamGain.outputStreamChanged(2_000_000)
        assertEquals(1.0, streamGain.current, 0.0)
    }

    @Test
    fun aSeekBackToAnEarlierStreamFindsItsGainWhileItIsStillQueued() {
        val streamGain = StreamGain()
        streamGain.streamQueued(0, first)
        streamGain.streamQueued(1_000_000, second)
        streamGain.outputStreamChanged(0)
        assertEquals(0.5, streamGain.current, 0.0)
        streamGain.outputStreamChanged(1_000_000)
        assertEquals(0.25, streamGain.current, 0.0)
    }
}
