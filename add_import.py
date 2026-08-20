with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "r") as f:
    content = f.read()

content = content.replace("import apincer.music.core.utils.StringUtils", "import apincer.music.core.utils.StringUtils\nimport apincer.music.core.utils.ThaiEncodingUtils")

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "w") as f:
    f.write(content)
