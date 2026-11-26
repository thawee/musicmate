package apincer.android.mmate.ui.compose

import android.content.Context
import apincer.music.core.Settings
import apincer.music.core.model.Track
import apincer.music.core.playback.LoudnessTagger
import apincer.music.core.playback.ReplayGainManager
import apincer.music.core.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/** Compose-facing controller for measuring and writing ReplayGain tags. Close it when its owner
 * leaves composition; an in-progress measurement is cancelled cooperatively between tracks. */
class ReplayGainMeasurement(
    private val context: Context,
    private val repository: TagRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tagger: LoudnessTagger = LoudnessTagger.create(repository)
) : AutoCloseable {
    enum class Phase { IDLE, SCANNING, MEASURING, LIBRARY_SCANNED, COMPLETE, CANCELLED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val libraryScanned: Boolean = false,
        val cancelRequested: Boolean = false,
        val progress: Float = 0f,
        val currentTrack: Track? = null,
        val succeeded: Int = 0,
        val failedTracks: List<Track> = emptyList(),
        val missingTracks: List<Track> = emptyList(),
        val total: Int = 0,
        val error: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestedAlbums: List<List<Track>> = emptyList()
    private var lastRequest: (suspend () -> List<Track>)? = null

    fun measureTrack(track: Track) = start { listOf(track) }

    fun measureAlbum(track: Track) = start {
        if (track.album.isNullOrBlank()) listOf(track)
        else repository.findByAlbumAndAlbumArtist(track.album, track.albumArtist, 0, 0)
            .ifEmpty { listOf(track) }
    }

    /** Reads actual file tags on the IO dispatcher; tracks without a finite track gain are listed. */
    fun scanMissing() {
        if (job?.isCompleted == false) return
        job?.cancel()
        job = scope.launch {
            mutableState.value = State(phase = Phase.SCANNING)
            try {
                val tracks = withContext(Dispatchers.IO) { repository.getAllMusics() }
                val missing = ArrayList<Track>()
                tracks.forEachIndexed { index, track ->
                    currentCoroutineContext().ensureActive()
                    ReplayGainManager.getInstance().invalidate(track.path)
                    val info = ReplayGainManager.getInstance().getReplayGain(track.path)
                    if (info == null || !info.hasTrackGain || !info.trackGainDb.isFinite()) missing += track
                    mutableState.value = State(
                        phase = Phase.SCANNING,
                        progress = (index + 1).toFloat() / tracks.size.coerceAtLeast(1),
                        currentTrack = track,
                        total = tracks.size
                    )
                }
                mutableState.value = mutableState.value.copy(
                    phase = Phase.LIBRARY_SCANNED, currentTrack = null, libraryScanned = true,
                    missingTracks = missing.toList()
                )
            } catch (_: CancellationException) {
                mutableState.value = mutableState.value.copy(phase = Phase.CANCELLED, currentTrack = null)
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(phase = Phase.IDLE, currentTrack = null,
                    error = e.message ?: e.javaClass.simpleName)
            } finally {
                Settings.markReplayGainTagsChanged(context)
            }
        }
    }

    /** Measures only tracks found missing by scanMissing; complete albums are a separate action. */
    fun measureMissing() {
        val missing = mutableState.value.missingTracks
        start { missing }
    }

    fun retryFailed() {
        val failedPaths = mutableState.value.failedTracks.map { it.path }.toSet()
        if (failedPaths.isEmpty()) {
            lastRequest?.let { start(it) }
            return
        }
        val retry = requestedAlbums.filter { album -> album.any { it.path in failedPaths } }.flatten()
        start { retry }
    }

    fun cancel() {
        if (job?.isActive == true) {
            mutableState.value = mutableState.value.copy(cancelRequested = true)
            job?.cancel()
        }
    }

    internal fun start(resolve: suspend () -> List<Track>) {
        if (job?.isCompleted == false) return
        job?.cancel()
        lastRequest = resolve
        job = scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) { resolve() }
                val albums = LoudnessTagger.byAlbum(tracks).map { it.toList() }
                requestedAlbums = albums
                val allTracks = albums.flatten()
                val failed = LinkedHashMap<String, Track>()
                var completed = 0
                var succeeded = 0
                val coroutineJob = currentCoroutineContext()[Job]
                val previous = mutableState.value
                mutableState.value = State(
                    phase = Phase.MEASURING,
                    libraryScanned = previous.libraryScanned,
                    missingTracks = previous.missingTracks,
                    total = allTracks.size
                )
                for (album in albums) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val reported = HashSet<String>()
                    try {
                        tagger.tag(album, object : LoudnessTagger.Listener {
                            override fun onMeasuring(track: Track) {
                                mutableState.value = mutableState.value.copy(currentTrack = track)
                            }

                            override fun onTagged(track: Track, written: Boolean) {
                                reported += track.path
                                completed++
                                if (written) succeeded++ else failed[track.path] = track
                                mutableState.value = mutableState.value.copy(
                                    progress = completed.toFloat() / allTracks.size.coerceAtLeast(1),
                                    currentTrack = track,
                                    succeeded = succeeded,
                                    failedTracks = failed.values.toList(),
                                    missingTracks = if (written) mutableState.value.missingTracks.filterNot { it.path == track.path }
                                        else mutableState.value.missingTracks
                                )
                            }
                        }) { coroutineJob?.isActive != true }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        album.filterNot { it.path in reported }.forEach {
                            failed[it.path] = it
                            completed++
                        }
                        mutableState.value = mutableState.value.copy(
                            progress = completed.toFloat() / allTracks.size.coerceAtLeast(1),
                            failedTracks = failed.values.toList(),
                            error = e.message ?: e.javaClass.simpleName
                        )
                    }
                }
                mutableState.value = mutableState.value.copy(
                    phase = Phase.COMPLETE, progress = 1f, currentTrack = null
                )
            } catch (_: CancellationException) {
                mutableState.value = mutableState.value.copy(
                    phase = Phase.CANCELLED, cancelRequested = true, currentTrack = null
                )
            } catch (e: Exception) {
                val current = mutableState.value.currentTrack
                mutableState.value = mutableState.value.copy(
                    phase = Phase.COMPLETE, currentTrack = null,
                    failedTracks = if (current == null) mutableState.value.failedTracks
                    else (mutableState.value.failedTracks + current).distinctBy { it.path },
                    error = e.message ?: e.javaClass.simpleName
                )
            } finally {
                if (mutableState.value.succeeded > 0) Settings.markReplayGainTagsChanged(context)
            }
        }
    }

    override fun close() {
        job?.cancel()
        scope.cancel()
    }
}
