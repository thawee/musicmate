import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Add the 3 methods
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
# insert before the last }
content = content[:content.rfind("}")] + methods + "\n}"

# fix playTrackList which was using adapter.getSongs() in showTrackPopupMenu
content = content.replace("adapter.getSongs()", "apincer.android.mmate.ui.compose.ListInterop.getTracks()")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
