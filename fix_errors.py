import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Remove the predicate class
content = re.sub(r'private class MusicTrackSelectionPredicate.*?\}', '', content, flags=re.DOTALL)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)

with open("app/src/main/java/apincer/android/mmate/ui/TagsActivity.java", "r") as f:
    content2 = f.read()

content2 = content2.replace("((TagsEditorFragment) activeFragment).initEditorInputs(musicTag);", "((TagsEditorFragment) activeFragment).initEditorInputs();")
content2 = content2.replace("((TagsTechnicalFragment) activeFragment).displayTechnicalInfo(musicTag);", "// ((TagsTechnicalFragment) activeFragment).displayTechnicalInfo(musicTag); // Re-renders automatically in Compose")

with open("app/src/main/java/apincer/android/mmate/ui/TagsActivity.java", "w") as f:
    f.write(content2)
