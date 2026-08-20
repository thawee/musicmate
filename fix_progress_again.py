with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

content = content.replace("state.getPosition()", "state.currentPositionSecond * 1000L")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
