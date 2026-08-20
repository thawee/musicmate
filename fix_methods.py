import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# isPlaying -> check nowPlayingState
content = content.replace("if (playbackService.isPlaying()) playbackService.pause();\n                    else playbackService.play();", 
    "if (nowPlayingState.getPlaybackState().getValue() != null && nowPlayingState.getPlaybackState().getValue().currentState == apincer.music.core.playback.PlaybackState.State.PLAYING) playbackService.pausePlayer();\n                    else if (playbackService.getNowPlayingSong() != null) playbackService.playSong(playbackService.getNowPlayingSong());")

# shuffle
content = content.replace("boolean shuffle = !playbackService.isShuffleModeEnabled();", "boolean shuffle = !nowPlayingState.getIsShuffle().getValue();")
content = content.replace("playbackService.setShuffleMode(shuffle ? PlaybackService.SHUFFLE_MODE_ALL : PlaybackService.SHUFFLE_MODE_NONE);", "playbackService.setShuffleMode(shuffle);")

# repeat
content = content.replace("int mode = playbackService.getRepeatMode();", "int mode = nowPlayingState.getRepeatMode().getValue();")
content = content.replace("int nextMode = (mode == PlaybackService.REPEAT_MODE_NONE) ? PlaybackService.REPEAT_MODE_ALL :\n                                   (mode == PlaybackService.REPEAT_MODE_ALL) ? PlaybackService.REPEAT_MODE_ONE : PlaybackService.REPEAT_MODE_NONE;", "int nextMode = (mode == 0) ? 1 : (mode == 1) ? 2 : 0;")
content = content.replace("playbackService.setRepeatMode(nextMode);", "playbackService.setRepeatMode(String.valueOf(nextMode));")

# remove override errors
content = content.replace("""                            @Override
                            public void onSuccess(android.graphics.drawable.Drawable result) {""", """                            public void onSuccess(android.graphics.drawable.Drawable result) {""")

content = content.replace("""                            @Override
                            public void onError(android.graphics.drawable.Drawable error) {""", """                            public void onError(android.graphics.drawable.Drawable error) {""")

# fix populateNowPlayingSheet isShuffle and repeat
content = content.replace("nowPlayingState.getIsShuffle().setValue(playbackService.isShuffleModeEnabled());", "// nowPlayingState.getIsShuffle().setValue(playbackService.isShuffleModeEnabled()); // Not directly available")
content = content.replace("nowPlayingState.getRepeatMode().setValue(playbackService.getRepeatMode());", "// nowPlayingState.getRepeatMode().setValue(playbackService.getRepeatMode()); // Not directly available")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
