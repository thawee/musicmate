import re
with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Delete fields
content = re.sub(r'^[ \t]*private RecyclerView mRecyclerView;\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*private MusicTagAdapter\.TrackItemDecoration itemDecoration;\n', '', content, flags=re.MULTILINE)

# Delete usages inside viewModel.musicItems.observe
content = re.sub(r'^[ \t]*mRecyclerView\.post\(\(\) -> \{\n', '            composeListView.post(() -> {\n', content, flags=re.MULTILINE)

# Delete save/restore layout manager state since Compose handles it naturally when items update
content = re.sub(r'^[ \t]*android\.os\.Parcelable state = null;\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*if \(mRecyclerView\.getLayoutManager\(\) != null\) \{\n[ \t]*state = mRecyclerView\.getLayoutManager\(\)\.onSaveInstanceState\(\);\n[ \t]*\}\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*if \(state != null && mRecyclerView\.getLayoutManager\(\) != null\) \{\n[ \t]*mRecyclerView\.getLayoutManager\(\)\.onRestoreInstanceState\(state\);\n[ \t]*\}\n', '', content, flags=re.MULTILINE)

# Replace simple usages
content = content.replace("mRecyclerView = findViewById(R.id.recycler_view);", "composeListView = findViewById(R.id.compose_list_view);\n        apincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);")
content = content.replace("mRecyclerView.stopScroll();", "")
content = content.replace("mRecyclerView.scrollToPosition(0);", "")
content = content.replace("mRecyclerView.postDelayed(() -> mRecyclerView.smoothScrollBy(0, 0), 10);", "")

# Remove setup block
setup_pattern = r'^[ \t]*mRecyclerView\.setLayoutManager\(new LinearLayoutManager\(this\)\);\n.*?mRecyclerView\.setPreserveFocusAfterLayout\(true\);\n'
content = re.sub(setup_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# Insets
content = content.replace("ViewCompat.setOnApplyWindowInsetsListener(mRecyclerView,", "ViewCompat.setOnApplyWindowInsetsListener(composeListView,")

# Touch listeners
touch_pattern = r'^[ \t]*mRecyclerView\.addOnItemTouchListener\(new RecyclerView\.SimpleOnItemTouchListener\(\) \{.*?\n[ \t]*\}\);\n'
content = re.sub(touch_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# Scroll listener
scroll_pattern = r'^[ \t]*mRecyclerView\.addOnScrollListener\(new RecyclerView\.OnScrollListener\(\) \{.*?\n[ \t]*\}\);\n'
content = re.sub(scroll_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# Fast scroller
fast_pattern = r'^[ \t]*new FastScrollerBuilder\(mRecyclerView\).*?\.build\(\);\n'
content = re.sub(fast_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# scrollToPosition inside checkNowPlaying
content = content.replace("mRecyclerView.post(() -> {", "composeListView.post(() -> {")
content = re.sub(r'^[ \t]*mRecyclerView\.scrollToPosition\(positionWithOffset\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*LinearLayoutManager layoutManager = \(LinearLayoutManager\) mRecyclerView\.getLayoutManager\(\);\n[ \t]*if \(layoutManager != null\) \{.*?\n[ \t]*\}\n', '', content, flags=re.MULTILINE | re.DOTALL)

# isScrolling
content = re.sub(r'^[ \t]*return mRecyclerView\.getScrollState\(\) != RecyclerView\.SCROLL_STATE_IDLE.*?\n', '        return false;\n', content, flags=re.MULTILINE)

# setPadding
content = re.sub(r'^[ \t]*mRecyclerView\.setPadding\(0, 0, 0, 0\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*mRecyclerView\.setPadding\(0, \(int\) dpToPx\(getApplicationContext\(\), 42\), 0, 0\);\n', '', content, flags=re.MULTILINE)

# musicItemsLoading
content = content.replace("mRecyclerView.post(() -> swipeRefreshLayout.setRefreshing(isLoading))", "composeListView.post(() -> swipeRefreshLayout.setRefreshing(isLoading))")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
