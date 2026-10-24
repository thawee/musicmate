package apincer.android.mmate.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class FlacDecoderSelectorTest {
    @Test
    fun platformDecoderComesFirstAndTheRestKeepTheirOrder() {
        val ranked = listOf("c2.sec.flac.decoder", "c2.qti.flac.sw.decoder", "c2.android.flac.decoder")
        assertEquals(
            listOf("c2.android.flac.decoder", "c2.sec.flac.decoder", "c2.qti.flac.sw.decoder"),
            FlacDecoderSelector.preferPlatform(ranked) { it }
        )
    }
}
