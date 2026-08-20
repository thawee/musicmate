import re

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "r") as f:
    content = f.read()

# Add doShowReadTagsPreview
content = content.replace("fun doAutoFormatTags() {", "fun doShowReadTagsPreview() {\n        Toast.makeText(context, \"Tags from Filename to be implemented in Compose\", Toast.LENGTH_SHORT).show()\n    }\n\n    fun doFormatTags() {")
content = content.replace("fun doAutoFormatTags()", "fun doFormatTags()") # in case my previous change wasn't complete

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "w") as f:
    f.write(content)
