package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CollectionLabelsTest {

    @Test
    fun `folder paths read as storage volume and folders`() {
        assertEquals("Internal storage › Music", readableFolderPath("/storage/emulated/0/Music"))
        assertEquals("Internal storage › Music › Jazz", readableFolderPath("/storage/emulated/0/Music/Jazz/"))
        assertEquals("Internal storage", readableFolderPath("/storage/emulated/0"))
        assertEquals("SD card › Albums", readableFolderPath("/storage/1A2B-3C4D/Albums"))
        assertEquals("/mnt/other/Music", readableFolderPath("/mnt/other/Music"))
    }

    @Test
    fun `add folder label names the volume only when there are several`() {
        assertEquals("Add folder", addFolderLabel("primary", 1))
        assertEquals("Internal", addFolderLabel("primary", 2))
        assertEquals("SD card", addFolderLabel("1A2B-3C4D", 2))
    }

    @Test
    fun `monogram uses up to two initials`() {
        assertEquals("AL", monogramOf("Aurora Lane"))
        assertEquals("S", monogramOf("Synthpop"))
        assertEquals("KQ", monogramOf("Kasem Quartet & Friends"))
        assertEquals("", monogramOf("  - "))
    }

    @Test
    fun `monogram colour is stable per name`() {
        assertEquals(monogramColor("Aurora Lane"), monogramColor("aurora lane"))
        assertNotEquals(monogramColor("Classical"), monogramColor("Folk"))
    }
}
