package apincer.android.mmate.storage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.provider.FileSystem
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Half of a crash check driven from the host; skipped unless run with `-e interruptProbe true`.
 * It replaces Music/MMAccessTest/Interrupted/Cover.jpg slowly through the app's installed file access,
 * so the host can force-stop the app mid-write and then start it to let recovery run.
 */
@RunWith(AndroidJUnit4::class)
class InterruptedReplaceProbe {
    @Test
    fun slowReplace() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("interruptProbe") == "true")
        val dir = File("/storage/emulated/0/Music/MMAccessTest/Interrupted")
        val cover = File(dir, "Cover.jpg")
        val access = FileSystem.access()
        access.delete(cover)
        access.mkdirs(dir)
        access.writeAtomically(cover) { it.write("original".toByteArray()) }
        Log.i("InterruptedReplaceProbe", "slow replace started")
        access.writeAtomically(cover) { out ->
            repeat(600) {
                out.write(ByteArray(1024) { 7 })
                out.flush()
                Thread.sleep(50)
            }
        }
    }
}
