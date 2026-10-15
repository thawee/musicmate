package apincer.android.mmate.ui.viewmodel

import apincer.music.core.provider.FileSystem
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * An editor-owned image. The album files are touched only when Save is requested.
 * [targets] holds one cover file per folder the image applies to.
 */
class ArtworkDraft(val file: File, val targets: List<File>) {
    constructor(file: File, target: File) : this(file, listOf(target))

    val target: File get() = targets.first()

    @Throws(IOException::class)
    fun commit() {
        targets.forEach(::commitTo)
    }

    private fun commitTo(target: File) {
        FileSystem.access().writeAtomically(target) { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    fun discard() { file.delete() }

    companion object {
        @Throws(IOException::class)
        fun stage(cacheDirectory: File, target: File, input: InputStream): ArtworkDraft =
            stage(cacheDirectory, listOf(target), input)

        @Throws(IOException::class)
        fun stage(cacheDirectory: File, targets: List<File>, input: InputStream): ArtworkDraft {
            require(targets.isNotEmpty()) { "No cover target" }
            val file = File.createTempFile("artwork-draft-", ".img", cacheDirectory)
            try {
                input.use { source -> file.outputStream().use { source.copyTo(it) } }
                if (file.length() == 0L) throw IOException("The selected image is empty")
                return ArtworkDraft(file, targets)
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }
}
