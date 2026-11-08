package apincer.android.mmate.audio.headphones

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HeadphoneProfileStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): HeadphoneLibrary {
        if (!file.isFile) return HeadphoneLibrary()
        return try {
            require(file.length() <= MAX_LIBRARY_BYTES) { "Headphone library is too large." }
            val content = file.inputStream().use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count != -1) {
                    require(bytes.size() + count <= MAX_LIBRARY_BYTES) { "Headphone library is too large." }
                    bytes.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
            json.decodeFromString<HeadphoneLibrary>(content).also(HeadphoneValidation::library).let { saved ->
                val migrated = saved.copy(bindings = saved.bindings.mapValues { it.value.migrated() })
                if (migrated != saved) {
                    // An unavailable rewrite must not discard a usable decoded library.
                    try { save(migrated) } catch (_: IOException) { /* Retry with the next save. */ }
                }
                migrated
            }
        } catch (_: IllegalArgumentException) {
            HeadphoneLibrary()
        } catch (_: IOException) {
            HeadphoneLibrary()
        }
    }

    @Synchronized
    fun save(library: HeadphoneLibrary) {
        HeadphoneValidation.library(library)
        val bytes = json.encodeToString(library).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LIBRARY_BYTES) { "Headphone library is too large." }
        val destination = file.absoluteFile
        val parent = destination.parentFile ?: throw IOException("Headphone library needs a parent directory.")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create headphone library directory.")
        val temporary = File.createTempFile("headphone-library-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) throw IOException("Could not replace headphone library.")
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val MAX_LIBRARY_BYTES = 4 * 1024 * 1024
    }
}
