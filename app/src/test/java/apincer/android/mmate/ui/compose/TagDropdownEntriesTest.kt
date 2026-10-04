package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDropdownEntriesTest {
    private val presets = listOf("Jazz", "Pop", "Rock")
    private val library = listOf("pop", "Shoegaze")

    @Test
    fun `opening shows every choice with library extras and clear`() {
        val entries = dropdownEntries(presets, library, query = "Rock", typed = false, hasValue = true)
        assertEquals(DropdownEntry.Clear, entries.first())
        val values = entries.filterIsInstance<DropdownEntry.Value>().map { it.text }
        // The current value does not filter; "pop" duplicates a preset and is hidden
        assertEquals(listOf("Jazz", "Pop", "Rock", "Shoegaze"), values)
        assertTrue(entries.contains(DropdownEntry.Header("In your library")))
    }

    @Test
    fun `typing filters and shows nothing unrelated`() {
        val values = dropdownEntries(presets, library, query = "ro", typed = true, hasValue = true)
            .filterIsInstance<DropdownEntry.Value>().map { it.text }
        assertEquals(listOf("Rock"), values)
        assertTrue(dropdownEntries(presets, library, query = "xyz", typed = true, hasValue = true).isEmpty())
    }
}
