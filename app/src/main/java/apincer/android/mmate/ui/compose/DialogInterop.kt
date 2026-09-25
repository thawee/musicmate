package apincer.android.mmate.ui.compose

import android.content.Context
import android.view.View
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import apincer.android.mmate.R
import apincer.android.mmate.ui.viewmodel.TagsViewModel
import apincer.music.core.model.Track
import apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult
import java.util.function.BiConsumer
import java.util.function.Consumer

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
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
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
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
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
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
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
    }

    @JvmStatic
    fun createQueuePageView(
        context: Context,
        state: QueueState,
        onTrackClicked: java.util.function.Consumer<Track>,
        onTrackRemoved: BiConsumer<Track, Int>,
        onClearQueue: Runnable,
        onJumpToPlaying: Runnable,
        onBrowseLibrary: Runnable
    ): View {
        return ComposeView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
                QueuePage(
                    state = state,
                    onTrackClicked = { onTrackClicked.accept(it) },
                    onTrackRemoved = { t, i -> onTrackRemoved.accept(t, i) },
                    onClearQueue = { onClearQueue.run() },
                    onJumpToPlaying = { onJumpToPlaying.run() },
                    onBrowseLibrary = { onBrowseLibrary.run() }
                )
            }
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
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
                FormatFilesDialog(
                    state = state,
                    onClose = { onClose.run() },
                    onCancel = { onCancel.run() },
                    onOk = { onOk.run() }
                )
            }
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
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                MusicMateTheme {
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

    @JvmStatic
    fun createAudioHubSheetView(
        context: Context,
        nowPlayingState: NowPlayingState,
        queueState: QueueState,
        mediaServerState: MediaServerState,
        initialTab: Int,
        onDismissRequest: Runnable,
        onSelectTargetPlayer: Runnable,
        onPlayPause: Runnable,
        onNext: Runnable,
        onPrevious: Runnable,
        onShuffleToggle: Runnable,
        onRepeatToggle: Runnable,
        onSeek: java.util.function.Consumer<Float>,
        onVolumeDown: Runnable,
        onVolumeUp: Runnable,
        onVolumeChanged: java.util.function.Consumer<Float>,
        onTrackClicked: Runnable,
        onQueueTrackClicked: java.util.function.Consumer<Track>,
        onQueueTrackRemoved: BiConsumer<Track, Int>,
        onQueueClear: Runnable,
        onQueueJumpToPlaying: Runnable,
        onQueueBrowseLibrary: Runnable,
        onEngineChanged: java.util.function.Consumer<String>,
        onStartServerClicked: Runnable,
        onStopServerClicked: Runnable,
        onCopyUrlClicked: Runnable,
        onOpenUrlClicked: Runnable,
        onQrCodeClicked: Runnable
    ): View {
        return ComposeView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                MusicMateTheme {
                    AudioHubSheet(
                        nowPlayingState = nowPlayingState,
                        queueState = queueState,
                        mediaServerState = mediaServerState,
                        initialTab = initialTab,
                        onDismissRequest = { onDismissRequest.run() },
                        onSelectTargetPlayer = { onSelectTargetPlayer.run() },
                        onPlayPause = { onPlayPause.run() },
                        onNext = { onNext.run() },
                        onPrevious = { onPrevious.run() },
                        onShuffleToggle = { onShuffleToggle.run() },
                        onRepeatToggle = { onRepeatToggle.run() },
                        onSeek = { onSeek.accept(it) },
                        onVolumeDown = { onVolumeDown.run() },
                        onVolumeUp = { onVolumeUp.run() },
                        onVolumeChanged = { onVolumeChanged.accept(it) },
                        onTrackClicked = { onTrackClicked.run() },
                        onQueueTrackClicked = { onQueueTrackClicked.accept(it) },
                        onQueueTrackRemoved = { track, index -> onQueueTrackRemoved.accept(track, index) },
                        onQueueClear = { onQueueClear.run() },
                        onQueueJumpToPlaying = { onQueueJumpToPlaying.run() },
                        onQueueBrowseLibrary = { onQueueBrowseLibrary.run() },
                        onEngineChanged = { onEngineChanged.accept(it) },
                        onStartServerClicked = { onStartServerClicked.run() },
                        onStopServerClicked = { onStopServerClicked.run() },
                        onCopyUrlClicked = { onCopyUrlClicked.run() },
                        onOpenUrlClicked = { onOpenUrlClicked.run() },
                        onQrCodeClicked = { onQrCodeClicked.run() }
                    )
                }
            }
        }
    }

    fun interface QuickFixListener {
        fun onQuickFix(actionId: String)
    }

    @JvmStatic
    @JvmOverloads
    fun setTagsHeaderBadges(
        composeView: ComposeView,
        track: Track?,
        itemCount: Int = 1,
        viewModel: TagsViewModel? = null,
        onPlayTrack: java.util.function.Consumer<Track>? = null,
        onPlayAll: java.util.function.Consumer<List<Track>>? = null,
        onQueueAll: java.util.function.Consumer<List<Track>>? = null,
        onViewInLibrary: BiConsumer<String, String>? = null,
        listener: QuickFixListener? = null
    ) {
        composeView.setContent {
            MusicMateTheme {
                val provenance = viewModel?.studioProvenanceFlow?.collectAsState()?.value
                val relatedSheetState = viewModel?.relatedTracksSheetState?.collectAsState()?.value

                Box {
                    TagPreviewHeader(
                        track = track,
                        itemCount = itemCount,
                        provenance = provenance,
                        onOpenRelated = { filterType, query, title ->
                            viewModel?.openRelatedTracks(filterType, query, title)
                        },
                        onQuickFixClick = { actionId ->
                            listener?.onQuickFix(actionId)
                        }
                    )

                    if (relatedSheetState != null && relatedSheetState.isVisible) {
                        RelatedTracksSheet(
                            state = relatedSheetState,
                            onDismissRequest = { viewModel.closeRelatedTracks() },
                            onPlayTrack = { tr -> onPlayTrack?.accept(tr) },
                            onPlayAll = { tracks -> onPlayAll?.accept(tracks) },
                            onQueueAll = { tracks -> onQueueAll?.accept(tracks) },
                            onViewInLibrary = { type, kw -> onViewInLibrary?.accept(type, kw) }
                        )
                    }
                }
            }
        }
    }

    @JvmStatic
    fun showSearchQueryDialog(
        context: Context,
        initialTitle: String,
        initialArtist: String,
        onSearch: BiConsumer<String, String>
    ) {
        val dialog = ComponentDialog(context, R.style.AlertDialogTheme)
        val vmOwner = (context as? ViewModelStoreOwner)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(dialog)
            setViewTreeSavedStateRegistryOwner(dialog)
            if (vmOwner != null) {
                setViewTreeViewModelStoreOwner(vmOwner)
            }
            setContent {
                MusicMateTheme {
                    SearchQueryContent(
                        initialTitle = initialTitle,
                        initialArtist = initialArtist,
                        onDismissRequest = { dialog.dismiss() },
                        onSearch = { t, a ->
                            dialog.dismiss()
                            onSearch.accept(t, a)
                        }
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(dialog)
            window.decorView.setViewTreeSavedStateRegistryOwner(dialog)
            if (vmOwner != null) {
                window.decorView.setViewTreeViewModelStoreOwner(vmOwner)
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        dialog.show()
    }

    @JvmStatic
    fun showSearchResultsDialog(
        context: Context,
        results: List<MusicBrainzSearchResult>,
        onSelect: Consumer<MusicBrainzSearchResult>
    ) {
        val dialog = ComponentDialog(context, R.style.AlertDialogTheme)
        val vmOwner = (context as? ViewModelStoreOwner)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(dialog)
            setViewTreeSavedStateRegistryOwner(dialog)
            if (vmOwner != null) {
                setViewTreeViewModelStoreOwner(vmOwner)
            }
            setContent {
                MusicMateTheme {
                    SearchResultsContent(
                        results = results,
                        onDismissRequest = { dialog.dismiss() },
                        onSelect = { res ->
                            dialog.dismiss()
                            onSelect.accept(res)
                        }
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(dialog)
            window.decorView.setViewTreeSavedStateRegistryOwner(dialog)
            if (vmOwner != null) {
                window.decorView.setViewTreeViewModelStoreOwner(vmOwner)
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        dialog.show()
    }
}
