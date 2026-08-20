import re

with open("app/src/main/java/apincer/android/mmate/ui/compose/QualityPieChart.kt", "r") as f:
    content = f.read()

content = content.replace("val color: Color", "val color: Int")
content = content.replace("entry.color", "Color(entry.color)")

with open("app/src/main/java/apincer/android/mmate/ui/compose/QualityPieChart.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/apincer/android/mmate/ui/AboutActivity.java", "r") as f:
    content = f.read()

content = content.replace("new androidx.compose.ui.graphics.Color(color)", "color")

with open("app/src/main/java/apincer/android/mmate/ui/AboutActivity.java", "w") as f:
    f.write(content)
