with open("app/src/main/java/apincer/android/mmate/ui/MySelectionTracker.java", "r") as f:
    content = f.read()

if "getSelectionAsTracks()" not in content:
    method = """
    public java.util.Set<apincer.music.core.model.Track> getSelectionAsTracks() {
        java.util.Set<apincer.music.core.model.Track> tracks = new java.util.HashSet<>();
        java.util.List<apincer.music.core.model.Track> all = apincer.android.mmate.ui.compose.ListInterop.getTracks();
        for (Long pos : selection) {
            if (pos >= 0 && pos < all.size()) {
                tracks.add(all.get(pos.intValue()));
            }
        }
        return tracks;
    }
"""
    content = content[:content.rfind("}")] + method + "\n}"
    with open("app/src/main/java/apincer/android/mmate/ui/MySelectionTracker.java", "w") as f:
        f.write(content)
