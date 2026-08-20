package apincer.android.mmate.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import apincer.android.mmate.ui.compose.TagsEditorPage
import apincer.android.mmate.ui.compose.TagsEditorState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import apincer.music.core.repository.FileRepository
import apincer.music.core.repository.TagRepository
import apincer.music.core.model.Track
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import apincer.android.mmate.R
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.ThaiEncodingUtils
import apincer.android.mmate.utils.TagUIUtils
import apincer.music.core.utils.MusicMateExecutors

import apincer.music.core.utils.MusicPathTagParser

@AndroidEntryPoint
class TagsEditorFragment : Fragment() {

    @Inject
    lateinit var fileRepos: FileRepository

    @Inject
    lateinit var tagRepos: TagRepository

    private val tagsActivity: TagsActivity
        get() = activity as TagsActivity

    private val editorState = TagsEditorState()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val editItems = tagsActivity.editItems ?: emptyList()
                val track = if (editItems.isNotEmpty()) editItems[0] else null
                
                // Initialize State once on recomposition if needed
                
                val albumArtistOptions = buildAlbumArtistOptions(track)

                TagsEditorPage(
                    track = track,
                    state = editorState,
                    albumArtistOptions = albumArtistOptions
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initEditorInputs()
    }

    private fun buildAlbumArtistOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        val defaults = context?.resources?.getStringArray(R.array.default_album_artist) ?: emptyArray()
        defaults.forEach { d ->
            val trimmed = StringUtils.trimToEmpty(d)
            if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
        }
        if (tag != null) {
            val artist = StringUtils.trimToEmpty(tag.artist)
            if (artist.isNotEmpty() && !list.contains(artist)) list.add(artist)
            val albumArtist = StringUtils.trimToEmpty(tag.albumArtist)
            if (albumArtist.isNotEmpty() && !list.contains(albumArtist)) list.add(albumArtist)
        }
        return list
    }

    fun initEditorInputs() {
        val editItems = tagsActivity.editItems ?: emptyList()
        if (editItems.isEmpty()) return
        val tag = editItems[0]

        editorState.title = checkMultiValues(tag.title) { it.title }
        editorState.artist = checkMultiValues(tag.artist) { it.artist }
        editorState.album = checkMultiValues(tag.album) { it.album }
        editorState.albumArtist = checkMultiValues(tag.albumArtist) { it.albumArtist }
        editorState.track = checkMultiValues(tag.track) { it.track }
        editorState.year = checkMultiValues(tag.year) { it.year }
        editorState.genre = checkMultiValues(tag.genre) { it.genre }
        editorState.mood = checkMultiValues(tag.mood) { it.mood }
        editorState.style = checkMultiValues(tag.style) { it.style }
        editorState.origin = checkMultiValues(tag.origin) { it.origin }
        editorState.publisher = checkMultiValues(tag.publisher) { it.publisher }
        
        editorState.resetModified()
        tagsActivity.setDirty(false)
    }

    private fun checkMultiValues(baseValue: String?, getter: (Track) -> String?): String {
        val editItems = tagsActivity.editItems ?: emptyList()
        if (editItems.size <= 1) return baseValue ?: ""
        
        for (item in editItems) {
            if (baseValue != getter(item)) {
                return " - " // Multi-values marker
            }
        }
        return baseValue ?: ""
    }
    
    private fun isMultiValuesMarker(text: String): Boolean {
        return text == " - "
    }

    fun doSaveMediaItem() {
        tagsActivity.startProgressBar()
        val itemsToSave = ArrayList(tagsActivity.editItems ?: emptyList())
        val totalItems = itemsToSave.size
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val completedCount = AtomicInteger(0)

        requireActivity().currentFocus?.clearFocus()

        CompletableFuture.runAsync {
            for (item in itemsToSave) {
                buildPendingTags(item)
            }
        }.thenCompose {
            CompletableFuture.runAsync({
                for (tag in itemsToSave) {
                    try {
                        val status = fileRepos.setMusicTag(tag)
                        if (status) successCount.incrementAndGet() else failureCount.incrementAndGet()
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                        Log.e("TagsEditorFragment", "doSaveMediaItem error for ${tag.path}", e)
                    }
                    val current = completedCount.incrementAndGet()
                    tagsActivity.updateProgressBar("$current/$totalItems")
                }
            }, MusicMateExecutors.getExecutorService())
        }.thenAccept {
            tagsActivity.refreshDisplayTag()
        }.whenComplete { _, exception ->
            tagsActivity.stopProgressBar()
            if (exception == null) {
                tagsActivity.runOnUiThread {
                    Toast.makeText(context, "Saved ${successCount.get()} item(s)", Toast.LENGTH_SHORT).show()
                }
            } else {
                tagsActivity.runOnUiThread {
                    Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildPendingTags(tagUpdate: Track) {
        val multi = (tagsActivity.editItems?.size ?: 0) > 1

        if (!multi || editorState.titleModified) tagUpdate.title = buildTag(editorState.title, tagUpdate.title)
        if (!multi || editorState.trackModified) tagUpdate.track = buildTag(editorState.track, tagUpdate.track)
        if (!multi || editorState.albumModified) tagUpdate.album = buildTag(editorState.album, tagUpdate.album)
        if (!multi || editorState.artistModified) tagUpdate.artist = buildTag(editorState.artist, tagUpdate.artist)
        if (!multi || editorState.albumArtistModified) tagUpdate.albumArtist = buildTag(editorState.albumArtist, tagUpdate.albumArtist)
        if (!multi || editorState.genreModified) tagUpdate.genre = buildTag(editorState.genre, tagUpdate.genre)
        if (!multi || editorState.moodModified) tagUpdate.mood = buildTag(editorState.mood, tagUpdate.mood)
        if (!multi || editorState.styleModified) tagUpdate.style = buildTag(editorState.style, tagUpdate.style)
        if (!multi || editorState.originModified) tagUpdate.origin = buildTag(editorState.origin, tagUpdate.origin)
        if (!multi || editorState.publisherModified) tagUpdate.publisher = buildTag(editorState.publisher, tagUpdate.publisher)
        if (!multi || editorState.yearModified) tagUpdate.year = buildTag(editorState.year, tagUpdate.year)
    }

    private fun buildTag(newVal: String, oldVal: String?): String {
        val text = StringUtils.trimToEmpty(newVal)
        if (text.isEmpty() || text == " - ") return ""
        if (isMultiValuesMarker(text)) return oldVal ?: ""
        return text
    }

    fun doShowReadTagsPreview() {
        Toast.makeText(context, "Tags from Filename to be implemented in Compose", Toast.LENGTH_SHORT).show()
    }

        fun doFormatTags() {
        tagsActivity.startProgressBar()
        val itemsToProcess = ArrayList(tagsActivity.editItems ?: emptyList())
        CompletableFuture.supplyAsync {
            var totalFormatted = 0
            var thaiFixedCount = 0
            for (tag in itemsToProcess) {
                var thaiFixed = false
                if (ThaiEncodingUtils.isGarbledThai(tag.title)) {
                    tag.title = ThaiEncodingUtils.fixThaiEncoding(tag.title)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.artist)) {
                    tag.artist = ThaiEncodingUtils.fixThaiEncoding(tag.artist)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.album)) {
                    tag.album = ThaiEncodingUtils.fixThaiEncoding(tag.album)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.albumArtist)) {
                    tag.albumArtist = ThaiEncodingUtils.fixThaiEncoding(tag.albumArtist)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.genre)) {
                    tag.genre = ThaiEncodingUtils.fixThaiEncoding(tag.genre)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.composer)) {
                    tag.composer = ThaiEncodingUtils.fixThaiEncoding(tag.composer)
                    thaiFixed = true
                }
                if (thaiFixed) thaiFixedCount++
                
                tag.title = StringUtils.formatTitle(tag.title)
                tag.artist = StringUtils.formatArtists(tag.artist)
                tag.albumArtist = StringUtils.formatTitle(tag.albumArtist)
                tag.genre = StringUtils.formatTitle(tag.genre)
                if (!StringUtils.isEmpty(tag.track)) {
                    tag.track = StringUtils.formatTrack(tag.track)
                }
                if (StringUtils.isEmpty(tag.album)) {
                    // We don't have getDefaultAlbum in Kotlin easily unless imported, let's just do:
                    tag.album = StringUtils.formatTitle("Unknown Album")
                }
                totalFormatted++
            }
            Pair(totalFormatted, thaiFixedCount)
        }.thenAccept { (total, thaiFixed) ->
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
            var msg = "Reformatted $total track(s)"
            if (thaiFixed > 0) msg += " (Fixed Thai encoding on $thaiFixed)"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }.exceptionally {
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
            null
        }
    }
}
