with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] `QualityPieChartView.java` -> `QualityPieChart.kt`",
    "- [x] `QualityPieChartView.java` -> `QualityPieChart.kt`"
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
