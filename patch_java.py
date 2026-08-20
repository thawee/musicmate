with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

setup_start = content.find("mRecyclerView = findViewById(R.id.recycler_view);")
if setup_start != -1:
    setup_end = content.find("mRecyclerView.setLayoutManager", setup_start)
    insert_str = "mRecyclerView = findViewById(R.id.recycler_view);\n        androidx.compose.ui.platform.ComposeView composeListView = findViewById(R.id.compose_list_view);\n        apincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);\n        "
    content = content[:setup_start] + insert_str + content[setup_end:]

obs_start = content.find("adapter.setMusicTags(musicTags);")
if obs_start != -1:
    insert_str = "adapter.setMusicTags(musicTags);\n                apincer.android.mmate.ui.compose.ListInterop.updateTracks(musicTags);"
    content = content[:obs_start] + insert_str + content[obs_start + len("adapter.setMusicTags(musicTags);"):]

sel_start = content.find("mTracker.getSelectionLiveData().observe(this, selection -> {")
if sel_start != -1:
    sel_end = content.find("});", sel_start)
    insert_str = "\n            apincer.android.mmate.ui.compose.ListInterop.updateSelectedTracks(mTracker.getSelectionAsTracks());\n            "
    content = content[:sel_end] + insert_str + content[sel_end:]

play1 = content.find("adapter.setPlaybackService(playbackService);")
if play1 != -1:
    content = content[:play1] + "adapter.setPlaybackService(playbackService);\n                    apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());\n" + content[play1 + len("adapter.setPlaybackService(playbackService);"):]

play2 = content.find("adapter.setPlaybackState(playbackState);")
if play2 != -1:
    content = content[:play2] + "adapter.setPlaybackState(playbackState);\n                    apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackService.isPlaying());\n" + content[play2 + len("adapter.setPlaybackState(playbackState);"):]

methods = """
    public void onTrackClicked(apincer.music.core.model.Track tag, int position) {
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

    public void onTrackLongClicked(apincer.music.core.model.Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null) {
            mTracker.select((long) position);
        }
    }

    public void onTrackMenuClicked(apincer.music.core.model.Track tag, int position) {
        if (tag != null) {
            showTrackPopupMenu(findViewById(R.id.compose_list_view), tag);
        }
    }
"""
content = content[:content.rfind("}")] + methods + "\n}"

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
