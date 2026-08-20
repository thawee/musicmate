with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

content = content.replace("nowPlayingState.getIsShuffle()", "nowPlayingState.isShuffle()")
content = content.replace("setupNowPlayingTab(viewNowPlayingPage);", "setupNowPlayingTab();")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
