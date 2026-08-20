with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Replace playbackService.isPlaying() with a state check
# Wait, for the ServiceConnected block:
# It's called when onServiceConnected fires, we might not have playbackState yet.
# Actually, we can just default to false, or fetch it.
# Let's see what adapter.setPlaybackService did. It probably initialized it.
# Let's replace playbackService.isPlaying() with false for the first call.
content = content.replace("playbackService.isPlaying()", "false")

# Then in the playbackState observer:
# apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), false);
# We need to change that `false` to `playbackState.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING`
content = content.replace("ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), false);", "ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackState.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING);")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)

