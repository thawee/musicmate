package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.PeqBand
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** One calibrated car: the fitted correction and the curves it came from, on [FrequencyGrid.HZ]. */
@Serializable
data class CarProfile(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val bluetoothName: String? = null,
    val bluetoothAddress: String? = null,
    val bands: List<PeqBand>,
    val fromHz: Double,
    val measuredDb: List<Double>,
    val targetDb: List<Double>,
    val predictedDb: List<Double>,
    /** "UNPROCESSED" or "VOICE_RECOGNITION". */
    val micSource: String,
    /** True when the phone could not offer an unprocessed mic. */
    val approximate: Boolean,
    /** The verify sweep's result, null until it runs. */
    val verified: Boolean? = null,
    val worstDeviationDb: Double? = null
)

/** One JSON file per car in [dir]. Unreadable files are skipped, never fatal. */
class CarProfileStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Newest first. */
    fun list(): List<CarProfile> = (dir.listFiles { file -> file.name.endsWith(SUFFIX) } ?: emptyArray())
        .mapNotNull { read(it) }
        .sortedByDescending { it.createdAtMillis }

    fun get(id: String): CarProfile? = read(File(dir, id + SUFFIX))

    /** Writes to a temporary file first, so a crash never leaves half a profile. */
    fun save(profile: CarProfile) {
        dir.mkdirs()
        val temp = File(dir, profile.id + ".tmp")
        temp.writeText(json.encodeToString(CarProfile.serializer(), profile))
        if (!temp.renameTo(File(dir, profile.id + SUFFIX))) throw IOException("Could not save car profile ${profile.id}")
    }

    fun delete(id: String) = File(dir, id + SUFFIX).delete()

    private fun read(file: File): CarProfile? = try {
        if (file.exists()) json.decodeFromString(CarProfile.serializer(), file.readText()) else null
    } catch (error: SerializationException) {
        null
    } catch (error: IllegalArgumentException) {
        null
    } catch (error: IOException) {
        null
    }

    private companion object {
        const val SUFFIX = ".json"
    }
}
