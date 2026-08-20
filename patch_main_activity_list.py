import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Replace adapter and RecyclerView fields
content = re.sub(r'private MusicTagAdapter adapter;', 'private apincer.music.core.model.SearchCriteria currentCriteria = new apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY);', content)
content = re.sub(r'private RecyclerView mRecyclerView;', '', content)
content = re.sub(r'private MusicTagAdapter.TrackItemDecoration itemDecoration;', '', content)
content = re.sub(r'import androidx.recyclerview.widget.RecyclerView;', 'import androidx.compose.ui.platform.ComposeView;', content)
content = re.sub(r'import androidx.recyclerview.widget.LinearLayoutManager;', '', content)

# Remove fast scroller builder
content = re.sub(r'new FastScrollerBuilder\(mRecyclerView\).*?build\(\);', '', content, flags=re.DOTALL)

# In onCreate, replace mRecyclerView setup with ComposeView
setup_start = content.find("mRecyclerView = findViewById(R.id.recycler_view);")
setup_end = content.find("adapter.registerAdapterDataObserver(", setup_start)
if setup_start != -1 and setup_end != -1:
    new_setup = """ComposeView composeListView = findViewById(R.id.compose_list_view);
        apincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);
        """
    content = content[:setup_start] + new_setup + content[setup_end:]

# Fix registerAdapterDataObserver
content = re.sub(r'adapter\.registerAdapterDataObserver\(.*?\}\);', '', content, flags=re.DOTALL)
content = re.sub(r'adapter\.setClickListener\(.*?\);', '', content)
content = re.sub(r'adapter\.setOnCoverArtClickListener\(.*?\);', '', content)

# In setupSelectionObserver, replace adapter.getMusicTag
content = content.replace("adapter.getMusicTag(item.intValue());", "apincer.android.mmate.ui.compose.ListInterop.getTracks().get(item.intValue());")
content = content.replace("adapter.getItemCount()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().size()")

# We need a getTracks method in ListInterop, let's add it soon.

# Fix viewmodel observer
obs_start = content.find("viewModel.getMusicItems().observe(this, tags -> {")
obs_end = content.find("});", obs_start) + 3
new_obs = """viewModel.getMusicItems().observe(this, tags -> {
            apincer.android.mmate.ui.compose.ListInterop.updateTracks(tags);
            swipeRefreshLayout.setRefreshing(false);
            if (tags.isEmpty()) {
                emptyStateView.setVisibility(View.VISIBLE);
                composeListView.setVisibility(View.GONE);
            } else {
                emptyStateView.setVisibility(View.GONE);
                composeListView.setVisibility(View.VISIBLE);
            }
            updateHeaderPanel(tags.size(), null);
        });"""
# Actually, wait, compose handles empty state perfectly! We don't need emptyStateView anymore.
new_obs2 = """viewModel.getMusicItems().observe(this, tags -> {
            apincer.android.mmate.ui.compose.ListInterop.updateTracks(tags);
            swipeRefreshLayout.setRefreshing(false);
            emptyStateView.setVisibility(View.GONE); // Let compose handle it
            updateHeaderPanel(tags.size(), null);
        });"""
content = content[:obs_start] + new_obs2 + content[obs_end:]

# Replace all adapter.getCriteria()
content = content.replace("adapter.getCriteria()", "currentCriteria")

# Replace search logic
content = content.replace("adapter.search(\"\");", "currentCriteria.resetSearch();")
content = content.replace("adapter.search(text);", "if(text == null || text.isEmpty()) currentCriteria.resetSearch(); else currentCriteria.searchFor(text);")
content = content.replace("adapter.setType(type);", "currentCriteria.setType(type);")
content = content.replace("adapter.setKeyword(keyword);", "currentCriteria.setKeyword(keyword);")
content = content.replace("adapter.resetFilter();", "currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);")
content = content.replace("adapter.hasFilter()", "(!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()))")
content = content.replace("adapter.isSearchMode()", "currentCriteria.isSearchMode()")

# Replace totalSize and totalDuration
content = content.replace("adapter.getTotalItems()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().size()")
content = content.replace("adapter.getTotalSize()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum()")
content = content.replace("adapter.getTotalDuration()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum()")

# Fix headers
content = content.replace("adapter.getHeaderLabel()", "\"Tracks\"") # simplify for now
content = content.replace("adapter.getHeaderTitle()", "currentCriteria.getType().name()") 

# Playback state
content = content.replace("adapter.setPlaybackService(playbackService);", "apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());")
content = content.replace("adapter.setPlaybackState(playbackState);", "apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
