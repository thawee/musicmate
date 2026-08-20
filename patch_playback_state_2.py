with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackState.currentState" in line and "onServiceConnected" in "".join(lines[max(0, i-10):i]):
        lines[i] = "                    apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), false);\n"

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.writelines(lines)
