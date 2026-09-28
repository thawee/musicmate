package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import apincer.music.core.model.AudioTag
import apincer.music.core.model.Track
import apincer.music.core.playback.PlaybackState

internal object UxPreviewFixtures {
    val tracks: List<Track>
        get() = listOf(
            track(
                id = 101,
                title = "So What",
                artist = "Miles Davis",
                album = "Kind of Blue",
                encoding = "FLAC",
                sampleRate = 96_000,
                bitDepth = 24,
                durationSeconds = 545.0
            ),
            track(
                id = 102,
                title = "Blue in Green",
                artist = "Miles Davis",
                album = "Kind of Blue",
                encoding = "FLAC",
                sampleRate = 44_100,
                bitDepth = 16,
                durationSeconds = 337.0
            ),
            track(
                id = 103,
                title = "The Girl from Ipanema",
                artist = "Stan Getz & João Gilberto",
                album = "Getz/Gilberto",
                encoding = "ALAC",
                sampleRate = 88_200,
                bitDepth = 24,
                durationSeconds = 317.0
            ),
            track(
                id = 104,
                title = "Take Five",
                artist = "The Dave Brubeck Quartet",
                album = "Time Out",
                encoding = "AAC",
                sampleRate = 48_000,
                bitDepth = 16,
                durationSeconds = 324.0
            )
        )

    fun populatedLibrary(): MainScaffoldState = baseState().apply {
        tracks.addAll(UxPreviewFixtures.tracks)
    }

    fun emptyLibrary(): MainScaffoldState = baseState().apply {
        libraryEmpty.value = true
        headerStatsText.value = "No music indexed"
    }

    fun searchNoResults(): MainScaffoldState = baseState().apply {
        searchQuery.value = "Coltrane live"
        hasActiveMusicFilters.value = true
        headerStatsText.value = "No matches"
    }

    fun selectedLibrary(): MainScaffoldState = populatedLibrary().apply {
        selectedTracks.add(tracks[0])
        selectedTracks.add(tracks[2])
        headerStatsText.value = "2 selected"
    }

    fun nowPlayingEmpty(): NowPlayingState = NowPlayingState().apply {
        targetTitle.value = "This device"
        targetBadge.value = "LOCAL"
        targetDetails.value = "Built-in audio output"
    }

    fun nowPlayingPlaying(): NowPlayingState = NowPlayingState().apply {
        track.value = tracks.first()
        playbackState.value = PlaybackState().apply { currentState = PlaybackState.State.PLAYING }
        progressMs.value = 183_000L
        durationMs.value = 545_000L
        specsVerdict.value = "HI-RES LOSSLESS"
        specsFormat.value = "FLAC • 24-bit / 96 kHz"
        specsBitrate.value = "2,744 kbps"
        specsDr.value = "DR12"
        specsReplayGain.value = "-4.2 dB"
        specsFileSize.value = "178 MB"
        volume.value = 0.64f
        signalPathSteps.addAll(listOf("FLAC", "PCM 24/96", "USB DAC"))
        targetTitle.value = "Reference DAC"
        targetBadge.value = "LOCAL"
        targetDetails.value = "Exclusive USB output"
    }

    fun emptyQueue(): QueueState = QueueState(emptyList(), null).apply {
        totalDurationText = "0 min"
    }

    fun populatedQueue(): QueueState = QueueState(tracks, null).apply {
        totalDurationText = "25 min"
    }

    fun serverStopped(): MediaServerState = MediaServerState().apply {
        serverStatusText = "Server Stopped"
        isServerRunning = false
        isNetworkAvailable = true
        currentEngine = "httpcore"
        engineDescription = "Balanced compatibility and efficiency"
    }

    fun serverRunning(): MediaServerState = MediaServerState().apply {
        serverStatusText = "MusicMate Server"
        isServerRunning = true
        isNetworkAvailable = true
        serverUrl = "http://192.168.1.42:9000"
        broadcastInfo = "DLNA 1.5 • Wi-Fi • Port 9000"
        currentEngine = "httpcore"
        engineDescription = "Balanced compatibility and efficiency"
        qrCodeBitmap = null
    }

    private fun baseState() = MainScaffoldState().apply {
        headerStatsText.value = "4 tracks • 25 min"
        isFloatingDockVisible.value = false
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        encoding: String,
        sampleRate: Long,
        bitDepth: Int,
        durationSeconds: Double
    ): Track = AudioTag(id).apply {
        setUniqueKey("preview-track-$id")
        setPath("/preview/music/$id.$encoding")
        setSimpleName("$id.$encoding")
        setTitle(title)
        setArtist(artist)
        setAlbum(album)
        setAudioEncoding(encoding)
        setFileType(encoding.lowercase())
        setAudioSampleRate(sampleRate)
        setAudioBitsDepth(bitDepth)
        setAudioChannels("2")
        setAudioDuration(durationSeconds)
        setDynamicRange(12.0)
        setDrScore(12.0)
        setQualityInd(if (bitDepth >= 24) "Hi-Res" else "Lossless")
        setIsManaged(true)
    }
}

@Composable
internal fun PreviewTrackArtwork(track: Track) {
    val background = when ((track.id % 4).toInt()) {
        0 -> Color(0xFF254B62)
        1 -> Color(0xFF5B3A55)
        2 -> Color(0xFF355748)
        else -> Color(0xFF67452F)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = track.title.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
