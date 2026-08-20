with open("app/src/main/res/layout/fragment_about.xml", "r") as f:
    content = f.read()

content = content.replace(
    "<apincer.android.mmate.ui.widget.QualityPieChartView",
    "<androidx.compose.ui.platform.ComposeView"
)

with open("app/src/main/res/layout/fragment_about.xml", "w") as f:
    f.write(content)
