with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

import re

# Remove the field
content = content.replace("private MusicTagAdapter adapter;", "private apincer.music.core.model.SearchCriteria currentCriteria = new apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY);")
# The other adapter (NoFilterArrayAdapter) is local, so it's fine. We only want to touch `adapter.` or `adapter = ` or `(adapter)`

# Replace usages
content = content.replace("adapter.getCriteria()", "currentCriteria")
content = content.replace("adapter.hasFilter()", "(!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()))")
content = content.replace("adapter.isSearchMode()", "currentCriteria.isSearchMode()")

content = content.replace("adapter.resetFilter();", "currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);")
content = content.replace("adapter.search(\"\");", "currentCriteria.resetSearch();")
content = content.replace("adapter.search(text);", "if(text == null || text.isEmpty()) currentCriteria.resetSearch(); else currentCriteria.searchFor(text);")
content = content.replace("adapter.setType(type);", "currentCriteria.setType(type);")
content = content.replace("adapter.setKeyword(keyword);", "currentCriteria.setKeyword(keyword);")

content = content.replace("adapter.getTotalItems()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().size()")
content = content.replace("adapter.getItemCount()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().size()")
content = content.replace("adapter.getTotalSize()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum()")
content = content.replace("adapter.getTotalDuration()", "apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum()")
content = content.replace("adapter.getHeaderLabel()", "\"Tracks\"")
content = content.replace("adapter.getHeaderTitle()", "currentCriteria.getType().name()") 

content = content.replace("adapter.getSongs()", "apincer.android.mmate.ui.compose.ListInterop.getTracks()")

# For getMusicTag, we need to be careful with nulls
content = re.sub(r'adapter\.getMusicTag\((.*?)\)', r'(\1 >= 0 && \1 < apincer.android.mmate.ui.compose.ListInterop.getTracks().size() ? apincer.android.mmate.ui.compose.ListInterop.getTracks().get(\1) : null)', content)

content = content.replace("adapter.getMusicTagPosition(currentlyPlaying)", "apincer.android.mmate.ui.compose.ListInterop.getTracks().indexOf(currentlyPlaying)")

# Remove standalone statements
content = re.sub(r'^[ \t]*adapter\.notifyItemChanged\(.*?\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*adapter\.setMusicTags\(.*?\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*adapter\.setPlaybackService\(.*?\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*adapter\.setPlaybackState\(.*?\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*adapter\.setClickListener\(.*?\);\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^[ \t]*mRecyclerView\.setAdapter\(adapter\);\n', '', content, flags=re.MULTILINE)

# Remove the initialization
content = re.sub(r'^[ \t]*adapter = new MusicTagAdapter.*?;\n', '', content, flags=re.MULTILINE)

# Remove registerAdapterDataObserver block completely
obs_pattern = r'^[ \t]*adapter\.registerAdapterDataObserver\(new RecyclerView\.AdapterDataObserver\(\) \{.*?\n[ \t]*\}\);\n'
content = re.sub(obs_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# Remove setOnCoverArtClickListener completely
click_pattern = r'^[ \t]*adapter\.setOnCoverArtClickListener\(\(view, position\) -> \{.*?\n[ \t]*\}\);\n'
content = re.sub(click_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

# Replace viewModel.search(adapter, ...) with currentCriteria
content = content.replace("viewModel.search(adapter, query)", "viewModel.search(currentCriteria, query)")
content = content.replace("viewModel.search(adapter, newText)", "viewModel.search(currentCriteria, newText)")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
