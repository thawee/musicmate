package apincer.android.mmate.storage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.authenticity.AudioAnalysisResult
import apincer.music.core.authenticity.AudioAuthenticityAnalyzer
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Host-driven check, skipped unless run with `-e authProbe <folder>`. Runs the Verify Lossless
 * analyzer on every FLAC file in the folder and logs its measurements and verdict, one line per file,
 * so a corpus of originals and fakes made from them can be scored.
 */
@RunWith(AndroidJUnit4::class)
class AuthenticityProbe {

    private fun report(line: String) = Log.i("AuthenticityProbe", line)

    @Test
    fun analyzeFolder() {
        val folder = InstrumentationRegistry.getArguments().getString("authProbe")
        assumeTrue(folder != null)
        val files = File(folder!!).listFiles { f -> f.name.endsWith(".flac") }.orEmpty().sortedBy { it.name }
        report("files=${files.size} in $folder")
        for (file in files) {
            val (rate, bits) = streamInfo(file)
            val started = System.currentTimeMillis()
            val done = CountDownLatch(1)
            var line = "error"
            AudioAuthenticityAnalyzer.analyze(file.absolutePath, rate, bits, object : AudioAuthenticityAnalyzer.Callback {
                override fun onResult(r: AudioAnalysisResult) {
                    line = "rms=%.1f peak=%.1f cutoff=%.0f kind=%s verdict=%s".format(
                        r.rms, r.peak, r.cutoffHz, r.kind, r.verdict)
                    done.countDown()
                }

                override fun onError(error: String) {
                    line = "error=$error"
                    done.countDown()
                }
            })
            done.await(10, TimeUnit.MINUTES)
            report("${file.name} $bits/$rate ${System.currentTimeMillis() - started}ms $line")
        }
    }

    /** Sample rate and bit depth from the FLAC STREAMINFO block. */
    private fun streamInfo(file: File): Pair<Int, Int> =
        RandomAccessFile(file, "r").use { raf ->
            val b = ByteArray(8)
            raf.seek(18)
            raf.readFully(b)
            val v = b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xff) }
            val rate = (v ushr 44).toInt()
            val bits = ((v ushr 36) and 0x1f).toInt() + 1
            rate to bits
        }
}
