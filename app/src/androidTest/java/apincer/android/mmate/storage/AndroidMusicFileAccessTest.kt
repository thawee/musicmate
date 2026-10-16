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
import apincer.android.mmate.MusixMateApp
import apincer.android.mmate.ui.FolderGrantActivity
import apincer.music.core.Constants
import apincer.music.core.codec.AudioDecoder
import apincer.music.core.codec.TagReader
import apincer.music.core.model.AudioTag
import apincer.music.core.provider.AndroidMusicFileAccess
import apincer.music.core.provider.MusicFileProvider
import apincer.music.core.provider.PathMusicFileAccess
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
            assertEquals("Grant audio first: adb shell pm grant apincer.android.mmate android.permission.READ_MEDIA_AUDIO",
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO))
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

    /** Deletes a folder and everything in it; the access itself only deletes empty folders. */
    private fun deleteTree(file: File) {
        if (file.isDirectory) access.list(file).forEach { deleteTree(it) }
        access.delete(file)
    }

    @Before
    fun freshAlbum() {
        journal.delete()
        deleteTree(base)
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
    fun walkFindsDsfThatAPathWalkMisses() {
        val supported = { name: String -> TagReader.isSupportedFileFormat(name) }
        val all = listOf("01 Song.flac", "02 Song.dsf", "03 Song.mp3")
        assertEquals(all, access.walk(base, supported).map { it.name }.sorted())
        assertEquals(listOf("01 Song.flac", "03 Song.mp3"),
            PathMusicFileAccess().walk(base, supported).map { it.name }.sorted())
        assertEquals(all, access.list(album).map { it.name }.sorted())
    }

    /** Scan, library checks and the server's headers stat DSF by path; only opening it needs the grant. */
    @Test
    fun dsfStatsByPathWithoutTheGrant() {
        val dsf = File(album, "02 Song.dsf")
        assertTrue(dsf.exists() && dsf.isFile)
        assertEquals(asset("fixture.dsf").length(), dsf.length())
        assertTrue(dsf.lastModified() > 0)
        assertEquals(dsf.length(), java.nio.file.Files.readAttributes(dsf.toPath(),
            java.nio.file.attribute.BasicFileAttributes::class.java).size())
        assertFalse("Opening it by path is still refused", dsf.canRead())
    }

    @Test
    fun deleteKeepsAFolderHoldingFilesAPathListingHides() {
        access.delete(File(album, "01 Song.flac"))
        access.delete(File(album, "03 Song.mp3"))
        assertEquals("Only the DSF is left, and a path listing cannot see it", 0, album.list()?.size ?: 0)

        assertFalse(access.delete(album))
        assertEquals(listOf("02 Song.dsf"), names(album))
    }

    @Test
    fun readsDsfTagsThroughTheGrant() {
        val dsf = File(album, "02 Song.dsf")
        access.edit(dsf) { working ->
            val audio = AudioFileIO.read(working)
            audio.tagOrCreateAndSetDefault.setField(FieldKey.TITLE, "Read through the grant")
            audio.commit()
        }
        assertNotNull("DSF is read through the grant", access.readUri(dsf))
        assertEquals("FLAC is read by path", null, access.readUri(File(album, "01 Song.flac")))

        val audio = TagReader.readAudioFile(dsf)
        assertEquals("Read through the grant", audio.tag.getFirst(FieldKey.TITLE))
        assertEquals("dsf", audio.ext)
        assertTrue(audio.audioHeader.sampleRateAsNumber > 0)
    }

    @Test
    fun scansDsfIntoTheLibrary() {
        val app = context.applicationContext as MusixMateApp
        val dsf = File(album, "02 Song.dsf")
        access.edit(dsf) { working ->
            val audio = AudioFileIO.read(working)
            audio.tagOrCreateAndSetDefault.setField(FieldKey.TITLE, "Scanned DSF")
            audio.commit()
        }
        app.fileRepository.scanMusicFile(dsf, true)
        val tracks = app.tagRepository.getByPath(dsf.absolutePath)
        try {
            assertEquals("Scanned DSF", tracks.single().title)
            assertTrue(tracks.single().audioSampleRate > 0)
        } finally {
            tracks.forEach { app.tagRepository.removeTag(it) }
        }
    }

    @Test
    fun decodesDsfWithFFmpegThroughTheGrant() {
        val dsf = File(album, "02 Song.dsf")
        val track = AudioTag(1L).apply {
            path = dsf.absolutePath // outside apply: AudioTag has its own "album"
            audioEncoding = Constants.MEDIA_ENC_DSF
        }
        // Android's own decoder, the fallback, cannot read DSF, so PCM here came from FFmpeg
        val pcm = AudioDecoder.decodeAudio(track, 2)
        assertTrue("decoded ${pcm.size} bytes", pcm.isNotEmpty())
    }

    private fun openDescriptors() = File("/proc/self/fd").list()!!.size

    @Test
    fun streamsDsfBytesAndReleasesDescriptors() {
        val dsf = File(album, "02 Song.dsf")
        val expected = asset("fixture.dsf").readBytes()
        access.openRead(dsf).use { channel ->
            assertEquals(expected.size.toLong(), channel.size())
            val buffer = java.nio.ByteBuffer.allocate(expected.size)
            while (buffer.hasRemaining() && channel.read(buffer, buffer.position().toLong()) > 0) { }
            assertArrayEquals(expected, buffer.array())
        }

        val before = openDescriptors()
        repeat(200) { access.openRead(dsf).use { it.size() } }
        val after = openDescriptors()
        assertTrue("descriptors before $before, after $after", after - before < 10)
    }

    @Test
    fun externalPlayersGetDsfThroughTheProvider() {
        val dsf = File(album, "02 Song.dsf")
        val uri = MusicFileProvider.getUriForFile(dsf.absolutePath)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(asset("fixture.dsf").readBytes(), bytes)
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

    /** The Play build scans and streams FLAC and MP3 by path, so files written through the grant must stay readable. */
    private fun readableByPath(file: File): Boolean {
        repeat(10) {
            try {
                FileInputStream(file).use { it.read() }
                return true
            } catch (e: IOException) {
                Thread.sleep(300)
            }
        }
        return false
    }

    @Test
    fun editedAndMovedAudioStaysReadableByPath() {
        val flac = File(album, "01 Song.flac")
        access.edit(flac) { working ->
            val audio = AudioFileIO.read(working)
            audio.tagOrCreateAndSetDefault.setField(FieldKey.TITLE, "Edited")
            audio.commit()
        }
        assertTrue(readableByPath(flac))

        val moved = File(base, "Moved/01 Moved.mp3")
        assertTrue(access.move(File(album, "03 Song.mp3"), moved, false))
        assertTrue(readableByPath(moved))
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
