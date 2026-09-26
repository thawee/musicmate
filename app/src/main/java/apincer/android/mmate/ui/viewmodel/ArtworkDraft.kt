package apincer.android.mmate.ui.viewmodel

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** An editor-owned image. The album file is touched only when Save is requested. */
class ArtworkDraft(val file: File, val target: File) {
    @Throws(IOException::class)
    fun commit() {
        // Write beside the destination so the final rename is atomic on its filesystem.
        val replacement = File.createTempFile(".cover-", ".tmp", target.parentFile)
        try {
            file.inputStream().use { input ->
                replacement.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            Files.move(replacement.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            replacement.delete()
        }
    }

    fun discard() { file.delete() }

    companion object {
        @Throws(IOException::class)
        fun stage(cacheDirectory: File, target: File, input: InputStream): ArtworkDraft {
            val file = File.createTempFile("artwork-draft-", ".img", cacheDirectory)
            try {
                input.use { source -> file.outputStream().use { source.copyTo(it) } }
                if (file.length() == 0L) throw IOException("The selected image is empty")
                return ArtworkDraft(file, target)
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }
}
