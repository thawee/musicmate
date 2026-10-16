package apincer.android.mmate.storage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.MusixMateApp
import apincer.music.core.provider.FileSystem
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Host-driven timing, skipped unless run with `-e batchProbe true`. Saves a changed comment to every
 * scanned FLAC and M4A under Music/MusicMate Demo through FileRepository.setMusicTag, the call the tag
 * editor's Save makes, and logs the time per file and what is left in the app's cache.
 */
@RunWith(AndroidJUnit4::class)
class BatchEditProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(line: String) = Log.i("BatchEditProbe", line)

    @Test
    fun saveTagsOnLargeFiles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("batchProbe") == "true")
        val app = context.applicationContext as MusixMateApp
        val files = FileSystem.access().walk(File("/storage/emulated/0/Music/MusicMate Demo")) {
            it.endsWith(".flac") || it.endsWith(".m4a")
        }.sortedBy { it.path }
        report("access=${FileSystem.access().javaClass.simpleName} files=${files.size}")
        var totalMs = 0L
        var totalBytes = 0L
        var failed = 0
        for (file in files) {
            val track = app.tagRepository.getByPath(file.absolutePath).firstOrNull() ?: continue
            track.comment = "S6 batch ${System.currentTimeMillis()}"
            val start = System.nanoTime()
            val saved = app.fileRepository.setMusicTag(track)
            val ms = (System.nanoTime() - start) / 1_000_000
            totalMs += ms
            totalBytes += file.length()
            if (!saved) failed++
            report("${file.name}: ${file.length() / 1_000_000} MB, $ms ms, saved=$saved")
        }
        report("total ${files.size} files, ${totalBytes / 1_000_000} MB, $totalMs ms, failed=$failed")
        report("cache left: ${context.cacheDir.list()?.toList()}")
    }
}
