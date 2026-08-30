package apincer.android.mmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import apincer.android.mmate.ui.compose.TagsTechnicalPage
import apincer.music.core.codec.FFMpegHelper
import apincer.music.core.repository.FileRepository
import apincer.music.core.repository.TagRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import apincer.android.mmate.ui.compose.MusicMateTheme

@AndroidEntryPoint
class TagsTechnicalFragment : Fragment() {

    @Inject
    lateinit var fileRepos: FileRepository

    @Inject
    lateinit var tagRepos: TagRepository

    private val tagsActivity: TagsActivity
        get() = activity as TagsActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MusicMateTheme {
                    val editItems by tagsActivity.viewModel.editItemsFlow.collectAsState()
                    val displayTag by tagsActivity.viewModel.displayTagFlow.collectAsState()
                    val track = displayTag ?: editItems.firstOrNull()

                    if (track != null) {
                        TagsTechnicalPage(
                            track = track,
                            fileRepos = fileRepos
                        )
                    }
                }
            }
        }
    }

    fun doRemoveEmbedCoverart() {
        tagsActivity.startProgressBar()
        CompletableFuture.runAsync {
            tagsActivity.editItems.forEach { tag ->
                FFMpegHelper.removeCoverArt(context, tag)
                fileRepos.scanMusicFile(File(tag.path), false)
            }
        }.thenAccept {
            tagsActivity.stopProgressBar()
        }.exceptionally {
            tagsActivity.stopProgressBar()
            null
        }
    }

    fun doExtractEmbedCoverart() {
        tagsActivity.startProgressBar()
        CompletableFuture.runAsync {
            tagsActivity.editItems.forEach { tag ->
                val pathFile = File(tag.path).parentFile
                val coverArtPath = "${pathFile?.absolutePath}/Cover.jpg"
                FFMpegHelper.extractCoverArt(tag.path, File(coverArtPath), null)
            }
        }.thenAccept {
            tagsActivity.stopProgressBar()
        }.exceptionally {
            tagsActivity.stopProgressBar()
            null
        }
    }

    fun doResetTagFromFile() {
        tagsActivity.startProgressBar()
        CompletableFuture.runAsync {
            tagsActivity.editItems.forEach { tag ->
                tagRepos.removeTag(tag)
                fileRepos.scanMusicFile(File(tag.path), true)
            }
        }.thenAccept {
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
        }.exceptionally {
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
            null
        }
    }
}
