import re

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "r") as f:
    content = f.read()

# Fix imports
content = content.replace("import apincer.android.mmate.utils.MusicMateExecutors", "import apincer.music.core.utils.MusicMateExecutors")
content = content.replace("import apincer.android.mmate.utils.MusicPathTagParser", "import apincer.music.core.utils.MusicPathTagParser")
# TagUIUtils is correct in import, but wait, did I import it? Let's check:
if "import apincer.android.mmate.utils.TagUIUtils" not in content:
    content = content.replace("import apincer.music.core.utils.StringUtils", "import apincer.music.core.utils.StringUtils\nimport apincer.android.mmate.utils.TagUIUtils")

# Fix isDirty
content = content.replace("tagsActivity.isDirty = false", "tagsActivity.setDirty(false)")
content = content.replace("import apincer.music.core.utils.TagUIUtils", "") # Remove incorrect one if present

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "w") as f:
    f.write(content)
