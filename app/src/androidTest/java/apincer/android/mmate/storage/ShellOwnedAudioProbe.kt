package apincer.android.mmate.storage

import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.provider.FileSystem
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

/**
 * Host-driven check, skipped unless run with `-e shellProbe true`. The host pushes
 * Music/MMShell/a.flac and b.mp3 (owned by the shell, like files copied from a computer). This edits
 * both through the app's installed access, moves b.mp3, and logs whether each result is still
 * readable by path and how MediaStore classifies it.
 */
@RunWith(AndroidJUnit4::class)
class ShellOwnedAudioProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(line: String) = Log.i("ShellOwnedAudioProbe", line)

    private fun pathReadable(file: File): String {
        repeat(10) { attempt ->
            try {
                FileInputStream(file).use { it.read() }
                return "readable after ${attempt * 500} ms"
            } catch (e: Exception) {
                Thread.sleep(500)
            }
        }
        return "NOT readable by path after 5 s"
    }

    private fun mediaStoreAudio(file: File): String {
        context.contentResolver.query(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Media.MIME_TYPE), "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(file.absolutePath), null)?.use { c ->
            return if (c.moveToFirst()) "audio row mime=${c.getString(0)}" else "no audio row"
        }
        return "null cursor"
    }

    @Test
    fun editAndMoveShellOwnedFiles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("shellProbe") == "true")
        val dir = File("/storage/emulated/0/Music/MMShell")
        val access = FileSystem.access()
        report("access=${access.javaClass.simpleName}")
        for (name in listOf("a.flac", "b.mp3")) {
            report("control before edit $name: ${pathReadable(File(dir, name))}; ${mediaStoreAudio(File(dir, name))}")
        }

        for (name in listOf("a.flac", "b.mp3")) {
            val file = File(dir, name)
            val result = runCatching {
                access.edit(file) { working ->
                    val audio = AudioFileIO.read(working)
                    audio.tagOrCreateAndSetDefault.setField(FieldKey.TITLE, "Probe $name")
                    audio.commit()
                }
            }
            report("edit $name: ${result.exceptionOrNull() ?: "ok"}")
            report("after edit $name: ${pathReadable(file)}; ${mediaStoreAudio(file)}")
        }

        val moved = File(dir, "Moved/b2.mp3")
        report("move b.mp3 -> Moved/b2.mp3: ${access.move(File(dir, "b.mp3"), moved, false)}")
        report("after move: ${pathReadable(moved)}; ${mediaStoreAudio(moved)}")
    }
}
