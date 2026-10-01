package apincer.android.mmate.ui.viewmodel

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

class ArtworkDraftTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun selectingAndDiscardingNeverChangesOriginalArtwork() {
        val target = temporary.newFile("Cover.jpg").apply { writeText("original") }
        val draft = ArtworkDraft.stage(temporary.root, target, "new artwork".byteInputStream())
        assertEquals("original", target.readText())
        assertEquals("new artwork", draft.file.readText())
        draft.discard()
        assertEquals("original", target.readText())
        assertFalse(draft.file.exists())
    }

    @Test fun saveReplacesArtworkAndRetainsDraftUntilTagsFinishSaving() {
        val target = temporary.newFile("Cover.jpg").apply { writeText("original") }
        val draft = ArtworkDraft.stage(temporary.root, target, "new artwork".byteInputStream())
        draft.commit()
        assertEquals("new artwork", target.readText())
        assertTrue(draft.file.exists())
        draft.commit() // retry after a metadata failure is safe
        assertEquals("new artwork", target.readText())
    }

    @Test fun failedInputPreservesOriginalAndRemovesIncompleteDraft() {
        val target = temporary.newFile("Cover.jpg").apply { writeText("original") }
        val failingInput = object : InputStream() {
            override fun read(): Int = throw IOException("Unreadable selection")
        }
        assertThrows(IOException::class.java) { ArtworkDraft.stage(temporary.root, target, failingInput) }
        assertEquals("original", target.readText())
        assertEquals(listOf("Cover.jpg"), temporary.root.list()!!.toList())
    }

    @Test fun failedSaveKeepsDraftForRetry() {
        val missingParent = File(temporary.root, "missing")
        val target = File(missingParent, "Cover.jpg")
        val draft = ArtworkDraft.stage(temporary.root, target, "new artwork".byteInputStream())
        assertThrows(IOException::class.java) { draft.commit() }
        assertTrue(draft.file.exists())
        missingParent.mkdir()
        draft.commit()
        assertEquals("new artwork", target.readText())
    }

    @Test fun saveWritesTheCoverIntoEveryTargetFolder() {
        val first = temporary.newFolder("albumA")
        val second = temporary.newFolder("albumB")
        val targets = listOf(File(first, "Cover.jpg"), File(second, "Cover.jpg").apply { writeText("old") })
        val draft = ArtworkDraft.stage(temporary.root, targets, "new artwork".byteInputStream())
        draft.commit()
        assertEquals("new artwork", targets[0].readText())
        assertEquals("new artwork", targets[1].readText())
        assertEquals(targets[0], draft.target)
    }
}
