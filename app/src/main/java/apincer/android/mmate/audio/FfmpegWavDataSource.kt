package apincer.android.mmate.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import apincer.music.core.codec.FfmpegPcm
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Serves a file FFmpeg decodes ([FfmpegPcm]) as a WAV image, so Media3's WavExtractor plays it
 * with duration and exact seeks: each seek reopens at a byte, which becomes a fresh FFmpeg run
 * from that sample frame. Used for ALAC, AIFF and DSD only (see [FfmpegPlayback]).
 */
@OptIn(UnstableApi::class)
class FfmpegWavDataSource(private val context: Context) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var image: WavImage? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val file = File(dataSpec.uri.path ?: throw IOException("No path: ${dataSpec.uri}"))
        val pcm = FfmpegPlayback.probe(file)
        if (dataSpec.position > pcm.wavLength()) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) null else dataSpec.length
        val opened = WavImage(pcm.header(), pcm.frameSize(), pcm.sampleRate, pcm.wavLength(), dataSpec.position, length, file.name) {
            pcm.open(context, file, it)
        }
        image = opened
        transferStarted(dataSpec)
        return opened.remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = image!!.read(buffer, offset, length)
        if (count > 0) bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        val opened = image ?: return
        image = null
        try {
            opened.close()
        } finally {
            transferEnded()
        }
    }
}

/**
 * Bytes [position] on of a WAV image: the [header] from memory, then PCM from [openPcm] started at
 * the right sample frame. A read that starts inside a frame drops that frame's leading bytes, and
 * a decode that ends early is padded with silence so the header stays true.
 */
class WavImage(
    private val header: ByteArray,
    private val frameSize: Int,
    private val sampleRate: Int,
    totalLength: Long,
    private var position: Long,
    length: Long?,
    private val name: String,
    private val openPcm: (startFrame: Long) -> InputStream
) : Closeable {
    var remaining = (totalLength - position).let { if (length != null) minOf(it, length) else it }
        private set
    private var stream: InputStream? = null
    private var pcmRead = 0L

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val wanted = minOf(length.toLong(), remaining).toInt()
        val count = if (position < header.size) {
            val n = minOf(wanted, header.size - position.toInt())
            System.arraycopy(header, position.toInt(), buffer, offset, n)
            n
        } else {
            readPcm(buffer, offset, wanted)
        }
        position += count
        remaining -= count
        return count
    }

    private fun readPcm(buffer: ByteArray, offset: Int, length: Int): Int {
        val input = stream ?: openStream()
        val n = input.read(buffer, offset, length)
        if (n > 0) {
            pcmRead += n
            return n
        }
        // Nothing at all is a failed decode, unless the read starts in the last second, where
        // ffprobe's length may run a little past the real samples
        if (pcmRead == 0L && remaining > sampleRate.toLong() * frameSize) {
            throw IOException("FFmpeg produced no audio: $name")
        }
        buffer.fill(0, offset, offset + length)
        return length
    }

    private fun openStream(): InputStream {
        val dataOffset = position - header.size
        val input = openPcm(dataOffset / frameSize)
        stream = input
        var skip = dataOffset % frameSize
        while (skip > 0) {
            val skipped = input.skip(skip)
            if (skipped <= 0) break
            skip -= skipped
        }
        return input
    }

    override fun close() {
        stream?.close()
        stream = null
    }
}

/** Which tracks play through FFmpeg, and the data source that routes them there. */
@OptIn(UnstableApi::class)
object FfmpegPlayback {
    const val SCHEME = "mmate-ffmpeg"
    private val FFMPEG_EXTENSIONS = setOf("aif", "aiff", "aifc", "dsf", "dff")
    private val probes = object : LinkedHashMap<String, FfmpegPcm>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FfmpegPcm>?) = size > 16
    }

    /**
     * True for formats local playback cannot decode itself: AIFF and DSD (no Media3 extractor),
     * by extension too, since the scan's encoding may be missing; and ALAC, which some phones'
     * decoders play as silence. AAC in .m4a stays on the normal path.
     */
    @JvmStatic
    fun needsFfmpeg(path: String?, alacOrAiff: Boolean, dsd: Boolean): Boolean =
        alacOrAiff || dsd || path?.substringAfterLast('.', "")?.lowercase() in FFMPEG_EXTENSIONS

    /** The URI that sends [file] through [FfmpegWavDataSource]. */
    @JvmStatic
    fun uriFor(file: File): Uri = Uri.fromFile(file).buildUpon().scheme(SCHEME).build()

    /** ffprobe runs once per file and change, on the loader thread. */
    fun probe(file: File): FfmpegPcm {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(probes) { probes[key] }?.let { return it }
        val pcm = FfmpegPcm.probe(file)
        synchronized(probes) { probes[key] = pcm }
        return pcm
    }

    /** [SCHEME] URIs go to [FfmpegWavDataSource], everything else to Media3's default sources. */
    @JvmStatic
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        val defaults = DefaultDataSource.Factory(app)
        return DataSource.Factory { RoutingDataSource(defaults.createDataSource(), FfmpegWavDataSource(app)) }
    }

    private class RoutingDataSource(private val default: DataSource, private val ffmpeg: DataSource) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            default.addTransferListener(transferListener)
            ffmpeg.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme == SCHEME) ffmpeg else default
            active = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) = active!!.read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
            }
        }
    }
}
