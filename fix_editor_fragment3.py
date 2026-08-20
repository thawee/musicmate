with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsEditorPage.kt", "r") as f:
    content = f.read()

content = content.replace("import apincer.android.mmate.utils.CoverartFetcher", "import apincer.android.mmate.coil3.CoverartFetcher")

with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsEditorPage.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "r") as f:
    content2 = f.read()

content2 = content2.replace("TagUIUtils.isThaiEncodingProblem", "ThaiEncodingUtils.isGarbledThai")
content2 = content2.replace("TagUIUtils.fixThaiEncoding", "ThaiEncodingUtils.fixThaiEncoding")
if "import apincer.music.core.utils.ThaiEncodingUtils" not in content2:
    content2 = content2.replace("import apincer.music.core.utils.TagUIUtils", "import apincer.music.core.utils.TagUIUtils\nimport apincer.music.core.utils.ThaiEncodingUtils")

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "w") as f:
    f.write(content2)
