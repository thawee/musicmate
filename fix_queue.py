with open("app/src/main/java/apincer/android/mmate/ui/compose/DialogInterop.kt", "r") as f:
    content = f.read()

if "import apincer.music.core.model.Track" not in content:
    content = content.replace(
        "import java.util.function.BiConsumer",
        "import java.util.function.BiConsumer\nimport apincer.music.core.model.Track"
    )

with open("app/src/main/java/apincer/android/mmate/ui/compose/DialogInterop.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/apincer/android/mmate/ui/compose/QueuePage.kt", "r") as f:
    content = f.read()

content = content.replace(
    "painterResource(id = R.drawable.ic_drag_handle_black_24dp)",
    "painterResource(id = R.drawable.ic_menu_white_24dp)"
)

with open("app/src/main/java/apincer/android/mmate/ui/compose/QueuePage.kt", "w") as f:
    f.write(content)
