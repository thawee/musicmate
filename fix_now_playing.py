with open("app/src/main/java/apincer/android/mmate/ui/compose/DialogInterop.kt", "r") as f:
    content = f.read()

content = content.replace("    }\n}\n\n    @JvmStatic", "    }\n\n    @JvmStatic")

with open("app/src/main/java/apincer/android/mmate/ui/compose/DialogInterop.kt", "w") as f:
    f.write(content)
