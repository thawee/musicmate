import sys

with open("app/src/main/res/layout/activity_main.xml", "r") as f:
    xml = f.read()
import re
xml = re.sub(r'<androidx.recyclerview.widget.RecyclerView.*?\/>', 
    '''<androidx.compose.ui.platform.ComposeView
                    android:id="@+id/compose_list_view"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    app:layout_behavior="@string/appbar_scrolling_view_behavior" />''', 
    xml, flags=re.DOTALL)
with open("app/src/main/res/layout/activity_main.xml", "w") as f:
    f.write(xml)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# 1. Imports
content = content.replace("import androidx.recyclerview.widget.RecyclerView;", "import androidx.compose.ui.platform.ComposeView;")
content = content.replace("import androidx.recyclerview.widget.LinearLayoutManager;", "")

# 2. Fields
content = content.replace("private RecyclerView mRecyclerView;", "private ComposeView composeListView;")
content = content.replace("private MusicTagAdapter adapter;", "private apincer.music.core.model.SearchCriteria currentCriteria = new apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY);")
content = content.replace("private MusicTagAdapter.TrackItemDecoration itemDecoration;", "")

# 3. FastScroller
content = re.sub(r'new FastScrollerBuilder\(mRecyclerView\).*?\.build\(\);', '', content, flags=re.DOTALL)

# 4. mRecyclerView to composeListView
content = content.replace("mRecyclerView = findViewById(R.id.recycler_view);", "composeListView = findViewById(R.id.compose_list_view);\napincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);")
content = re.sub(r'mRecyclerView\.[a-zA-Z0-9_]+\(.*?\);', '', content)
content = content.replace("ViewCompat.setOnApplyWindowInsetsListener(composeListView, (v, insets) -> {", "ViewCompat.setOnApplyWindowInsetsListener(composeListView, (v, insets) -> {")
content = content.replace("mRecyclerView", "composeListView")

# 5. Adapter usages
content = content.replace("adapter.setPlaybackService(playbackService);", "apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());")
content = content.replace("adapter.setPlaybackState(playbackState);", "apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());")
content = content.replace("adapter.getCriteria()", "currentCriteria")
content = content.replace("adapter.hasFilter()", "(!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()))")
content = content.replace("adapter.isSearchMode()", "currentCriteria.isSearchMode()")
content = content.replace("adapter.search(\"\");", "currentCriteria.resetSearch();")
content = content.replace("adapter.search(text);", "if(text == null || text.isEmpty()) currentCriteria.resetSearch(); else currentCriteria.searchFor(text);")
content = content.replace("adapter.setType(type);", "currentCriteria.setType(type);")
content = content.replace("adapter.setKeyword(keyword);", "currentCriteria.setKeyword(keyword);")
content = content.replace("adapter.resetFilter();", "currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);")
content = content.replace("adapter.getTotalItems()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().size()")
content = content.replace("adapter.getTotalSize()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum()")
content = content.replace("adapter.getTotalDuration()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum()")
content = content.replace("adapter.getHeaderLabel()", "\"Tracks\"")
content = content.replace("adapter.getHeaderTitle()", "currentCriteria.getType().name()") 

# 6. ViewModel observe
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
content = content[:obs_start] + new_obs + content[obs_end:]

# 7. SelectionTracker
sel_start = content.find("mTracker = new MySelectionTracker();")
sel_end = content.find("});", sel_start)
if sel_start != -1 and sel_end != -1:
    content = content[:sel_end] + "\n            apincer.android.mmate.ui.compose.ListInterop.updateSelectedTracks(mTracker.getSelectionAsTracks());" + content[sel_end:]

# 8. Clicks methods
methods = """
    public void onTrackClicked(Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null && mTracker.hasSelection()) {
            if (mTracker.isSelected((long) position)) {
                mTracker.deselect((long) position);
            } else {
                mTracker.select((long) position);
            }
            return;
        }

        if(tag != null && tag.isContainer()) {
            doStartRefresh(tag.getContainerType(), tag.getTitle());
        } else if (tag != null) {
            doShowEditActivity(java.util.Collections.singletonList(tag));
        }
    }

    public void onTrackLongClicked(Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null) {
            mTracker.select((long) position);
        }
    }

    public void onTrackMenuClicked(Track tag, int position) {
        if (tag != null) {
            showTrackPopupMenu(findViewById(R.id.compose_list_view), tag);
        }
    }
"""
content = content[:content.rfind("}")] + methods + "\n}"

# Fix playTrackList inside showTrackPopupMenu which used adapter.getSongs()
content = content.replace("adapter.getSongs()", "apincer.android.mmate.ui.compose.ListInterop.getTracks()")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)

