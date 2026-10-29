package apincer.android.mmate.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class PlayerDiagnosticsTest {
    @Test
    fun causeChainRetainsFailureTypesAndCodeLocationsWithoutPathsOrMessages() {
        val cause = FileNotFoundException("/storage/private/music.flac: missing")
        cause.stackTrace = arrayOf(StackTraceElement("Decoder", "open", "Decoder.java", 42))
        val error = IOException("secret title", cause)

        val diagnostic = AndroidPlayerController.describeErrorCauses(error)

        assertTrue(diagnostic.contains("java.io.IOException"))
        assertTrue(diagnostic.contains("java.io.FileNotFoundException@Decoder.open:42"))
        assertFalse(diagnostic.contains("/storage"))
        assertFalse(diagnostic.contains("secret title"))
    }

    @Test
    fun causeChainIsBoundedEvenForCyclicExceptions() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)

        val diagnostic = AndroidPlayerController.describeErrorCauses(first)

        assertTrue(diagnostic.length < 2000)
        assertTrue(diagnostic.endsWith(" -> ..."))
    }
}
