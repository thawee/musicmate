with open("app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingState.kt", "r") as f:
    content = f.read()

content = content.replace("PlaybackState.STOPPED", "PlaybackState().apply { currentState = PlaybackState.State.STOPPED }")

with open("app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingState.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingPage.kt", "r") as f:
    content = f.read()

content = content.replace("PlaybackState.PLAYING", "PlaybackState.State.PLAYING")
content = content.replace("state.playbackState.value ==", "state.playbackState.value?.currentState ==")
content = content.replace("ic_baseline_flip_24", "rounded_swap_horiz_24")

with open("app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingPage.kt", "w") as f:
    f.write(content)
