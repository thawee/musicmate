import re

with open("app/src/main/java/apincer/android/mmate/ui/compose/ChartInterop.kt", "r") as f:
    content = f.read()

new_method = """
    @JvmStatic
    fun setDynamicRangeMeterContent(view: ComposeView, track: apincer.music.core.model.Track?) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            DynamicRangeMeter(track = track)
        }
    }
}"""
content = content.replace("}\n", new_method, 1)

with open("app/src/main/java/apincer/android/mmate/ui/compose/ChartInterop.kt", "w") as f:
    f.write(content)
