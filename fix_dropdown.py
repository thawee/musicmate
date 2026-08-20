with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsEditorPage.kt", "r") as f:
    content = f.read()

content = content.replace("Modifier\n                .menuAnchor()\n                .fillMaxWidth()", "Modifier\n                .menuAnchor(MenuAnchorType.PrimaryEditable, true)\n                .fillMaxWidth()")

with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsEditorPage.kt", "w") as f:
    f.write(content)
