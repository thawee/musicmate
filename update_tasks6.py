with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] `QualityIndicatorView`, `NewIndicatorView`, `RatingIndicatorView` -> `AudioBadges.kt`",
    "- [x] `QualityIndicatorView`, `NewIndicatorView`, `RatingIndicatorView` -> `AudioBadges.kt`"
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
