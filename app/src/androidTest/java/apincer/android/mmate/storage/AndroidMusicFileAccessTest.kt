package apincer.android.mmate.storage

import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import apincer.android.mmate.ui.FolderGrantActivity
import apincer.music.core.provider.AndroidMusicFileAccess
import apincer.music.core.provider.StorageTreeRoots
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.regex.Pattern

/**
 * Library changes through a granted Music folder, the way the Play build works. Run with all-files
 * access denied: `adb shell appops set apincer.android.mmate MANAGE_EXTERNAL_STORAGE deny`.
 * The first run grants Music/ through the system folder picker.
 */
@RunWith(AndroidJUnit4::class)
class AndroidMusicFileAccessTest {

    companion object {
        private const val MUSIC = "/storage/emulated/0/Music"
        private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
        private val context get() = instrumentation.targetContext

        @BeforeClass
        @JvmStatic
        fun grantMusicFolder() {
            assertFalse("Deny all-files access first: adb shell appops set apincer.android.mmate MANAGE_EXTERNAL_STORAGE deny",
                Environment.isExternalStorageManager())
            // Always go through the picker, so the grant flow itself is tested
            for (permission in context.contentResolver.persistedUriPermissions) {
                context.contentResolver.releasePersistableUriPermission(permission.uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            assertEquals(null, StorageTreeRoots.find(context, File(MUSIC, "probe")))
            val scenario = ActivityScenario.launchActivityForResult<FolderGrantActivity>(
                FolderGrantActivity.createIntent(context, MUSIC))
            val device = UiDevice.getInstance(instrumentation)
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)use this folder"))), 15_000).click()
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))), 15_000).click()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(MUSIC, scenario.result.resultData.getStringExtra(FolderGrantActivity.EXTRA_PATH))
        }
    }

    private val base = File(MUSIC, "MMAccessTest")
    private val album = File(base, "Artist/Album")
    private val journal = File(context.cacheDir, "test-replace-journal.txt")
    private val access = AndroidMusicFileAccess(context, journal)

    private fun asset(name: String): File {
        val file = File(context.cacheDir, "asset-$name")
        instrumentation.context.assets.open("storage/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    /** Reads a library file back through the grant, by copying it to app storage. */
    private fun readBack(file: File): File {
        val copy = File(context.cacheDir, "back-${System.nanoTime()}-${file.name}")
        access.copy(file, copy)
        return copy
    }

    private fun bytes(file: File) = readBack(file).let { copy -> copy.readBytes().also { copy.delete() } }

    private fun title(file: File): String? = readBack(file).let { copy ->
        AudioFileIO.read(copy).tag?.getFirst(FieldKey.TITLE).also { copy.delete() }
    }

    /** Names in a granted folder, including files a path listing cannot see. */
    private fun names(dir: File): List<String> {
        val root = StorageTreeRoots.find(context, dir)!!
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            Uri.parse(root.treeUri), root.documentId(dir.absolutePath))
        val names = mutableListOf<String>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { while (it.moveToNext()) names += it.getString(0) }
        return names.sorted()
    }

    @Before
    fun freshAlbum() {
        journal.delete()
        access.delete(base)
        assertTrue(access.mkdirs(album))
        access.copy(asset("fixture.flac"), File(album, "01 Song.flac"))
        access.copy(asset("fixture.dsf"), File(album, "02 Song.dsf"))
        access.copy(asset("fixture.mp3"), File(album, "03 Song.mp3"))
    }

    @Test
    fun copiesThroughTheGrantWhatPathsCannotReach() {
        assertEquals(listOf("01 Song.flac", "02 Song.dsf", "03 Song.mp3"), names(album))
        assertArrayEquals(asset("fixture.dsf").readBytes(), bytes(File(album, "02 Song.dsf")))
        assertThrows("DSF is not readable by path without all-files access", IOException::class.java) {
            FileInputStream(File(album, "02 Song.dsf")).use { it.read() }
        }
    }

    @Test
    fun editsFlacAndDsfTags() {
        for (name in listOf("01 Song.flac", "02 Song.dsf", "03 Song.mp3")) {
            val file = File(album, name)
            access.edit(file) { working ->
                val audio = AudioFileIO.read(working)
                audio.tagOrCreateAndSetDefault.setField(FieldKey.TITLE, "Edited $name")
                audio.commit()
            }
            assertEquals("Edited $name", title(file))
        }
        assertEquals("No temp file may be left", listOf("01 Song.flac", "02 Song.dsf", "03 Song.mp3"), names(album))
        assertFalse("A finished replace leaves no journal", journal.exists())
    }

    @Test
    fun failedEditKeepsTheOriginal() {
        val file = File(album, "01 Song.flac")
        val before = bytes(file)

        assertThrows(IllegalStateException::class.java) {
            access.edit(file) { working ->
                working.writeText("half written")
                throw IllegalStateException("tagger failed")
            }
        }

        assertArrayEquals(before, bytes(file))
        assertEquals(listOf("01 Song.flac", "02 Song.dsf", "03 Song.mp3"), names(album))
    }

    @Test
    fun writesCoversAtomically() {
        val cover = File(album, "Cover.jpg")
        access.writeAtomically(cover) { it.write("first".toByteArray()) }
        access.writeAtomically(cover) { it.write("second".toByteArray()) }
        assertArrayEquals("second".toByteArray(), bytes(cover))

        assertThrows(IOException::class.java) {
            access.writeAtomically(cover) {
                it.write("half".toByteArray())
                throw IOException("download failed")
            }
        }
        assertArrayEquals("second".toByteArray(), bytes(cover))
        assertFalse(names(album).any { it.endsWith(".mmtmp") })

        assertThrows("A vanished folder is not recreated", IOException::class.java) {
            access.writeAtomically(File(base, "Gone/Cover.jpg")) { it.write(1) }
        }
        assertFalse(names(base).contains("Gone"))
    }

    @Test
    fun movesRenamesAndRespectsExistingFiles() {
        val flac = File(album, "01 Song.flac")
        val renamed = File(album, "01 Renamed.flac")
        assertTrue(access.move(flac, renamed, false))

        val elsewhere = File(base, "Other Artist/Other Album/01 Moved.flac")
        assertTrue("Moves across folders, creating them", access.move(renamed, elsewhere, false))
        assertEquals(listOf("02 Song.dsf", "03 Song.mp3"), names(album))
        assertEquals(listOf("01 Moved.flac"), names(elsewhere.parentFile!!))

        val dsf = File(album, "02 Song.dsf")
        val mp3 = File(album, "03 Song.mp3")
        assertFalse("An occupied target is kept", access.move(dsf, mp3, false))
        assertArrayEquals(asset("fixture.mp3").readBytes(), bytes(mp3))

        assertTrue(access.move(dsf, mp3, true))
        assertArrayEquals(asset("fixture.dsf").readBytes(), bytes(mp3))
        assertEquals(listOf("03 Song.mp3"), names(album))
    }

    @Test
    fun deletesFiles() {
        assertTrue(access.delete(File(album, "02 Song.dsf")))
        assertFalse("A missing file reports failure", access.delete(File(album, "02 Song.dsf")))
        assertEquals(listOf("01 Song.flac", "03 Song.mp3"), names(album))
    }

    @Test
    fun recoveryRenamesACompleteTempWhenTheOriginalIsGone() {
        access.writeAtomically(File(album, "Cover.jpg.mmtmp")) { it.write("complete".toByteArray()) }
        journal.writeText("${File(album, "Cover.jpg").absolutePath}\tCover.jpg.mmtmp\n")

        AndroidMusicFileAccess(context, journal).recover()

        assertArrayEquals("complete".toByteArray(), bytes(File(album, "Cover.jpg")))
        assertFalse(names(album).contains("Cover.jpg.mmtmp"))
        assertFalse(journal.exists())
    }

    @Test
    fun recoveryDeletesATempWhenTheOriginalIsStillThere() {
        access.writeAtomically(File(album, "Cover.jpg")) { it.write("original".toByteArray()) }
        access.writeAtomically(File(album, "Cover.jpg.mmtmp")) { it.write("partial".toByteArray()) }
        journal.writeText("${File(album, "Cover.jpg").absolutePath}\tCover.jpg.mmtmp\n")

        AndroidMusicFileAccess(context, journal).recover()

        assertArrayEquals("original".toByteArray(), bytes(File(album, "Cover.jpg")))
        assertFalse(names(album).contains("Cover.jpg.mmtmp"))
        assertNotNull(names(album).firstOrNull { it == "Cover.jpg" })
        assertFalse(journal.exists())
    }
}
