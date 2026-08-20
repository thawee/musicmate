with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

obs_start = content.find("mTracker.getSelectionLiveData().observe(this, selection -> {")
if obs_start != -1:
    obs_end = content.find("});", obs_start)
    insert_str = "\n            apincer.android.mmate.ui.compose.ListInterop.updateSelectedTracks(mTracker.getSelectionAsTracks());"
    content = content[:obs_end] + insert_str + content[obs_end:]
    
with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
