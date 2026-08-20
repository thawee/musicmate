package apincer.android.mmate.ui.compose

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import java.util.function.BiConsumer
import apincer.music.core.model.Track

object DialogInterop {
    @JvmStatic
    fun createMusicFoldersDialogView(
        context: Context,
        directories: List<String>,
        defaultPaths: Set<String>,
        storageIds: List<String>,
        onClose: Runnable,
        onCancel: Runnable,
        onScan: BiConsumer<Boolean, List<String>>,
        onAddStorage: java.util.function.Consumer<String>
    ): View {
        return ComposeView(context).apply {
            setContent {
                MusicFoldersDialog(
                    initialDirectories = directories,
                    defaultPaths = defaultPaths,
                    storageIds = storageIds,
                    onClose = { onClose.run() },
                    onCancel = { onCancel.run() },
                    onScan = { isDeep, dirs -> onScan.accept(isDeep, dirs) },
                    onAddStorage = { sid -> onAddStorage.accept(sid) }
                )
            }
        }
    }

    @JvmStatic
    fun createActionFilesDialogView(
        context: Context,
        title: String,
        titleIconRes: Int,
        state: ActionFilesState,
        okButtonText: String,
        onClose: Runnable,
        onCancel: Runnable,
        onOk: Runnable
    ): View {
        return ComposeView(context).apply {
            setContent {
                ActionFilesDialog(
                    title = title,
                    titleIconRes = titleIconRes,
                    state = state,
                    okButtonText = okButtonText,
                    onClose = { onClose.run() },
                    onCancel = { onCancel.run() },
                    onOk = { onOk.run() }
                )
            }
        }
    }

    @JvmStatic
    fun createMediaServerPageView(
        context: Context,
        state: MediaServerState,
        onEngineChanged: java.util.function.Consumer<String>,
        onStartClicked: Runnable,
        onStopClicked: Runnable,
        onCopyUrlClicked: Runnable,
        onOpenUrlClicked: Runnable,
        onQrCodeClicked: Runnable
    ): View {
        return ComposeView(context).apply {
            setContent {
                MediaServerPage(
                    state = state,
                    onEngineChanged = { onEngineChanged.accept(it) },
                    onStartClicked = { onStartClicked.run() },
                    onStopClicked = { onStopClicked.run() },
                    onCopyUrlClicked = { onCopyUrlClicked.run() },
                    onOpenUrlClicked = { onOpenUrlClicked.run() },
                    onQrCodeClicked = { onQrCodeClicked.run() }
                )
            }
        }
    }

    @JvmStatic
    fun createQueuePageView(
        context: Context,
        state: QueueState,
        onTrackClicked: java.util.function.Consumer<Track>,
        onTrackRemoved: BiConsumer<Track, Int>,
        onClearQueue: Runnable,
        onJumpToPlaying: Runnable
    ): View {
        return ComposeView(context).apply {
            setContent {
                QueuePage(
                    state = state,
                    onTrackClicked = { onTrackClicked.accept(it) },
                    onTrackRemoved = { t, i -> onTrackRemoved.accept(t, i) },
                    onClearQueue = { onClearQueue.run() },
                    onJumpToPlaying = { onJumpToPlaying.run() }
                )
            }
        }
    }

    @JvmStatic
    fun createFormatFilesDialogView(
        context: Context,
        state: FormatFilesState,
        onClose: Runnable,
        onCancel: Runnable,
        onOk: Runnable
    ): View {
        return ComposeView(context).apply {
            setContent {
                FormatFilesDialog(
                    state = state,
                    onClose = { onClose.run() },
                    onCancel = { onCancel.run() },
                    onOk = { onOk.run() }
                )
            }
        }
    }

    @JvmStatic
    fun createNowPlayingPageView(
        context: Context,
        state: NowPlayingState,
        onPlayPause: Runnable,
        onNext: Runnable,
        onPrevious: Runnable,
        onShuffleToggle: Runnable,
        onRepeatToggle: Runnable,
        onSeek: java.util.function.Consumer<Float>,
        onVolumeDown: Runnable,
        onVolumeUp: Runnable,
        onVolumeChanged: java.util.function.Consumer<Float>,
        onTrackClicked: Runnable
    ): View {
        return ComposeView(context).apply {
            setContent {
                NowPlayingPage(
                    state = state,
                    onPlayPause = { onPlayPause.run() },
                    onNext = { onNext.run() },
                    onPrevious = { onPrevious.run() },
                    onShuffleToggle = { onShuffleToggle.run() },
                    onRepeatToggle = { onRepeatToggle.run() },
                    onSeek = { onSeek.accept(it) },
                    onVolumeDown = { onVolumeDown.run() },
                    onVolumeUp = { onVolumeUp.run() },
                    onVolumeChanged = { onVolumeChanged.accept(it) },
                    onTrackClicked = { onTrackClicked.run() }
                )
            }
        }
    }
}
