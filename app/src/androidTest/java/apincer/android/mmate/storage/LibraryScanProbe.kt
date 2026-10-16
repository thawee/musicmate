package apincer.android.mmate.storage

import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import apincer.android.mmate.MusixMateApp
import apincer.android.mmate.worker.ScanAudioFileWorker
import apincer.music.core.Constants
import apincer.music.core.provider.FileSystem
import apincer.music.core.repository.FileRepository
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Host-driven check, skipped unless run with `-e scanProbe true`. Sets Music/ as the scan folder,
 * runs a full scan through the real worker, and logs what the library holds and any temp files left
 * in Music/, so the host can stream the logged tracks from the media server.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScanProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(line: String) = Log.i("LibraryScanProbe", line)

    @Test
    fun scanMusicFolder() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("scanProbe") == "true")
        val music = "/storage/emulated/0/Music"
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, setOf(music)).commit()
        report("allFiles=${Environment.isExternalStorageManager()} access=${FileSystem.access().javaClass.simpleName}")

        val started = System.currentTimeMillis()
        ScanAudioFileWorker.startScan(context, true)
        val work = WorkManager.getInstance(context)
        while (System.currentTimeMillis() - started < 180_000) {
            Thread.sleep(1000)
            val infos = work.getWorkInfosForUniqueWork("MusicScanWork").get()
            if (infos.isNotEmpty() && infos.all { it.state.isFinished }) {
                report("scan ${infos.map { it.state }} in ${System.currentTimeMillis() - started} ms")
                break
            }
        }

        val repos = (context.applicationContext as MusixMateApp).tagRepository
        report("library tracks=${repos.totalSongs}")
        val found = FileSystem.access().walk(File(music)) { true }
        for (file in found.sortedBy { it.path }) {
            val tracks = repos.getByPath(file.absolutePath)
            if (tracks.isNotEmpty()) report("track id=${tracks[0].id} ${file.path}")
        }
        val temps = found.filter { it.name.startsWith(".mm-") || it.name.endsWith(".mmtmp") }
        report("temp files left=${temps.map { it.path }}")

        // Cover lookups per track: a raw folder listing each time against the per-folder memo
        val tracks = found.filter { it.path.contains("/MMScale/") && it.name.endsWith(".mp3") }
        if (tracks.isNotEmpty()) {
            var start = System.nanoTime()
            tracks.forEach { FileSystem.access().list(it.parentFile!!) }
            val raw = (System.nanoTime() - start) / 1_000_000
            start = System.nanoTime()
            val covers = tracks.count { FileRepository.getFolderCoverArt(it.absolutePath) != null }
            val memo = (System.nanoTime() - start) / 1_000_000
            report("cover lookups for ${tracks.size} tracks: raw listing $raw ms, lookup with memo $memo ms, covers found $covers")
        }
    }
}
