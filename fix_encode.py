with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

content = content.replace(
    "import android.widget.TextView;",
    "import android.widget.TextView;\nimport apincer.android.mmate.ui.compose.FormatFilesState;\nimport apincer.android.mmate.ui.compose.DialogInterop;"
)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
