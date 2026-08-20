import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

prog_start = content.find("private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {")
if prog_start != -1:
    brace_count = 0
    prog_end = -1
    for i in range(prog_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                prog_end = i + 1
                break
                
    new_prog = """private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {
        if (!isAdded() || state == null) return;
        nowPlayingState.getPlaybackState().setValue(state);
        nowPlayingState.getProgressMs().setValue(state.getPosition());
    }"""
    content = content[:prog_start] + new_prog + content[prog_end:]

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
