package apincer.android.mmate.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import apincer.android.mmate.R
import apincer.android.mmate.ui.compose.TagsEditorPage
import apincer.android.mmate.ui.compose.TagsEditorState
import apincer.music.core.model.Track
import apincer.music.core.repository.FileRepository
import apincer.music.core.repository.TagRepository
import apincer.music.core.utils.MusicMateExecutors
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.ThaiEncodingUtils
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import apincer.android.mmate.ui.compose.MusicMateTheme

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
                MusicMateTheme {
                    val editItems by tagsActivity.viewModel.editItemsFlow.collectAsState()
                    val displayTag by tagsActivity.viewModel.displayTagFlow.collectAsState()
                    val track = displayTag ?: editItems.firstOrNull()

                    LaunchedEffect(editItems) {
                        if (editItems.isNotEmpty() && !editorState.isAnyModified()) {
                            populateEditorInputs(editItems)
                        }
                    }

                    val albumArtistOptions = remember(track) { buildAlbumArtistOptions(track) }
                    val artistOptions = remember(track) { buildArtistOptions(track) }
                    val genreOptions = remember(track) { buildGenreOptions(track) }
                    val styleOptions = remember(track) { buildStyleOptions(track) }
                    val originOptions = remember(track) { buildOriginOptions(track) }
                    val moodOptions = remember(track) { buildMoodOptions(track) }
                    val publisherOptions = remember(track) { buildPublisherOptions(track) }

                    TagsEditorPage(
                        track = track,
                        state = editorState,
                        albumArtistOptions = albumArtistOptions,
                        artistOptions = artistOptions,
                        genreOptions = genreOptions,
                        styleOptions = styleOptions,
                        originOptions = originOptions,
                        moodOptions = moodOptions,
                        publisherOptions = publisherOptions
                    )
                }
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

    private fun buildArtistOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        if (tag != null) {
            val artist = StringUtils.trimToEmpty(tag.artist)
            if (artist.isNotEmpty() && !list.contains(artist)) list.add(artist)
        }
        try {
            tagRepos.artistList?.forEach { a ->
                val trimmed = StringUtils.trimToEmpty(a)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        } catch (e: Exception) {
            Log.e("TagsEditorFragment", "Error loading artist list", e)
        }
        return list
    }

    private fun buildGenreOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        if (tag != null) {
            val genre = StringUtils.trimToEmpty(tag.genre)
            if (genre.isNotEmpty() && !list.contains(genre)) list.add(genre)
        }
        context?.let { ctx ->
            TagRepository.getDefaultGenreList(ctx)?.forEach { g ->
                val trimmed = StringUtils.trimToEmpty(g)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        }
        try {
            tagRepos.actualGenreList?.forEach { g ->
                val trimmed = StringUtils.trimToEmpty(g)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        } catch (e: Exception) {
            Log.e("TagsEditorFragment", "Error loading actual genre list", e)
        }
        return list
    }

    private fun buildStyleOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        context?.let { ctx ->
            TagRepository.getDefaultStyleList(ctx)?.forEach { s ->
                val trimmed = StringUtils.trimToEmpty(s)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        }
        if (tag != null) {
            val style = StringUtils.trimToEmpty(tag.style)
            if (style.isNotEmpty() && !list.contains(style)) list.add(style)
        }
        return list
    }

    private fun buildOriginOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        context?.let { ctx ->
            TagRepository.getDefaultOriginList(ctx)?.forEach { o ->
                val trimmed = StringUtils.trimToEmpty(o)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        }
        if (tag != null) {
            val origin = StringUtils.trimToEmpty(tag.origin)
            if (origin.isNotEmpty() && !list.contains(origin)) list.add(origin)
        }
        return list
    }

    private fun buildMoodOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        context?.let { ctx ->
            TagRepository.getDefaultMoodList(ctx)?.forEach { m ->
                val trimmed = StringUtils.trimToEmpty(m)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        }
        if (tag != null) {
            val mood = StringUtils.trimToEmpty(tag.mood)
            if (mood.isNotEmpty() && !list.contains(mood)) list.add(mood)
        }
        return list
    }

    private fun buildPublisherOptions(tag: Track?): List<String> {
        val list = mutableListOf<String>()
        context?.let { ctx ->
            tagRepos.getDefaultPublisherList(ctx)?.forEach { p ->
                val trimmed = StringUtils.trimToEmpty(p)
                if (trimmed.isNotEmpty() && !list.contains(trimmed)) list.add(trimmed)
            }
        }
        if (tag != null) {
            val publisher = StringUtils.trimToEmpty(tag.publisher)
            if (publisher.isNotEmpty() && !list.contains(publisher)) list.add(publisher)
        }
        return list
    }

    fun initEditorInputs() {
        val editItems = tagsActivity.getEditItems()
        populateEditorInputs(editItems)
    }

    fun populateEditorInputs(items: List<Track>) {
        if (items.isEmpty()) return
        val tag = items[0]

        editorState.title = checkMultiValues(items, tag.title) { it.title }
        editorState.artist = checkMultiValues(items, tag.artist) { it.artist }
        editorState.album = checkMultiValues(items, tag.album) { it.album }
        editorState.albumArtist = checkMultiValues(items, tag.albumArtist) { it.albumArtist }
        editorState.track = checkMultiValues(items, tag.track) { it.track }
        editorState.year = checkMultiValues(items, tag.year) { it.year }
        editorState.genre = checkMultiValues(items, tag.genre) { it.genre }
        editorState.mood = checkMultiValues(items, tag.mood) { it.mood }
        editorState.style = checkMultiValues(items, tag.style) { it.style }
        editorState.origin = checkMultiValues(items, tag.origin) { it.origin }
        editorState.publisher = checkMultiValues(items, tag.publisher) { it.publisher }
        
        editorState.resetModified()
        tagsActivity.setDirty(false)
    }

    fun isModified(): Boolean {
        return editorState.isAnyModified()
    }

    private fun checkMultiValues(items: List<Track>, baseValue: String?, getter: (Track) -> String?): String {
        if (items.size <= 1) return baseValue ?: ""
        
        for (item in items) {
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
